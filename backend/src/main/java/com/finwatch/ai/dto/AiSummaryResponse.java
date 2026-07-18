package com.finwatch.ai.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record AiSummaryResponse(
        Long analysisId,
        Long newsId,
        String symbol,
        String summary,
        List<String> keyPoints,
        List<String> positiveFactors,
        List<String> riskFactors,
        List<String> mentionedCompanies,
        List<String> evidenceSegments,
        List<String> keywords,
        String sentiment,
        String modelName,
        String promptVersion,
        String analysisScope,
        int originalCharacters,
        int processedCharacters,
        int providerCallCount,
        boolean cacheHit,
        int inputTokens,
        int outputTokens,
        BigDecimal estimatedCost,
        String costCurrency,
        int responseTimeMs,
        Instant generatedAt) {
}
