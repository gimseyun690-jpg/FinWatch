package com.finwatch.ai.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.finwatch.ai.dto.PortfolioEvaluationInput.Evidence;

public record PortfolioEvaluationResponse(
        Long evaluationId,
        Instant snapshotAt,
        String baseCurrency,
        String balanceStatus,
        String headline,
        String summary,
        Statement diversification,
        Statement concentration,
        Statement currencyExposure,
        Statement performanceContext,
        List<Statement> strengths,
        List<Statement> riskFactors,
        List<Statement> reviewPoints,
        List<String> dataLimitations,
        List<Evidence> evidence,
        Audit audit,
        String disclaimer) implements Serializable {

    public record Statement(String text, List<String> evidenceIds) implements Serializable {
    }

    public record Audit(
            String inputHash,
            String positionsHash,
            String promptVersion,
            String modelName,
            boolean cacheHit,
            int inputTokens,
            int outputTokens,
            BigDecimal estimatedCost,
            BigDecimal savedEstimatedCost,
            String costCurrency,
            int responseTimeMs,
            Instant generatedAt) implements Serializable {
    }
}
