package com.finwatch.fx.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.finwatch.data.provider.ProviderException;

class FrankfurterFxRateProviderTest {

    @Test
    void normalizesLatestUsdKrwReferenceRate() {
        Instant fetchedAt = Instant.parse("2026-07-15T06:00:00Z");
        var quote = FrankfurterFxRateProvider.normalizeLatest(
                Map.of("date", "2026-07-15", "base", "USD", "quote", "KRW", "rate", 1495.41),
                "USD", "KRW", fetchedAt);

        assertThat(quote.rate()).isEqualByComparingTo("1495.41");
        assertThat(quote.asOf()).isEqualTo(Instant.parse("2026-07-15T00:00:00Z"));
        assertThat(quote.fetchedAt()).isEqualTo(fetchedAt);
        assertThat(quote.providerSymbol()).contains("2026-07-15");
    }

    @Test
    void rejectsReversedPairAndSkipsMalformedHistoryRows() {
        assertThatThrownBy(() -> FrankfurterFxRateProvider.normalizeLatest(
                Map.of("date", "2026-07-15", "base", "KRW", "quote", "USD", "rate", 0.0006),
                "USD", "KRW", Instant.now()))
                .isInstanceOf(ProviderException.class);

        var bars = FrankfurterFxRateProvider.normalizeHistory(List.of(
                Map.of("date", "2026-07-14", "base", "USD", "quote", "KRW", "rate", 1495.39),
                Map.of("date", "invalid", "base", "USD", "quote", "KRW", "rate", BigDecimal.ZERO)),
                "USD", "KRW");

        assertThat(bars).singleElement().satisfies(bar -> {
            assertThat(bar.close()).isEqualByComparingTo("1495.39");
            assertThat(bar.open()).isEqualByComparingTo("1495.39");
        });
        assertThat(new FrankfurterFxRateProvider(null).historyRateType()).isEqualTo("REFERENCE");
    }
}
