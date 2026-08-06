package com.finwatch.realtime;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.finwatch.data.provider.KisMarketDataClient;
import com.finwatch.data.provider.ProviderException;

import jakarta.annotation.PreDestroy;

/**
 * Fallback poller for active subscription symbols when real-time WebSocket ticks
 * are not available (e.g. during US Day Market hours or quiet sessions).
 * <p>
 * Periodically polls KIS REST API for currently selected active symbols and pushes updates
 * into {@link RealtimeQuoteHub} so front-end users get live price updates every 3~5 seconds.
 */
@Component
public class RealtimeQuoteFallbackPoller {

    private final boolean enabled;
    private final Duration pollInterval;
    private final KisMarketDataClient kisMarketDataClient;
    private final UsMarketSessionResolver usSessionResolver;
    private final RealtimeSubscriptionManager subscriptionManager;
    private final RealtimeQuoteHub hub;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean running = new AtomicBoolean();

    public RealtimeQuoteFallbackPoller(
            @Value("${app.data.mode:DEMO}") String dataMode,
            @Value("${app.realtime.enabled:true}") boolean realtimeEnabled,
            @Value("${app.realtime.fallback-polling-enabled:true}") boolean fallbackPollingEnabled,
            @Value("${app.realtime.fallback-poll-interval:4s}") Duration pollInterval,
            KisMarketDataClient kisMarketDataClient,
            UsMarketSessionResolver usSessionResolver,
            RealtimeSubscriptionManager subscriptionManager,
            RealtimeQuoteHub hub) {
        this.enabled = realtimeEnabled && fallbackPollingEnabled && "LIVE".equalsIgnoreCase(dataMode);
        this.pollInterval = pollInterval.isNegative() || pollInterval.isZero() ? Duration.ofSeconds(4) : pollInterval;
        this.kisMarketDataClient = kisMarketDataClient;
        this.usSessionResolver = usSessionResolver;
        this.subscriptionManager = subscriptionManager;
        this.hub = hub;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "realtime-quote-poller");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void start() {
        if (!enabled || !running.compareAndSet(false, true)) {
            return;
        }
        long intervalSec = Math.max(2, pollInterval.toSeconds());
        scheduler.scheduleWithFixedDelay(
                this::safePollActiveSymbols,
                intervalSec,
                intervalSec,
                TimeUnit.SECONDS);
    }

    private void safePollActiveSymbols() {
        try {
            pollActiveSymbols();
        } catch (RuntimeException ignored) {
            // Polling failures do not interrupt scheduler loops
        }
    }

    private void pollActiveSymbols() {
        RealtimeSubscriptionManager.SubscriptionPlan plan = subscriptionManager.currentPlan();
        if (plan == null) {
            return;
        }

        Instant now = Instant.now();

        // 1. Poll US symbols if in DAY_MARKET or if quote is stale (> 4s)
        List<String> usSymbols = plan.usSymbols();
        for (String symbol : usSymbols) {
            MarketSessionStatus session = usSessionResolver.resolve(now);
            var currentQuoteOpt = hub.find("NASDAQ", symbol)
                    .or(() -> hub.find("NYSE", symbol))
                    .or(() -> hub.find(symbol));

            boolean isDayMarket = session == MarketSessionStatus.DAY_MARKET;
            boolean isStale = currentQuoteOpt.map(q -> Duration.between(q.asOf(), now).toSeconds() >= 4).orElse(true);

            if (isDayMarket || isStale) {
                pollOverseasStock(symbol, now, session);
            }
        }

        // 2. Poll KRX symbols if quote is stale (> 5s)
        List<String> krxSymbols = plan.krxSymbols();
        for (String symbol : krxSymbols) {
            var currentQuoteOpt = hub.find("KRX", symbol).or(() -> hub.find(symbol));
            boolean isStale = currentQuoteOpt.map(q -> Duration.between(q.asOf(), now).toSeconds() >= 5).orElse(true);

            if (isStale) {
                pollDomesticStock(symbol, now);
            }
        }
    }

    private void pollOverseasStock(String symbol, Instant asOf, MarketSessionStatus session) {
        try {
            // Default to NASDAQ if market not specified
            String market = "NASDAQ";
            var existing = hub.find(symbol);
            if (existing.isPresent()) {
                market = existing.get().market();
            }
            var quote = kisMarketDataClient.getOverseasQuote(market, symbol);
            if (quote != null && quote.price() != null && quote.price().signum() > 0) {
                hub.publish(new LiveQuote(
                        market,
                        symbol,
                        quote.price(),
                        quote.change(),
                        quote.changeRate(),
                        quote.volume(),
                        quote.currency(),
                        asOf,
                        "KIS_OVERSEAS_POLL",
                        session.name()));
            }
        } catch (ProviderException ignored) {
            // Silently ignore single symbol REST errors
        }
    }

    private void pollDomesticStock(String symbol, Instant asOf) {
        try {
            var quote = kisMarketDataClient.getDomesticQuote(symbol);
            if (quote != null && quote.price() != null && quote.price().signum() > 0) {
                hub.publish(new LiveQuote(
                        "KRX",
                        symbol,
                        quote.price(),
                        quote.change(),
                        quote.changeRate(),
                        quote.volume(),
                        quote.currency(),
                        asOf,
                        "KIS_KRX_POLL",
                        "REGULAR"));
            }
        } catch (ProviderException ignored) {
            // Silently ignore single symbol REST errors
        }
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        scheduler.shutdownNow();
    }
}
