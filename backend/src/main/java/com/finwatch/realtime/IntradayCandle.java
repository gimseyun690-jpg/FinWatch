package com.finwatch.realtime;

import java.math.BigDecimal;
import java.time.Instant;

public record IntradayCandle(
        String symbol,
        Instant time,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal volume,
        String currency,
        String source) {
}
