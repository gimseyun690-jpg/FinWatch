package com.finwatch.realtime;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;

public record LiveQuote(
        String market,
        String symbol,
        BigDecimal price,
        BigDecimal change,
        BigDecimal changeRate,
        BigDecimal volume,
        String currency,
        Instant asOf,
        String source,
        String sessionStatus) {

    public LiveQuote {
        market = normalize(market, "UNKNOWN");
        symbol = normalize(symbol, "");
    }

    public String canonicalKey() {
        return market + ":" + symbol;
    }

    private static String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
