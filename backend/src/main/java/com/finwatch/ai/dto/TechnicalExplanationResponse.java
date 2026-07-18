package com.finwatch.ai.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.finwatch.ai.dto.TechnicalExplanationInput.TechnicalEvidence;

public record TechnicalExplanationResponse(
        Long analysisId,
        String symbol,
        String market,
        String interval,
        Instant latestRecordedAt,
        String source,
        String freshness,
        String calculationVersion,
        String promptVersion,
        String inputHash,
        String summarySignal,
        String summary,
        String trendExplanation,
        String momentumExplanation,
        String volatilityExplanation,
        String volumeExplanation,
        List<SignalExplanation> supportingSignals,
        List<SignalExplanation> conflictingSignals,
        List<String> riskNotes,
        List<String> dataLimitations,
        List<TechnicalEvidence> evidence,
        String modelName,
        boolean cacheHit,
        int inputTokens,
        int outputTokens,
        BigDecimal estimatedCost,
        String costCurrency,
        int responseTimeMs,
        Instant generatedAt,
        String disclaimer) implements Serializable {

    public record SignalExplanation(String text, List<String> evidenceIds) implements Serializable {
    }
}
