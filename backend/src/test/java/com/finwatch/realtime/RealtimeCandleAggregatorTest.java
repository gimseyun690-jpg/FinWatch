package com.finwatch.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class RealtimeCandleAggregatorTest {

    private final RealtimeQuoteHub quoteHub = new RealtimeQuoteHub();
    private final RealtimeCandleAggregator aggregator = new RealtimeCandleAggregator(quoteHub);

    @Test
    void aggregatesKisTicksIntoOneMinuteCandlesUsingCumulativeVolumeDelta() {
        quoteHub.publish(quote("000660", "100", "1000", "2026-07-14T01:00:01Z", "KIS_WS"));
        quoteHub.publish(quote("000660", "110", "1007", "2026-07-14T01:00:20Z", "KIS_WS"));
        quoteHub.publish(quote("000660", "95", "1012", "2026-07-14T01:00:50Z", "KIS_WS"));
        quoteHub.publish(quote("000660", "102", "1020", "2026-07-14T01:01:02Z", "KIS_WS"));

        assertThat(aggregator.find("000660", 10)).hasSize(2);
        IntradayCandle first = aggregator.find("000660", 10).getFirst();
        assertThat(first.time()).isEqualTo(Instant.parse("2026-07-14T01:00:00Z"));
        assertThat(first.open()).isEqualByComparingTo("100");
        assertThat(first.high()).isEqualByComparingTo("110");
        assertThat(first.low()).isEqualByComparingTo("95");
        assertThat(first.close()).isEqualByComparingTo("95");
        assertThat(first.volume()).isEqualByComparingTo("12");
        assertThat(aggregator.find("000660", 10).getLast().volume()).isEqualByComparingTo("8");
    }

    @Test
    void sumsFinnhubPerTradeVolumesAndIgnoresRestSnapshots() {
        quoteHub.publish(quote("AAPL", "200", "999", "2026-07-14T01:00:00Z", "FINNHUB_REST", "SNAPSHOT"));
        quoteHub.publish(quote("AAPL", "201", "3", "2026-07-14T01:00:02Z", "FINNHUB_WS"));
        quoteHub.publish(quote("AAPL", "202", "4", "2026-07-14T01:00:03Z", "FINNHUB_WS"));

        assertThat(aggregator.find("AAPL", 10)).singleElement().satisfies(candle -> {
            assertThat(candle.open()).isEqualByComparingTo("201");
            assertThat(candle.close()).isEqualByComparingTo("202");
            assertThat(candle.volume()).isEqualByComparingTo("7");
        });
    }

    private LiveQuote quote(String symbol, String price, String volume, String asOf, String source) {
        return quote(symbol, price, volume, asOf, source, "LIVE");
    }

    private LiveQuote quote(
            String symbol,
            String price,
            String volume,
            String asOf,
            String source,
            String sessionStatus) {
        return new LiveQuote(
                symbol,
                new BigDecimal(price),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal(volume),
                source.startsWith("KIS") ? "KRW" : "USD",
                Instant.parse(asOf),
                source,
                sessionStatus);
    }
}
