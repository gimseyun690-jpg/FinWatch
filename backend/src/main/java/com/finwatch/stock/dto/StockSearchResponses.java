package com.finwatch.stock.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class StockSearchResponses {

    private StockSearchResponses() {
    }

    public record StockSearchItem(
            Long stockId,
            String market,
            String exchange,
            String symbol,
            String name,
            String englishName,
            String instrumentType,
            String currency,
            boolean active,
            boolean tradable,
            String status,
            String dataAvailability,
            String source) {
    }

    public record StockSearchPage(
            List<StockSearchItem> items,
            int page,
            int size,
            long totalElements,
            int totalPages,
            Instant catalogAsOf) {
    }

    public record CanonicalStockDetail(
            Long stockId,
            String market,
            String exchange,
            String symbol,
            String name,
            String englishName,
            String instrumentType,
            String currency,
            boolean active,
            boolean tradable,
            String status,
            String dataAvailability,
            String catalogSource,
            Instant catalogUpdatedAt,
            BigDecimal price,
            BigDecimal change,
            BigDecimal changeRate,
            BigDecimal volume,
            Instant asOf,
            String source) {
    }
}
