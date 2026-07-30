package com.finwatch.ai.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record PortfolioEvaluationInput(
        String baseCurrency,
        Instant snapshotAt,
        Instant windowStartedAt,
        int holdingCount,
        int valuedHoldingCount,
        boolean conversionComplete,
        boolean profitLossComplete,
        BigDecimal totalEvaluationAmount,
        BigDecimal totalPurchaseAmount,
        BigDecimal profitLoss,
        BigDecimal returnRate,
        BigDecimal topPositionWeight,
        BigDecimal topThreeWeight,
        BigDecimal concentrationHhi,
        String concentrationBand,
        List<CurrencyExposure> currencyExposures,
        List<Position> positions,
        List<Evidence> evidence,
        List<String> serverDataLimitations) implements Serializable {

    public record CurrencyExposure(
            String currency,
            BigDecimal evaluationAmount,
            BigDecimal weightPercent,
            int holdingCount) implements Serializable {
    }

    public record Position(
            String symbol,
            String name,
            String market,
            String currency,
            BigDecimal nativeEvaluationAmount,
            BigDecimal convertedEvaluationAmount,
            BigDecimal weightPercent,
            BigDecimal returnRate,
            String valuationStatus) implements Serializable {
    }

    public record Evidence(
            String id,
            String category,
            Map<String, String> values,
            String displayValue) implements Serializable {
    }
}
