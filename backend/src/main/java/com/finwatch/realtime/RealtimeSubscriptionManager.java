package com.finwatch.realtime;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.finwatch.alert.domain.AlertStatus;
import com.finwatch.alert.repository.PriceAlertRepository;
import com.finwatch.portfolio.repository.PortfolioHoldingRepository;
import com.finwatch.stock.domain.Stock;
import com.finwatch.stock.repository.StockRepository;
import com.finwatch.watchlist.repository.WatchlistRepository;

import jakarta.annotation.PreDestroy;

@Component
public class RealtimeSubscriptionManager {

    private final boolean active;
    private final StockRepository stockRepository;
    private final WatchlistRepository watchlistRepository;
    private final PortfolioHoldingRepository holdingRepository;
    private final PriceAlertRepository alertRepository;
    private final KisRealtimeClient kisRealtimeClient;
    private final FinnhubRealtimeClient finnhubRealtimeClient;
    private final KisUsDaytimeSessionResolver kisUsDaytimeSessionResolver;
    private final RealtimeQuoteHub hub;
    private final int kisLimit;
    private final int kisOverseasLimit;
    private final int finnhubLimit;
    private final Duration selectionGrace;
    private final Duration refreshInterval;
    private final ScheduledExecutorService scheduler;
    private final ConcurrentHashMap<String, Selection> sessionSelections = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Selection> recentSelections = new ConcurrentHashMap<>();
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean providerClientsStarted = new AtomicBoolean();
    private volatile SubscriptionPlan currentPlan = new SubscriptionPlan(List.of(), List.of(), Instant.EPOCH);

    public RealtimeSubscriptionManager(
            @Value("${app.data.mode:DEMO}") String dataMode,
            @Value("${app.realtime.enabled:true}") boolean enabled,
            @Value("${app.realtime.kis-max-subscriptions:40}") int kisLimit,
            @Value("${app.realtime.kis-overseas-max-subscriptions:40}") int kisOverseasLimit,
            @Value("${app.realtime.finnhub-max-subscriptions:50}") int finnhubLimit,
            @Value("${app.realtime.selection-grace:30s}") Duration selectionGrace,
            @Value("${app.realtime.subscription-refresh:15s}") Duration refreshInterval,
            StockRepository stockRepository,
            WatchlistRepository watchlistRepository,
            PortfolioHoldingRepository holdingRepository,
            PriceAlertRepository alertRepository,
            KisRealtimeClient kisRealtimeClient,
            FinnhubRealtimeClient finnhubRealtimeClient,
            KisUsDaytimeSessionResolver kisUsDaytimeSessionResolver,
            RealtimeQuoteHub hub) {
        this.active = enabled && "LIVE".equalsIgnoreCase(dataMode);
        this.stockRepository = stockRepository;
        this.watchlistRepository = watchlistRepository;
        this.holdingRepository = holdingRepository;
        this.alertRepository = alertRepository;
        this.kisRealtimeClient = kisRealtimeClient;
        this.finnhubRealtimeClient = finnhubRealtimeClient;
        this.kisUsDaytimeSessionResolver = kisUsDaytimeSessionResolver;
        this.hub = hub;
        this.kisLimit = Math.max(1, Math.min(40, kisLimit));
        this.kisOverseasLimit = Math.max(1, Math.min(40, kisOverseasLimit));
        this.finnhubLimit = Math.max(1, finnhubLimit);
        this.selectionGrace = selectionGrace.isNegative() ? Duration.ZERO : selectionGrace;
        this.refreshInterval = refreshInterval.isNegative() || refreshInterval.isZero()
                ? Duration.ofSeconds(15) : refreshInterval;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "realtime-subscriptions");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void start() {
        if (!active) {
            hub.updateProvider("KIS_OVERSEAS", "DISABLED", "Overseas KIS real-time is disabled outside LIVE mode.");
            hub.updateProvider("KIS", "DISABLED", "DATA_MODE=LIVE와 REALTIME_ENABLED=true에서 연결됩니다.");
            hub.updateProvider("FINNHUB", "DISABLED", "DATA_MODE=LIVE와 REALTIME_ENABLED=true에서 연결됩니다.");
            return;
        }
        if (!started.compareAndSet(false, true)) {
            return;
        }
        scheduler.scheduleWithFixedDelay(
                this::safeReconcile,
                0,
                Math.max(1, refreshInterval.toSeconds()),
                TimeUnit.SECONDS);
    }

