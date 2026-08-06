package com.finwatch.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class UsMarketSessionResolverTest {

    private final UsMarketSessionResolver resolver = new UsMarketSessionResolver();

    @Test
    void resolvesSummerExtendedAndRegularSessionBoundariesInNewYorkTime() {
        assertThat(resolve("2026-07-14T07:59:59.999Z")).isEqualTo(MarketSessionStatus.CLOSED);
        assertThat(resolve("2026-07-14T08:00:00Z")).isEqualTo(MarketSessionStatus.PRE_MARKET);
        assertThat(resolve("2026-07-14T13:29:59.999Z")).isEqualTo(MarketSessionStatus.PRE_MARKET);
        assertThat(resolve("2026-07-14T13:30:00Z")).isEqualTo(MarketSessionStatus.REGULAR);
        assertThat(resolve("2026-07-14T19:59:59.999Z")).isEqualTo(MarketSessionStatus.REGULAR);
        assertThat(resolve("2026-07-14T20:00:00Z")).isEqualTo(MarketSessionStatus.AFTER_HOURS);
        assertThat(resolve("2026-07-14T23:59:59.999Z")).isEqualTo(MarketSessionStatus.AFTER_HOURS);
        assertThat(resolve("2026-07-15T00:00:00Z")).isEqualTo(MarketSessionStatus.CLOSED);
    }

    @Test
    void appliesEasternStandardTimeAndClosesWeekends() {
        assertThat(resolve("2026-01-05T09:00:00Z")).isEqualTo(MarketSessionStatus.PRE_MARKET);
        assertThat(resolve("2026-01-05T14:30:00Z")).isEqualTo(MarketSessionStatus.REGULAR);
        assertThat(resolve("2026-01-05T21:00:00Z")).isEqualTo(MarketSessionStatus.AFTER_HOURS);
        assertThat(resolve("2026-07-18T14:00:00Z")).isEqualTo(MarketSessionStatus.CLOSED);
    }

    @Test
    void reportsUnknownWhenProviderTimeIsUnavailable() {
        assertThat(resolver.resolve(null)).isEqualTo(MarketSessionStatus.UNKNOWN);
    }

    private MarketSessionStatus resolve(String instant) {
        return resolver.resolve(Instant.parse(instant));
    }
}
