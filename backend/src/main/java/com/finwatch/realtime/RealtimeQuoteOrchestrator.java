package com.finwatch.realtime;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.finwatch.stock.domain.Stock;
import com.finwatch.stock.repository.StockRepository;

import jakarta.annotation.PreDestroy;

@Component
public class RealtimeQuoteOrchestrator {

    private final boolean liveMode;
    private final boolean enabled;
    private final StockRepository stockRepository;
    private final KisRealtimeClient kisRealtimeClient;
    private final FinnhubRealtimeClient finnhubRealtimeClient;
    private final RealtimeQuoteHub hub;
    private final AtomicBoolean started = new AtomicBoolean();

    public RealtimeQuoteOrchestrator(
            @Value("${app.data.mode:DEMO}") String dataMode,
            @Value("${app.realtime.enabled:true}") boolean enabled,
            StockRepository stockRepository,
            KisRealtimeClient kisRealtimeClient,
            FinnhubRealtimeClient finnhubRealtimeClient,
            RealtimeQuoteHub hub) {
        this.liveMode = "LIVE".equalsIgnoreCase(dataMode);
        this.enabled = enabled;
        this.stockRepository = stockRepository;
        this.kisRealtimeClient = kisRealtimeClient;
        this.finnhubRealtimeClient = finnhubRealtimeClient;
        this.hub = hub;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!enabled || !liveMode) {
            hub.updateProvider("KIS", "DISABLED", "DATA_MODE=LIVE와 REALTIME_ENABLED=true일 때 연결합니다.");
            hub.updateProvider("FINNHUB", "DISABLED", "DATA_MODE=LIVE와 REALTIME_ENABLED=true일 때 연결합니다.");
            return;
        }
        if (!started.compareAndSet(false, true)) {
            return;
        }
        Thread.ofVirtual().name("realtime-orchestrator").start(() -> {
            List<Stock> stocks = stockRepository.findAllByActiveTrueOrderByMarketAscNameAsc();
            List<String> krxSymbols = stocks.stream()
                    .filter(stock -> "KRX".equalsIgnoreCase(stock.getMarket()))
                    .map(Stock::getSymbol)
                    .toList();
            List<String> usSymbols = stocks.stream()
                    .filter(stock -> "NASDAQ".equalsIgnoreCase(stock.getMarket())
                            || "NYSE".equalsIgnoreCase(stock.getMarket()))
                    .map(Stock::getSymbol)
                    .toList();
            kisRealtimeClient.start(krxSymbols);
            finnhubRealtimeClient.start(usSymbols);
        });
    }

    @PreDestroy
    public void stop() {
        if (!started.get()) {
            return;
        }
        kisRealtimeClient.stop();
        finnhubRealtimeClient.stop();
    }
}
