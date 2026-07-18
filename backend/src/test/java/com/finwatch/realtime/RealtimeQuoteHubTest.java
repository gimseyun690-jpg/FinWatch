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

    private LiveQuote quote(String symbol, String price, Instant asOf) {
        return quote("KRX", symbol, price, asOf);
    }

    private LiveQuote quote(String market, String symbol, String price, Instant asOf) {
        return new LiveQuote(
                market,
                symbol,
                new BigDecimal(price),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ONE,
                "KRW",
                asOf,
                "TEST",
                "LIVE");
    }
}
