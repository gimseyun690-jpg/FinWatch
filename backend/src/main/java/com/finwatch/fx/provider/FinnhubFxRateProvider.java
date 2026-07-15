package com.finwatch.fx.provider;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.finwatch.data.provider.ProviderException;
import com.finwatch.data.provider.ProviderRestClientFactory;

@Component
public class FinnhubFxRateProvider implements FxRateProvider {

    private final RestClient restClient;
    private final String apiKey;
    private final String exchange;
    private final Object symbolLock = new Object();
    private volatile SymbolCache symbolCache = new SymbolCache(null, Instant.EPOCH);

    @Autowired
    public FinnhubFxRateProvider(
            @Value("${app.data.finnhub.api-key:}") String apiKey,
            @Value("${app.data.finnhub.base-url}") String baseUrl,
            @Value("${app.data.fx.finnhub-exchange:OANDA}") String exchange,
            @Value("${app.data.connect-timeout:3s}") Duration connectTimeout,
            @Value("${app.data.read-timeout:10s}") Duration readTimeout) {
        this(apiKey, exchange, ProviderRestClientFactory.create(baseUrl, connectTimeout, readTimeout));
    }

    FinnhubFxRateProvider(String apiKey, String exchange, RestClient restClient) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.exchange = exchange == null ? "OANDA" : exchange.trim().toUpperCase(Locale.ROOT);
        this.restClient = restClient;
    }

    @Override
    public String providerId() {
        return "FINNHUB";
    }

    @Override
    public boolean supports(String baseCurrency, String quoteCurrency) {
        return "USD".equals(baseCurrency) && "KRW".equals(quoteCurrency);
    }

    @Override
    public FxQuote latest(String baseCurrency, String quoteCurrency) {
        requireConfigured();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.get()
                    .uri(uri -> uri.path("/forex/rates").queryParam("base", baseCurrency).build())
                    .header("X-Finnhub-Token", apiKey)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(Map.class);
            BigDecimal rate = extractRate(response, baseCurrency, quoteCurrency);
            Instant fetchedAt = Instant.now();
            return new FxQuote(baseCurrency, quoteCurrency, rate, "REFERENCE", discoverSymbol(baseCurrency, quoteCurrency), fetchedAt, fetchedAt);
        } catch (RestClientResponseException exception) {
            throw mapHttp(exception);
        } catch (ResourceAccessException exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public List<FxBar> history(String baseCurrency, String quoteCurrency, Instant from, Instant to) {
        requireConfigured();
        String symbol = discoverSymbol(baseCurrency, quoteCurrency);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.get()
                    .uri(uri -> uri.path("/forex/candle")
                            .queryParam("symbol", symbol)
                            .queryParam("resolution", "D")
                            .queryParam("from", from.getEpochSecond())
                            .queryParam("to", to.getEpochSecond())
                            .build())
                    .header("X-Finnhub-Token", apiKey)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(Map.class);
            return normalizeBars(response, symbol);
        } catch (RestClientResponseException exception) {
            throw mapHttp(exception);
        } catch (ResourceAccessException exception) {
            throw unavailable(exception);
        }
    }

    String discoverSymbol(String baseCurrency, String quoteCurrency) {
        SymbolCache current = symbolCache;
        if (current.expiresAt().isAfter(Instant.now()) && current.symbol() != null) return current.symbol();
        synchronized (symbolLock) {
            current = symbolCache;
            if (current.expiresAt().isAfter(Instant.now()) && current.symbol() != null) return current.symbol();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> response = restClient.get()
                    .uri(uri -> uri.path("/forex/symbol").queryParam("exchange", exchange).build())
                    .header("X-Finnhub-Token", apiKey)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(List.class);
            String display = baseCurrency + "/" + quoteCurrency;
            String symbol = response == null ? null : response.stream()
                    .filter(item -> display.equalsIgnoreCase(string(item.get("displaySymbol"))))
                    .map(item -> string(item.get("symbol")))
                    .filter(value -> !value.isBlank())
                    .findFirst()
                    .orElse(null);
            if (symbol == null) {
                throw new ProviderException(HttpStatus.UNPROCESSABLE_ENTITY, "FX_PAIR_NOT_AVAILABLE", "Finnhub에서 USD/KRW 통화쌍을 찾을 수 없습니다.");
            }
            symbolCache = new SymbolCache(symbol, Instant.now().plus(Duration.ofHours(24)));
            return symbol;
        }
    }

    static BigDecimal extractRate(Map<String, Object> response, String baseCurrency, String quoteCurrency) {
        if (response == null || !baseCurrency.equalsIgnoreCase(string(response.get("base")))) {
            throw new ProviderException(HttpStatus.BAD_GATEWAY, "FX_PROVIDER_INVALID_RESPONSE", "Finnhub 환율 기준통화 응답이 올바르지 않습니다.");
        }
        Object quotes = response.get("quote");
        if (!(quotes instanceof Map<?, ?> values)) {
            throw new ProviderException(HttpStatus.BAD_GATEWAY, "FX_PROVIDER_INVALID_RESPONSE", "Finnhub 환율 응답에 quote가 없습니다.");
        }
        BigDecimal value = decimal(values.get(quoteCurrency));
        if (value.signum() <= 0) {
            throw new ProviderException(HttpStatus.BAD_GATEWAY, "FX_PROVIDER_INVALID_RESPONSE", "Finnhub USD/KRW 환율이 유효하지 않습니다.");
        }
        return value;
    }

    static List<FxBar> normalizeBars(Map<String, Object> response, String symbol) {
        if (response == null || !"ok".equalsIgnoreCase(string(response.get("s")))) return List.of();
        List<?> opens = list(response.get("o"));
        List<?> highs = list(response.get("h"));
        List<?> lows = list(response.get("l"));
        List<?> closes = list(response.get("c"));
        List<?> times = list(response.get("t"));
        int count = Math.min(times.size(), Math.min(closes.size(), Math.min(opens.size(), Math.min(highs.size(), lows.size()))));
        List<FxBar> bars = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            BigDecimal open = decimal(opens.get(index));
            BigDecimal high = decimal(highs.get(index));
            BigDecimal low = decimal(lows.get(index));
            BigDecimal close = decimal(closes.get(index));
            long epoch = longValue(times.get(index));
            if (open.signum() > 0 && high.compareTo(open.max(close)) >= 0 && low.compareTo(open.min(close)) <= 0 && epoch > 0) {
                bars.add(new FxBar(open, high, low, close, Instant.ofEpochSecond(epoch), symbol));
            }
        }
        return List.copyOf(bars);
    }

    private void requireConfigured() {
        if (apiKey.isBlank()) throw new ProviderException(HttpStatus.SERVICE_UNAVAILABLE, "FINNHUB_NOT_CONFIGURED", "FINNHUB_API_KEY가 필요합니다.");
    }

    private ProviderException mapHttp(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        if (status == 429) return new ProviderException(HttpStatus.TOO_MANY_REQUESTS, "FX_PROVIDER_QUOTA_EXCEEDED", "Finnhub 환율 호출 한도를 초과했습니다.", exception);
        return new ProviderException(HttpStatus.BAD_GATEWAY, "FX_PROVIDER_HTTP_" + status, "Finnhub 환율 HTTP 오류: " + status, exception);
    }

    private ProviderException unavailable(Exception exception) {
        return new ProviderException(HttpStatus.SERVICE_UNAVAILABLE, "FX_PROVIDER_UNAVAILABLE", "Finnhub 환율 API에 연결할 수 없습니다.", exception);
    }

    private static List<?> list(Object value) { return value instanceof List<?> list ? list : List.of(); }
    private static String string(Object value) { return value == null ? "" : value.toString().trim(); }
    private static BigDecimal decimal(Object value) { try { return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString()); } catch (NumberFormatException ignored) { return BigDecimal.ZERO; } }
    private static long longValue(Object value) { try { return value == null ? 0 : Long.parseLong(value.toString()); } catch (NumberFormatException ignored) { return 0; } }
    private record SymbolCache(String symbol, Instant expiresAt) { }
}
