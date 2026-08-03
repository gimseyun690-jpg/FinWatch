package com.finwatch.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;

import org.junit.jupiter.api.Test;

class RealtimeQuoteHubTest {

    @Test
    void keepsNewestQuoteAndDoesNotBroadcastStaleTicks() {
        RealtimeQuoteHub hub = new RealtimeQuoteHub();
        var events = new ArrayList<RealtimeEvent>();
        hub.addListener(events::add);
        Instant newestTime = Instant.parse("2026-07-14T01:00:01Z");

        hub.publish(quote("005930", "85000", newestTime));
        hub.publish(quote("005930", "84000", newestTime.minusSeconds(1)));

        assertThat(hub.find("005930")).get()
                .extracting(LiveQuote::price)
                .isEqualTo(new BigDecimal("85000"));
        assertThat(events).hasSize(1);
    }

    @Test
    void doesNotRebroadcastAnIdenticalQuoteWithTheSameProviderTimestamp() {
        RealtimeQuoteHub hub = new RealtimeQuoteHub();
        var events = new ArrayList<RealtimeEvent>();
        hub.addListener(events::add);
        LiveQuote quote = quote("AAPL", "201.25", Instant.parse("2026-07-14T14:00:01Z"));

        hub.publish(quote);
        hub.publish(quote);

        assertThat(events).hasSize(1);
    }

    @Test
    void websocketTradeSupersedesNewerRestSnapshotAndCannotBeOverwrittenByRest() {
        RealtimeQuoteHub hub = new RealtimeQuoteHub();
        var events = new ArrayList<RealtimeEvent>();
        hub.addListener(events::add);
        Instant tradeTime = Instant.parse("2026-07-14T01:00:03Z");

        hub.publish(quote("KRX", "005930", "85000", tradeTime.plusSeconds(2), "KIS_UNIFIED_REST", "SNAPSHOT"));
        hub.publish(quote("KRX", "005930", "85100", tradeTime, "KIS_UNIFIED_WS", "LIVE"));
        hub.publish(quote("KRX", "005930", "85200", tradeTime.plusSeconds(7), "KIS_UNIFIED_REST", "SNAPSHOT"));

        assertThat(hub.find("KRX", "005930")).get().satisfies(quote -> {
            assertThat(quote.price()).isEqualTo(new BigDecimal("85100"));
            assertThat(quote.asOf()).isEqualTo(tradeTime);
            assertThat(quote.source()).isEqualTo("KIS_UNIFIED_WS");
        });
        assertThat(events).hasSize(2);
    }

    @Test
    void staleWebsocketTradeDoesNotReplaceNewerWebsocketTrade() {
        RealtimeQuoteHub hub = new RealtimeQuoteHub();
        var events = new ArrayList<RealtimeEvent>();
        hub.addListener(events::add);
        Instant newestTime = Instant.parse("2026-07-14T01:00:05Z");

        hub.publish(quote("KRX", "005930", "85200", newestTime, "KIS_UNIFIED_WS", "LIVE"));
        hub.publish(quote("KRX", "005930", "85100", newestTime.minusSeconds(1), "KIS_KRX_WS", "LIVE"));

        assertThat(hub.find("KRX", "005930")).get().satisfies(quote -> {
            assertThat(quote.price()).isEqualTo(new BigDecimal("85200"));
            assertThat(quote.asOf()).isEqualTo(newestTime);
            assertThat(quote.source()).isEqualTo("KIS_UNIFIED_WS");
        });
        assertThat(events).hasSize(1);
    }

    @Test
    void freshRestSnapshotCanReplaceAStreamThatIsClearlyStale() {
        RealtimeQuoteHub hub = new RealtimeQuoteHub();
        Instant streamedAt = Instant.parse("2026-08-03T01:00:00Z");

        hub.publish(quote("KRX", "005930", "81000", streamedAt, "KIS_UNIFIED_WS", "LIVE"));
        hub.publish(quote(
                "KRX", "005930", "81500", streamedAt.plusSeconds(181), "KIS_UNIFIED_REST", "SNAPSHOT"));

        assertThat(hub.find("KRX", "005930")).get()
                .extracting(LiveQuote::price)
                .isEqualTo(new BigDecimal("81500"));
    }

    @Test
    void isolatesIdenticalSymbolsByMarketAndRejectsAmbiguousLegacyLookup() {
        RealtimeQuoteHub hub = new RealtimeQuoteHub();
        Instant asOf = Instant.parse("2026-07-14T01:00:01Z");

        hub.publish(quote("KRX", "DUP", "85000", asOf));
        hub.publish(quote("NASDAQ", "DUP", "125.50", asOf));

        assertThat(hub.find("KRX", "DUP")).get()
                .extracting(LiveQuote::price)
                .isEqualTo(new BigDecimal("85000"));
        assertThat(hub.find("NASDAQ", "DUP")).get()
                .extracting(LiveQuote::price)
                .isEqualTo(new BigDecimal("125.50"));
        assertThat(hub.find("DUP")).isEmpty();
        assertThat(hub.snapshot().quotes()).extracting(LiveQuote::canonicalKey)
                .containsExactly("KRX:DUP", "NASDAQ:DUP");
    }

    @Test
    void keepsNewestFxRateAndBroadcastsOnlyAcceptedProviderTime() {
        RealtimeQuoteHub hub = new RealtimeQuoteHub();
        var events = new ArrayList<RealtimeEvent>();
        hub.addListener(events::add);
        Instant fetchedAt = Instant.parse("2026-07-30T03:00:02Z");
        Instant newestTime = fetchedAt.minusSeconds(1);

        hub.publish(fxRate("1382.50", newestTime, fetchedAt));
        hub.publish(fxRate("1200", newestTime.minusSeconds(1), fetchedAt));
        hub.publish(fxRate("1400", fetchedAt.plusSeconds(301), fetchedAt));

        assertThat(hub.findFx("usd", "krw")).get()
                .extracting(RealtimeFxRate::rate)
                .isEqualTo(new BigDecimal("1382.50"));
        assertThat(hub.fxSnapshot()).singleElement()
                .extracting(RealtimeFxRate::canonicalKey)
                .isEqualTo("USD/KRW");
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.type()).isEqualTo("fx");
            assertThat(event.data()).isInstanceOf(RealtimeFxRate.class);
        });
    }

    private LiveQuote quote(String symbol, String price, Instant asOf) {
        return quote("KRX", symbol, price, asOf);
    }

    private LiveQuote quote(String market, String symbol, String price, Instant asOf) {
        return quote(market, symbol, price, asOf, "TEST", "LIVE");
    }

    private LiveQuote quote(
            String market,
            String symbol,
            String price,
            Instant asOf,
            String source,
            String sessionStatus) {
        return new LiveQuote(
                market,
                symbol,
                new BigDecimal(price),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ONE,
                "KRW",
                asOf,
                source,
                sessionStatus);
    }

    private RealtimeFxRate fxRate(String rate, Instant asOf, Instant fetchedAt) {
        return new RealtimeFxRate(
                "usd",
                "krw",
                new BigDecimal(rate),
                "live",
                "finnhub_ws",
                "oanda:usd_krw",
                asOf,
                fetchedAt);
    }
}
