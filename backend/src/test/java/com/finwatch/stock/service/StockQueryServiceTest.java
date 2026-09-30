package com.finwatch.stock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.finwatch.realtime.RealtimeCandleAggregator;
import com.finwatch.realtime.RealtimeQuoteHub;
import com.finwatch.stock.domain.MarketPrice;
import com.finwatch.stock.domain.Stock;
import com.finwatch.stock.repository.MarketPriceRepository;
import com.finwatch.stock.repository.StockRepository;
import com.finwatch.technical.TechnicalAnalysisCalculator;

class StockQueryServiceTest {

    private final StockRepository stocks = mock(StockRepository.class);
    private final MarketPriceRepository prices = mock(MarketPriceRepository.class);
    private final RealtimeQuoteHub quotes = mock(RealtimeQuoteHub.class);
    private final StockQueryService service = new StockQueryService(
            stocks, prices, new TechnicalAnalysisCalculator(), quotes, mock(RealtimeCandleAggregator.class));

    @Test
    void stockSummaryFetchesOnlyTwoDailyBarsForPreviousClose() {
        Stock stock = stock();
        MarketPrice latest = price(stock, "100", "2026-09-30T06:30:00Z");
        MarketPrice previous = price(stock, "90", "2026-09-29T06:30:00Z");
        when(stocks.findAllActiveWithPrices()).thenReturn(List.of(stock));
        when(prices.findTopByStockIdOrderByRecordedAtDesc(1L)).thenReturn(Optional.of(latest));
        when(prices.findTop2ByStockIdAndIntervalOrderByRecordedAtDesc(1L, "1D"))
                .thenReturn(List.of(latest, previous));

        var result = service.getStocks().getFirst();

        assertThat(result.price()).isEqualByComparingTo("100");
        assertThat(result.change()).isEqualByComparingTo("10");
        assertThat(result.changeRate()).isEqualByComparingTo("11.1111");
        verify(prices, never()).findAllByStockIdAndIntervalOrderByRecordedAtAsc(1L, "1D");
    }

    @Test
    void canonicalMetadataCountsHistoryWithoutLoadingItIntoHeap() {
        Stock stock = stock();
        MarketPrice latest = price(stock, "100", "2026-09-30T06:30:00Z");
        when(stocks.findByMarketAndSymbolAndActiveTrue("KRX", "005930"))
                .thenReturn(Optional.of(stock));
        when(prices.countByStockIdAndInterval(1L, "1D")).thenReturn(10_000L);
        when(prices.findTopByStockIdAndIntervalOrderByRecordedAtDesc(1L, "1D"))
                .thenReturn(Optional.of(latest));
        when(prices.findTopByStockIdOrderByRecordedAtDesc(1L)).thenReturn(Optional.of(latest));
        when(prices.findTop2ByStockIdAndIntervalOrderByRecordedAtDesc(1L, "1D"))
                .thenReturn(List.of(latest));

        var result = service.getStock("KRX", "005930");

        assertThat(result.historyAvailable()).isTrue();
        assertThat(result.historyPoints()).isEqualTo(10_000);
        assertThat(result.historyAsOf()).isEqualTo(latest.getRecordedAt());
        assertThat(result.dataAvailability()).isEqualTo("READY");
        verify(prices, never()).findAllByStockIdAndIntervalOrderByRecordedAtAsc(1L, "1D");
    }

    private Stock stock() {
        Stock stock = mock(Stock.class);
        when(stock.getId()).thenReturn(1L);
        when(stock.getMarket()).thenReturn("KRX");
        when(stock.getSymbol()).thenReturn("005930");
        when(stock.getName()).thenReturn("삼성전자");
        when(stock.getCurrency()).thenReturn("KRW");
        when(quotes.find("KRX", "005930")).thenReturn(Optional.empty());
        return stock;
    }

    private MarketPrice price(Stock stock, String close, String time) {
        BigDecimal value = new BigDecimal(close);
        return MarketPrice.create(stock, "1D", value, value, value, value,
                BigDecimal.TEN, Instant.parse(time), "KIS_REST");
    }
}
