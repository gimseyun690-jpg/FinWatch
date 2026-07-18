package com.finwatch.fx.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.finwatch.data.provider.ProviderException;

class FinnhubFxRateProviderTest {

    @Test
    void extractsUsdKrwWithoutReversingThePair() {
        BigDecimal rate = FinnhubFxRateProvider.extractRate(
                Map.of("base", "USD", "quote", Map.of("KRW", 1382.5)), "USD", "KRW");
        assertThat(rate).isEqualByComparingTo("1382.5");
        assertThatThrownBy(() -> FinnhubFxRateProvider.extractRate(
                Map.of("base", "KRW", "quote", Map.of("USD", 0.00072)), "USD", "KRW"))
                .isInstanceOf(ProviderException.class);
    }

    @Test
    void normalizesOnlyValidOhlcBars() {
        Map<String, Object> response = Map.of(
                "s", "ok",
                "o", List.of(1380, 1400),
                "h", List.of(1385, 1390),
                "l", List.of(1378, 1410),
                "c", List.of(1382.5, 1405),
                "t", List.of(1784018400L, 1784104800L));
        assertThat(FinnhubFxRateProvider.normalizeBars(response, "OANDA:USD_KRW"))
                .singleElement().satisfies(bar -> {
                    assertThat(bar.close()).isEqualByComparingTo("1382.5");
                    assertThat(bar.providerSymbol()).isEqualTo("OANDA:USD_KRW");
                });
    }
}
