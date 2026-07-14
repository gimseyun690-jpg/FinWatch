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

    private LiveQuote quote(String symbol, String price, Instant asOf) {
        return new LiveQuote(
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
