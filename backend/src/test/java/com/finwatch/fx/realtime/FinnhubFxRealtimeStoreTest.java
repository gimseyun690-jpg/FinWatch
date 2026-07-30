package com.finwatch.fx.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class FinnhubFxRealtimeStoreTest {

    private final FinnhubFxRealtimeStore store = new FinnhubFxRealtimeStore();

    @Test
    void returnsOnlyRecentMatchingProviderTimestampedQuotes() {
        Instant now = Instant.parse("2026-07-30T03:00:00Z");
        assertThat(store.accept(
                "OANDA:USD_KRW",
                new BigDecimal("1382.50"),
                now.minusSeconds(10),
                now)).isTrue();

        assertThat(store.latest("oanda:usd_krw", Duration.ofMinutes(2), now))
                .hasValueSatisfying(value -> {
                    assertThat(value.rate()).isEqualByComparingTo("1382.50");
                    assertThat(value.asOf()).isEqualTo(now.minusSeconds(10));
                });
        assertThat(store.latest("OANDA:EUR_USD", Duration.ofMinutes(2), now)).isEmpty();
        assertThat(store.latest("OANDA:USD_KRW", Duration.ofSeconds(5), now)).isEmpty();
    }

    @Test
    void rejectsInvalidAndOutOfOrderQuotes() {
        Instant now = Instant.parse("2026-07-30T03:00:00Z");
        assertThat(store.accept("OANDA:USD_KRW", BigDecimal.ZERO, now, now)).isFalse();
        assertThat(store.latest("OANDA:USD_KRW", Duration.ofMinutes(2), now)).isEmpty();

        assertThat(store.accept("OANDA:USD_KRW", new BigDecimal("1382.50"), now, now)).isTrue();
        assertThat(store.accept(
                "OANDA:USD_KRW",
                new BigDecimal("1200"),
                now.minusSeconds(1),
                now)).isFalse();
        assertThat(store.accept("OANDA:USD_KRW", new BigDecimal("1400"), now, now)).isFalse();

        assertThat(store.latest("OANDA:USD_KRW", Duration.ofMinutes(2), now))
                .get().extracting(FinnhubFxRealtimeStore.Snapshot::rate)
                .isEqualTo(new BigDecimal("1382.50"));
    }
}
