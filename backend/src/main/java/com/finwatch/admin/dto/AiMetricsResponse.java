package com.finwatch.admin.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record AiMetricsResponse(
        LocalDate from,
        LocalDate to,
        long requestCount,
        long modelCallCount,
        long cacheHitCount,
        long cacheMissCount,
        long inputTokens,
        long outputTokens,
        long totalTokens,
        BigDecimal estimatedCost,
        BigDecimal cacheHitRate,
        BigDecimal savedEstimatedCost,
        BigDecimal averageResponseTimeMs,
        String costCurrency,
        List<AiFeatureUsageResponse> featureUsage) {
}
