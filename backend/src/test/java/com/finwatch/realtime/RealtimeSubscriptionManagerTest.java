package com.finwatch.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.finwatch.alert.domain.AlertStatus;
import com.finwatch.alert.repository.PriceAlertRepository;
import com.finwatch.portfolio.repository.PortfolioHoldingRepository;
import com.finwatch.stock.domain.Stock;
import com.finwatch.stock.repository.StockRepository;
import com.finwatch.watchlist.repository.WatchlistRepository;

class RealtimeSubscriptionManagerTest {

    private RealtimeSubscriptionManager manager;

    @AfterEach
    void tearDown() {
        if (manager != null) {
            manager.stop();
        }
    }

    @Test
    void currentSelectionOutranksP1StocksAndProviderLimitIsApplied() {
        StockRepository stockRepository = mock(StockRepository.class);
        WatchlistRepository watchlistRepository = mock(WatchlistRepository.class);
        PortfolioHoldingRepository holdingRepository = mock(PortfolioHoldingRepository.class);
        PriceAlertRepository alertRepository = mock(PriceAlertRepository.class);
        KisRealtimeClient kisClient = mock(KisRealtimeClient.class);
        FinnhubRealtimeClient finnhubClient = mock(FinnhubRealtimeClient.class);
        Stock watchlistStock = stock("KRX", "005930");
        Stock selectedStock = stock("KRX", "000660");
        Stock holdingStock = stock("NASDAQ", "AAPL");
        when(watchlistRepository.findDistinctActiveStocksForRealtime()).thenReturn(List.of(watchlistStock));
        when(holdingRepository.findDistinctActiveStocksForRealtime()).thenReturn(List.of(holdingStock));
        when(alertRepository.findDistinctActiveStocksForRealtime(AlertStatus.ACTIVE)).thenReturn(List.of());
        when(stockRepository.findByMarketAndSymbolAndActiveTrue("KRX", "000660"))
                .thenReturn(Optional.of(selectedStock));
        manager = manager(
                stockRepository, watchlistRepository, holdingRepository, alertRepository, kisClient, finnhubClient,
                1, Duration.ofSeconds(30));

        assertThat(manager.select("session-1", "krx", "000660").accepted()).isTrue();
        manager.reconcileNow();

        assertThat(manager.currentPlan().krxSymbols()).containsExactly("000660");
        assertThat(manager.currentPlan().usSymbols()).containsExactly("AAPL");
        verify(kisClient).start(eq(List.of("000660")), argThat(subscriptions -> subscriptions.size() == 1
                && subscriptions.getFirst().market().equals("NASDAQ")
                && subscriptions.getFirst().symbol().equals("AAPL")
                && subscriptions.getFirst().trKey().equals("DNASAAPL")));
        verify(finnhubClient).updateInstrumentMarkets(Map.of("AAPL", "NASDAQ"));
        verify(finnhubClient).start(List.of("AAPL"));
    }

    @Test
    void releasedSelectionIsRemovedAfterZeroGraceAndP1Remains() {
        StockRepository stockRepository = mock(StockRepository.class);
        WatchlistRepository watchlistRepository = mock(WatchlistRepository.class);
        PortfolioHoldingRepository holdingRepository = mock(PortfolioHoldingRepository.class);
        PriceAlertRepository alertRepository = mock(PriceAlertRepository.class);
        Stock p1 = stock("KRX", "005930");
        Stock selected = stock("KRX", "000660");
        when(watchlistRepository.findDistinctActiveStocksForRealtime()).thenReturn(List.of(p1));
        when(holdingRepository.findDistinctActiveStocksForRealtime()).thenReturn(List.of());
        when(alertRepository.findDistinctActiveStocksForRealtime(AlertStatus.ACTIVE)).thenReturn(List.of());
        when(stockRepository.findByMarketAndSymbolAndActiveTrue("KRX", "000660"))
                .thenReturn(Optional.of(selected));
        manager = manager(
                stockRepository, watchlistRepository, holdingRepository, alertRepository,
                mock(KisRealtimeClient.class), mock(FinnhubRealtimeClient.class),
                40, Duration.ZERO);

        manager.select("session-1", "KRX", "000660");
        manager.reconcileNow();
        assertThat(manager.currentPlan().krxSymbols()).contains("000660", "005930");

        manager.releaseSession("session-1");
        manager.reconcileNow();
        assertThat(manager.currentPlan().krxSymbols()).containsExactly("005930");
    }

    @Test
    void marksFinnhubSymbolMarketUnknownWhenSubscriptionContextsConflict() {
        StockRepository stockRepository = mock(StockRepository.class);
        WatchlistRepository watchlistRepository = mock(WatchlistRepository.class);
        PortfolioHoldingRepository holdingRepository = mock(PortfolioHoldingRepository.class);
        PriceAlertRepository alertRepository = mock(PriceAlertRepository.class);
        KisRealtimeClient kisClient = mock(KisRealtimeClient.class);
        FinnhubRealtimeClient finnhubClient = mock(FinnhubRealtimeClient.class);
        Stock nasdaqDuplicate = stock("NASDAQ", "DUP");
        Stock nyseDuplicate = stock("NYSE", "DUP");
        when(watchlistRepository.findDistinctActiveStocksForRealtime())
                .thenReturn(List.of(nasdaqDuplicate, nyseDuplicate));
        when(holdingRepository.findDistinctActiveStocksForRealtime()).thenReturn(List.of());
        when(alertRepository.findDistinctActiveStocksForRealtime(AlertStatus.ACTIVE)).thenReturn(List.of());
        manager = manager(
                stockRepository, watchlistRepository, holdingRepository, alertRepository,
                kisClient, finnhubClient, 40, Duration.ZERO);

        manager.reconcileNow();

        assertThat(manager.currentPlan().usSymbols()).containsExactly("DUP");
        verify(finnhubClient).updateInstrumentMarkets(Map.of("DUP", "UNKNOWN"));
        verify(finnhubClient).start(List.of("DUP"));
    }

    private RealtimeSubscriptionManager manager(
            StockRepository stockRepository,
            WatchlistRepository watchlistRepository,
            PortfolioHoldingRepository holdingRepository,
            PriceAlertRepository alertRepository,
            KisRealtimeClient kisClient,
            FinnhubRealtimeClient finnhubClient,
            int kisLimit,
            Duration grace) {
        return new RealtimeSubscriptionManager(
                "LIVE", true, kisLimit, 40, 50, grace, Duration.ofSeconds(15),
                stockRepository, watchlistRepository, holdingRepository, alertRepository,
                kisClient, finnhubClient, new RealtimeQuoteHub());
    }

    private Stock stock(String market, String symbol) {
        Stock stock = mock(Stock.class);
        when(stock.getMarket()).thenReturn(market);
        when(stock.getSymbol()).thenReturn(symbol);
        when(stock.isActive()).thenReturn(true);
        when(stock.isTradable()).thenReturn(true);
        return stock;
    }
}
