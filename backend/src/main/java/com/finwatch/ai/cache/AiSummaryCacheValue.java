package com.finwatch.ai.cache;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record AiSummaryCacheValue(
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
        BigDecimal originalEstimatedCost,
        Instant generatedAt) implements Serializable {
}
