package com.finwatch.portfolio.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PortfolioResponse(
        List<CurrencySummary> currencySummaries,
        List<Holding> holdings) {

    public record Holding(
            Long id,
            String symbol,
            String name,
            String market,
            String currency,
            BigDecimal quantity,
            BigDecimal averagePurchasePrice,
            BigDecimal latestPrice,
            Instant priceAsOf,
            String priceSource,
            BigDecimal purchaseAmount,
            BigDecimal evaluationAmount,
            BigDecimal profitLoss,
            BigDecimal returnRate,
            String valuationStatus,
            Instant updatedAt) {
    }

    public record CurrencySummary(
            String currency,
            BigDecimal totalPurchaseAmount,
            BigDecimal totalEvaluationAmount,
            BigDecimal profitLoss,
            BigDecimal returnRate,
            boolean valuationComplete) {
    }
}
