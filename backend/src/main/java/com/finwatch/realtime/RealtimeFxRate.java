package com.finwatch.realtime;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;

public record RealtimeFxRate(
        String baseCurrency,
        String quoteCurrency,
        BigDecimal rate,
        String rateType,
        String source,
        String providerSymbol,
        Instant asOf,
        Instant fetchedAt) {

    public RealtimeFxRate {
        baseCurrency = normalize(baseCurrency);
        quoteCurrency = normalize(quoteCurrency);
        rateType = normalize(rateType);
        source = normalize(source);
        providerSymbol = normalize(providerSymbol);
    }

    public String canonicalKey() {
        return baseCurrency + "/" + quoteCurrency;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
