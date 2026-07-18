package com.finwatch.fx.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class FxRateResponses {
    private FxRateResponses() { }

    public record LatestFxRate(
            String baseCurrency,
            String quoteCurrency,
            BigDecimal rate,
            BigDecimal previousClose,
            BigDecimal change,
            BigDecimal changeRate,
            String rateType,
            String source,
            String providerSymbol,
            Instant asOf,
            Instant fetchedAt,
            String freshness) {
    }

    public record FxHistory(
            String baseCurrency,
            String quoteCurrency,
            String period,
            String interval,
            String rateType,
            List<FxHistoryItem> items) {
    }

    public record FxHistoryItem(
            Instant time,
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close,
            String source) {
    }

    public record FxPair(String baseCurrency, String quoteCurrency, String displayName, boolean active) { }
}
