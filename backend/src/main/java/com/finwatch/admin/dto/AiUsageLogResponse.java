package com.finwatch.admin.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.finwatch.ai.domain.AiUsageLog;

public record AiUsageLogResponse(
        Long id,
        String requestId,
        String featureType,
        String targetType,
        Long targetId,
        String modelName,
        int inputTokens,
        int outputTokens,
        int totalTokens,
        BigDecimal estimatedCost,
        BigDecimal savedEstimatedCost,
        boolean cacheHit,
        int responseTimeMs,
        String promptVersion,
        String status,
        Instant createdAt) {

    public static AiUsageLogResponse from(AiUsageLog log) {
        return new AiUsageLogResponse(
                log.getId(),
                log.getRequestId(),
                log.getFeatureType(),
                log.getTargetType(),
                log.getTargetId(),
                log.getModelName(),
                log.getInputTokens(),
                log.getOutputTokens(),
                log.getInputTokens() + log.getOutputTokens(),
                log.getEstimatedCost(),
                log.getSavedEstimatedCost(),
                log.isCacheHit(),
                log.getResponseTimeMs(),
                log.getPromptVersion(),
                log.getStatus(),
                log.getCreatedAt());
    }
}