    public SelectionResult select(String sessionId, String market, String symbol) {
        String normalizedMarket = normalize(market);
        String normalizedSymbol = normalize(symbol);
        Stock stock = stockRepository.findByMarketAndSymbolAndActiveTrue(normalizedMarket, normalizedSymbol)
                .orElse(null);
        if (stock == null) {
            return new SelectionResult(false, normalizedMarket, normalizedSymbol, "활성 종목을 찾을 수 없습니다.");
        }
        Instant now = Instant.now();
        Selection next = new Selection(stock, now, Instant.MAX);
        Selection previous = sessionSelections.put(sessionId, next);
        if (previous != null && !canonical(previous.stock()).equals(canonical(stock))) {
            recentSelections.put(canonical(previous.stock()), previous.withExpiry(now.plus(selectionGrace)));
        }
        if (active && started.get()) {
            scheduler.execute(this::safeReconcile);
        }
        return new SelectionResult(true, stock.getMarket(), stock.getSymbol(), "실시간 선택 종목에 반영했습니다.");
    }

    public void releaseSession(String sessionId) {
        Selection removed = sessionSelections.remove(sessionId);
        if (removed != null) {
            recentSelections.put(
                    canonical(removed.stock()),
                    removed.withExpiry(Instant.now().plus(selectionGrace)));
            if (active && started.get()) {
                scheduler.execute(this::safeReconcile);
            }
        }
    }

    public SubscriptionPlan currentPlan() {
        return currentPlan;
    }

    public void requestRefresh() {
        if (active && started.get()) {
            scheduler.execute(this::safeReconcile);
        }
    }

    void reconcileNow() {
        if (!active) {
            return;
        }
        Instant now = Instant.now();
        recentSelections.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
        Map<String, Candidate> candidates = new HashMap<>();
        addAll(candidates, watchlistRepository.findDistinctActiveStocksForRealtime(), 1, Instant.EPOCH);
        addAll(candidates, holdingRepository.findDistinctActiveStocksForRealtime(), 1, Instant.EPOCH);
        addAll(candidates, alertRepository.findDistinctActiveStocksForRealtime(AlertStatus.ACTIVE), 1, Instant.EPOCH);
        sessionSelections.values().forEach(selection -> add(candidates, selection.stock(), 0, selection.selectedAt()));
        recentSelections.values().forEach(selection -> add(candidates, selection.stock(), 2, selection.selectedAt()));

        Comparator<Candidate> priority = Comparator.comparingInt(Candidate::priority)
                .thenComparing(Candidate::lastUsed, Comparator.reverseOrder())
                .thenComparing(candidate -> candidate.stock().getMarket())
                .thenComparing(candidate -> candidate.stock().getSymbol());
        List<Candidate> ordered = candidates.values().stream().sorted(priority).toList();
        List<String> krx = ordered.stream()
                .filter(candidate -> "KRX".equalsIgnoreCase(candidate.stock().getMarket()))
                .limit(kisLimit)
                .map(candidate -> candidate.stock().getSymbol())
                .toList();
        Map<String, String> usMarkets = usInstrumentMarkets(ordered);
        List<String> us = List.copyOf(usMarkets.keySet());
        int remainingKisCapacity = Math.max(0, 40 - krx.size());
        List<KisOverseasSubscription> kisOverseas = kisOverseasSubscriptions(
                usMarkets,
                now,
                Math.min(kisOverseasLimit, remainingKisCapacity));
        currentPlan = new SubscriptionPlan(krx, us, now);
        finnhubRealtimeClient.updateInstrumentMarkets(usMarkets);
        if (providerClientsStarted.compareAndSet(false, true)) {
            kisRealtimeClient.start(krx, kisOverseas);
            finnhubRealtimeClient.start(us);
        } else {
            kisRealtimeClient.updateSubscriptions(krx, kisOverseas);
            finnhubRealtimeClient.updateSubscriptions(us);
        }
    }

