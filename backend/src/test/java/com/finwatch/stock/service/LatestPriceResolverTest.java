package com.finwatch.stock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.finwatch.realtime.LiveQuote;
import com.finwatch.realtime.RealtimeQuoteHub;
import com.finwatch.stock.domain.MarketPrice;
import com.finwatch.stock.domain.Stock;
import com.finwatch.stock.repository.MarketPriceRepository;

class LatestPriceResolverTest {

    private final MarketPriceRepository marketPriceRepository = mock(MarketPriceRepository.class);
    private final RealtimeQuoteHub quoteHub = new RealtimeQuoteHub();
    private final LatestPriceResolver resolver = new LatestPriceResolver(marketPriceRepository, quoteHub);

    @Test
    void prefersNewerRealtimeQuote() {
        Stock stock = stock("000660");
        storedPrice(stock, "1750000", Instant.parse("2026-07-14T01:00:00Z"));
        quoteHub.publish(quote("000660", "1763000", Instant.parse("2026-07-14T01:00:02Z")));

        var result = resolver.resolve(stock).orElseThrow();

        assertThat(result.price()).isEqualByComparingTo("1763000");
        assertThat(result.source()).isEqualTo("KIS_WS");
        assertThat(result.realtime()).isTrue();
    }

    @Test
    void doesNotReplaceNewerStoredPriceWithStaleSnapshot() {
        Stock stock = stock("AAPL");
        storedPrice(stock, "320.10", Instant.parse("2026-07-14T01:00:00Z"));
        quoteHub.publish(new LiveQuote(
                "AAPL", new BigDecimal("317.31"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                "USD", Instant.parse("2026-07-13T20:00:00Z"), "FINNHUB_REST", "SNAPSHOT"));

        var result = resolver.resolve(stock).orElseThrow();

        assertThat(result.price()).isEqualByComparingTo("320.10");
        assertThat(result.source()).isEqualTo("DB");
        assertThat(result.realtime()).isFalse();
    }

    private Stock stock(String symbol) {
        Stock stock = mock(Stock.class);
        when(stock.getId()).thenReturn(1L);
        when(stock.getSymbol()).thenReturn(symbol);
        return stock;
    }

    private void storedPrice(Stock stock, String price, Instant asOf) {
        MarketPrice stored = mock(MarketPrice.class);
        when(stored.getClosePrice()).thenReturn(new BigDecimal(price));
        when(stored.getRecordedAt()).thenReturn(asOf);
        when(stored.getSource()).thenReturn("DB");
        when(marketPriceRepository.findTopByStockIdOrderByRecordedAtDesc(stock.getId()))
                .thenReturn(Optional.of(stored));
    }

    private LiveQuote quote(String symbol, String price, Instant asOf) {
        return new LiveQuote(
                symbol, new BigDecimal(price), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE,
                "KRW", asOf, "KIS_WS", "LIVE");
    }
}
