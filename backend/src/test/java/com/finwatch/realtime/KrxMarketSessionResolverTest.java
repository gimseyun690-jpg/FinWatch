package com.finwatch.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class KrxMarketSessionResolverTest {

    private final KrxMarketSessionResolver resolver = new KrxMarketSessionResolver();

    @Test
    void resolvesPreMarketRegularAfterHoursAndClosedForKrx() {
        // 2026-08-04 Tuesday (Weekday)
        // 08:45 KST (23:45 UTC prev day) -> PRE_MARKET
        assertThat(resolver.resolve(Instant.parse("2026-08-03T23:45:00Z"))).isEqualTo(MarketSessionStatus.PRE_MARKET);

        // 10:30 KST (01:30 UTC) -> REGULAR
        assertThat(resolver.resolve(Instant.parse("2026-08-04T01:30:00Z"))).isEqualTo(MarketSessionStatus.REGULAR);

        // 16:30 KST (07:30 UTC) -> AFTER_HOURS (애프터장)
        assertThat(resolver.resolve(Instant.parse("2026-08-04T07:30:00Z"))).isEqualTo(MarketSessionStatus.AFTER_HOURS);

        // 20:00 KST (11:00 UTC) -> CLOSED
        assertThat(resolver.resolve(Instant.parse("2026-08-04T11:00:00Z"))).isEqualTo(MarketSessionStatus.CLOSED);

        // Saturday 10:00 KST -> CLOSED
        assertThat(resolver.resolve(Instant.parse("2026-08-08T01:00:00Z"))).isEqualTo(MarketSessionStatus.CLOSED);
    }
}