    private void safeReconcile() {
        try {
            reconcileNow();
        } catch (RuntimeException exception) {
            hub.updateProvider("SUBSCRIPTIONS", "DEGRADED", "구독 집합 갱신 실패: " + safeMessage(exception));
        }
    }

    private void addAll(
            Map<String, Candidate> candidates,
            List<Stock> stocks,
            int priority,
            Instant lastUsed) {
        if (stocks == null) {
            return;
        }
        stocks.forEach(stock -> add(candidates, stock, priority, lastUsed));
    }

    private void add(Map<String, Candidate> candidates, Stock stock, int priority, Instant lastUsed) {
        if (stock == null || !stock.isActive() || !stock.isTradable()) {
            return;
        }
        String key = canonical(stock);
        candidates.merge(key, new Candidate(stock, priority, lastUsed), (left, right) -> {
            if (right.priority() < left.priority()) {
                return right;
            }
            if (right.priority() == left.priority() && right.lastUsed().isAfter(left.lastUsed())) {
                return right;
            }
            return left;
        });
    }

    private Map<String, String> usInstrumentMarkets(List<Candidate> ordered) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Candidate candidate : ordered) {
            String market = normalize(candidate.stock().getMarket());
            if (!"NASDAQ".equals(market) && !"NYSE".equals(market) && !"AMEX".equals(market)) {
                continue;
            }
            String symbol = normalize(candidate.stock().getSymbol());
            if (!result.containsKey(symbol) && result.size() >= Math.max(kisOverseasLimit, finnhubLimit)) {
                continue;
            }
            result.merge(symbol, market, (current, incoming) -> current.equals(incoming) ? current : "UNKNOWN");
        }
        return java.util.Collections.unmodifiableMap(result);
    }

    private List<KisOverseasSubscription> kisOverseasSubscriptions(
            Map<String, String> markets,
            Instant now,
            int limit) {
        boolean daytime = kisUsDaytimeSessionResolver.isOpen(now);
        return markets.entrySet().stream()
                .filter(entry -> "NASDAQ".equals(entry.getValue())
                        || "NYSE".equals(entry.getValue())
                        || "AMEX".equals(entry.getValue()))
                .limit(limit)
                .map(entry -> daytime
                        ? KisOverseasSubscription.daytime(entry.getValue(), entry.getKey())
                        : KisOverseasSubscription.standard(entry.getValue(), entry.getKey()))
                .toList();
    }

    private String canonical(Stock stock) {
        return normalize(stock.getMarket()) + ":" + normalize(stock.getSymbol());
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        String sanitized = message.replaceAll("[\\r\\n\\t]+", " ").trim();
        return sanitized.length() <= 200 ? sanitized : sanitized.substring(0, 200);
    }

    @PreDestroy
    public void stop() {
        scheduler.shutdownNow();
        if (providerClientsStarted.get()) {
            kisRealtimeClient.stop();
            finnhubRealtimeClient.stop();
        }
    }

    public record SelectionResult(boolean accepted, String market, String symbol, String message) {
    }

    public record SubscriptionPlan(List<String> krxSymbols, List<String> usSymbols, Instant updatedAt) {
        public SubscriptionPlan {
            krxSymbols = List.copyOf(krxSymbols);
            usSymbols = List.copyOf(usSymbols);
        }
    }

    private record Candidate(Stock stock, int priority, Instant lastUsed) {
    }

    private record Selection(Stock stock, Instant selectedAt, Instant expiresAt) {
        private Selection withExpiry(Instant expiresAt) {
            return new Selection(stock, selectedAt, expiresAt);
        }
    }
}
