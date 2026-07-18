package com.finwatch.realtime;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;

public record IntradayCandle(
        String market,
        String symbol,
        Instant time,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal volume,
        String currency,
        String source) {

    public IntradayCandle {
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
