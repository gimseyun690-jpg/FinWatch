package com.finwatch.fx.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.finwatch.data.provider.ProviderException;
import com.finwatch.data.sync.DataMode;
import com.finwatch.fx.cache.FxRateCacheStore;
import com.finwatch.fx.cache.FxRateCacheValue;
import com.finwatch.fx.domain.ExchangeRate;
import com.finwatch.fx.dto.FxRateResponses.FxHistory;
import com.finwatch.fx.dto.FxRateResponses.FxHistoryItem;
import com.finwatch.fx.dto.FxRateResponses.FxPair;
import com.finwatch.fx.dto.FxRateResponses.LatestFxRate;
import com.finwatch.fx.provider.FxRateProvider;
import com.finwatch.fx.repository.ExchangeRateRepository;

@Service
public class FxRateService {

    private static final String USD = "USD";
    private static final String KRW = "KRW";

    private final ExchangeRateRepository repository;
    private final List<FxRateProvider> providers;
    private final FxRateCacheStore cache;
    private final DataMode dataMode;
    private final Duration cacheTtl;
    private final Duration freshWithin;
    private final Duration delayedWithin;
    private final ConcurrentHashMap<String, CompletableFuture<ExchangeRate>> inFlight = new ConcurrentHashMap<>();

    public FxRateService(
            ExchangeRateRepository repository,
            List<FxRateProvider> providers,
            FxRateCacheStore cache,
            @Value("${app.data.mode:DEMO}") String dataMode,
            @Value("${app.data.fx.cache-ttl:60s}") Duration cacheTtl,
            @Value("${app.data.fx.fresh-within:15m}") Duration freshWithin,
            @Value("${app.data.fx.delayed-within:36h}") Duration delayedWithin) {
        this.repository = repository;
        this.providers = List.copyOf(providers);
        this.cache = cache;
        this.dataMode = DataMode.from(dataMode);
        this.cacheTtl = cacheTtl;
        this.freshWithin = freshWithin;
        this.delayedWithin = delayedWithin;
    }

    @Transactional
    public LatestFxRate latest(String base, String quote) {
        Pair pair = pair(base, quote);
        String key = cacheKey(pair);
        Optional<FxRateCacheValue> cached = cache.get(key);
        if (cached.isPresent()) return response(cached.get(), previousClose(pair, cached.get().asOf()));

        ExchangeRate stored = latestStored(pair).orElse(null);
        if (dataMode == DataMode.DEMO) {
            if (stored == null) throw unavailable();
            cache(stored, key);
            return response(stored, previousClose(pair, stored.getAsOf()));
        }
        if (stored != null && stored.getFetchedAt().isAfter(Instant.now().minus(freshWithin))) {
            cache(stored, key);
            return response(stored, previousClose(pair, stored.getAsOf()));
        }

        try {
            ExchangeRate current = fetchSingleFlight(pair);
            return response(current, previousClose(pair, current.getAsOf()));
        } catch (RuntimeException exception) {
            if (stored != null) return response(stored, previousClose(pair, stored.getAsOf()));
            if (exception instanceof FxRateException fx) throw fx;
            if (exception.getCause() instanceof RuntimeException cause) throw cause;
            throw unavailable();
        }
    }

