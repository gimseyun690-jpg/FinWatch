package com.finwatch.fx.provider;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface FxRateProvider {
    String providerId();
    boolean supports(String baseCurrency, String quoteCurrency);
    FxQuote latest(String baseCurrency, String quoteCurrency);
    List<FxBar> history(String baseCurrency, String quoteCurrency, Instant from, Instant to);

    default String historyRateType() {
        return "DELAYED";
    }

    record FxQuote(
            String baseCurrency,
            String quoteCurrency,
            BigDecimal rate,
            String rateType,
            String providerSymbol,
            Instant asOf,
            Instant fetchedAt) {
    }

    record FxBar(
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close,
            Instant asOf,
            String providerSymbol) {
    }
}
