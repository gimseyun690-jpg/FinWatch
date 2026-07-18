package com.finwatch.admin.dto;

import java.math.BigDecimal;

public record AiFeatureUsageResponse(
        String feature,
        long requestCount,
        long successCount,
        long failedCount,
        long modelCallCount,
        long cacheHitCount,
        long totalTokens,
        BigDecimal estimatedCost,
        BigDecimal savedEstimatedCost) {
}