    @Transactional
    public Optional<LatestFxRate> latestForPortfolio() {
        try {
            LatestFxRate value = latest(USD, KRW);
            return "STALE".equals(value.freshness()) ? Optional.empty() : Optional.of(value);
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    @Transactional
    public FxHistory history(String base, String quote, String period, String interval) {
        Pair pair = pair(base, quote);
        if (!"1D".equalsIgnoreCase(interval)) throw new FxRateException(HttpStatus.BAD_REQUEST, "FX_INTERVAL_INVALID", "환율 MVP interval은 1D만 지원합니다.");
        int days = periodDays(period);
        Instant to = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant from = Instant.now().minus(days, ChronoUnit.DAYS);
        List<ExchangeRate> values = historyStored(pair, from, to);

        if (dataMode == DataMode.LIVE && values.size() < 2) {
            for (FxRateProvider provider : providers(pair)) {
                try {
                    for (var bar : provider.history(pair.base(), pair.quote(), from, to)) {
                        if (!validBar(bar) || repository.existsByBaseCurrencyAndQuoteCurrencyAndSourceAndAsOf(pair.base(), pair.quote(), provider.providerId(), bar.asOf())) continue;
                        repository.save(ExchangeRate.create(pair.base(), pair.quote(), bar.close(), bar.open(), bar.high(), bar.low(), bar.close(), "DELAYED", provider.providerId(), bar.providerSymbol(), bar.asOf(), Instant.now()));
                    }
                    values = historyStored(pair, from, to);
                    if (!values.isEmpty()) {
                        break;
                    }
                } catch (ProviderException ignored) {
                    // Try the next provider, then keep the last verified DB values as fallback.
                }
            }
        }
        String rateType = values.isEmpty() ? "UNAVAILABLE" : values.get(values.size() - 1).getRateType();
        List<FxHistoryItem> items = values.stream().map(value -> new FxHistoryItem(
                value.getAsOf(), nonNull(value.getOpenRate(), value.getRate()),
                nonNull(value.getHighRate(), value.getRate()), nonNull(value.getLowRate(), value.getRate()),
                nonNull(value.getCloseRate(), value.getRate()), value.getSource())).toList();
        return new FxHistory(pair.base(), pair.quote(), period.toUpperCase(Locale.ROOT), "1D", rateType, items);
    }

    public List<FxPair> pairs() {
        return List.of(new FxPair(USD, KRW, "미국 달러/대한민국 원", true));
    }

    private ExchangeRate fetchSingleFlight(Pair pair) {
        String key = pair.base() + ":" + pair.quote();
        CompletableFuture<ExchangeRate> created = new CompletableFuture<>();
        CompletableFuture<ExchangeRate> existing = inFlight.putIfAbsent(key, created);
        if (existing != null) return existing.join();
        try {
            RuntimeException lastFailure = null;
            for (FxRateProvider provider : providers(pair)) {
                try {
                    var quote = provider.latest(pair.base(), pair.quote());
                    validate(quote.rate(), quote.asOf());
                    ExchangeRate saved = repository.existsByBaseCurrencyAndQuoteCurrencyAndSourceAndAsOf(pair.base(), pair.quote(), provider.providerId(), quote.asOf())
                            ? latestStored(pair).orElseThrow()
                            : repository.save(ExchangeRate.create(pair.base(), pair.quote(), quote.rate(), null, null, null, quote.rate(), quote.rateType(), provider.providerId(), quote.providerSymbol(), quote.asOf(), quote.fetchedAt()));
                    cache(saved, cacheKey(pair));
                    created.complete(saved);
                    return saved;
                } catch (ProviderException | FxRateException exception) {
                    lastFailure = exception;
                }
            }
            throw lastFailure == null ? unavailable() : lastFailure;
        } catch (RuntimeException exception) {
            created.completeExceptionally(exception);
            throw exception;
        } finally {
            inFlight.remove(key, created);
        }
    }

    private List<FxRateProvider> providers(Pair pair) {
        List<FxRateProvider> supported = providers.stream()
                .filter(candidate -> candidate.supports(pair.base(), pair.quote()))
                .toList();
        if (supported.isEmpty()) {
            throw new FxRateException(HttpStatus.NOT_FOUND, "FX_PAIR_NOT_SUPPORTED", "지원하지 않는 환율 통화쌍입니다.");
        }
        return supported;
    }

    private Pair pair(String base, String quote) {
        String normalizedBase = normalizeCurrency(base);
        String normalizedQuote = normalizeCurrency(quote);
        if (normalizedBase.equals(normalizedQuote)) throw new FxRateException(HttpStatus.BAD_REQUEST, "FX_PAIR_INVALID", "기준통화와 상대통화는 달라야 합니다.");
        if (!USD.equals(normalizedBase) || !KRW.equals(normalizedQuote)) throw new FxRateException(HttpStatus.NOT_FOUND, "FX_PAIR_NOT_SUPPORTED", "MVP는 USD/KRW만 지원합니다.");
        return new Pair(normalizedBase, normalizedQuote);
    }

    private String normalizeCurrency(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z]{3}")) throw new FxRateException(HttpStatus.BAD_REQUEST, "FX_PAIR_INVALID", "통화 코드는 ISO 4217 대문자 3자리여야 합니다.");
        return normalized;
    }

    private void validate(BigDecimal rate, Instant asOf) {
        if (rate == null || rate.signum() <= 0 || asOf == null || asOf.isAfter(Instant.now().plus(Duration.ofMinutes(5)))) {
            throw new FxRateException(HttpStatus.BAD_GATEWAY, "FX_PROVIDER_INVALID_RESPONSE", "검증할 수 없는 환율 응답입니다.");
        }
    }

    private boolean validBar(FxRateProvider.FxBar bar) {
        return bar != null && bar.close() != null && bar.close().signum() > 0 && bar.asOf() != null && !bar.asOf().isAfter(Instant.now().plus(Duration.ofMinutes(5)));
    }

    private BigDecimal previousClose(Pair pair, Instant currentAsOf) {
        Instant from = currentAsOf.minus(14, ChronoUnit.DAYS);
        return historyStored(pair, from, currentAsOf.minusNanos(1)).stream()
                .reduce((first, second) -> second).map(value -> nonNull(value.getCloseRate(), value.getRate())).orElse(null);
    }

    private Optional<ExchangeRate> latestStored(Pair pair) {
        if (dataMode == DataMode.DEMO) {
            return repository.findFirstByBaseCurrencyAndQuoteCurrencyOrderByAsOfDesc(pair.base(), pair.quote());
        }
        return repository.findFirstByBaseCurrencyAndQuoteCurrencyAndSourceNotOrderByAsOfDesc(
                pair.base(), pair.quote(), "DEMO");
    }

    private List<ExchangeRate> historyStored(Pair pair, Instant from, Instant to) {
        if (dataMode == DataMode.DEMO) {
            return repository.findByBaseCurrencyAndQuoteCurrencyAndAsOfBetweenOrderByAsOfAsc(
                    pair.base(), pair.quote(), from, to);
        }
        return repository.findByBaseCurrencyAndQuoteCurrencyAndSourceNotAndAsOfBetweenOrderByAsOfAsc(
                pair.base(), pair.quote(), "DEMO", from, to);
    }

    private LatestFxRate response(ExchangeRate value, BigDecimal previous) {
        return response(new FxRateCacheValue(value.getBaseCurrency(), value.getQuoteCurrency(), value.getRate(), value.getRateType(), value.getSource(), value.getProviderSymbol(), value.getAsOf(), value.getFetchedAt()), previous);
    }

    private LatestFxRate response(FxRateCacheValue value, BigDecimal previous) {
        BigDecimal change = previous == null ? null : value.rate().subtract(previous).setScale(4, RoundingMode.HALF_UP);
        BigDecimal changeRate = previous == null || previous.signum() == 0 ? null : change.divide(previous, 8, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).setScale(4, RoundingMode.HALF_UP);
        return new LatestFxRate(value.baseCurrency(), value.quoteCurrency(), value.rate(), previous, change, changeRate, value.rateType(), value.source(), value.providerSymbol(), value.asOf(), value.fetchedAt(), freshness(value));
    }

    private String freshness(FxRateCacheValue value) {
        if ("DEMO".equals(value.rateType())) return "FRESH";
        Duration age = Duration.between(value.asOf(), Instant.now());
        if (age.compareTo(freshWithin) <= 0) return "FRESH";
        if (age.compareTo(delayedWithin) <= 0) return "DELAYED";
        return "STALE";
    }

    private void cache(ExchangeRate value, String key) {
        cache.put(key, new FxRateCacheValue(value.getBaseCurrency(), value.getQuoteCurrency(), value.getRate(), value.getRateType(), value.getSource(), value.getProviderSymbol(), value.getAsOf(), value.getFetchedAt()), cacheTtl);
    }

    private String cacheKey(Pair pair) { return "fx:latest:" + pair.base() + ":" + pair.quote(); }
    private int periodDays(String period) { return switch (period == null ? "" : period.toUpperCase(Locale.ROOT)) { case "1W" -> 7; case "1M" -> 31; case "3M" -> 93; case "1Y" -> 366; default -> throw new FxRateException(HttpStatus.BAD_REQUEST, "FX_PERIOD_INVALID", "period는 1W, 1M, 3M, 1Y 중 하나여야 합니다."); }; }
    private BigDecimal nonNull(BigDecimal preferred, BigDecimal fallback) { return preferred == null ? fallback : preferred; }
    private FxRateException unavailable() { return new FxRateException(HttpStatus.UNPROCESSABLE_ENTITY, "FX_RATE_UNAVAILABLE", "사용 가능한 USD/KRW 환율이 없습니다."); }
    private record Pair(String base, String quote) { }
}
