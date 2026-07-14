package com.finwatch.realtime;

import java.math.BigDecimal;
import java.time.Instant;

public record LiveQuote(
        String symbol,
        BigDecimal price,
        BigDecimal change,
        BigDecimal changeRate,
        BigDecimal volume,
        String currency,
        Instant asOf,
        String source,
        String sessionStatus) {
}
