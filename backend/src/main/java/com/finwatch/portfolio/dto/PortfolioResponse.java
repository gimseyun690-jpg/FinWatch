package com.finwatch.portfolio.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PortfolioResponse(
        String baseCurrency,
        BigDecimal baseCurrencyTotalEvaluationAmount,
        BigDecimal baseCurrencyTotalPurchaseAmount,
        BigDecimal baseCurrencyProfitLoss,
        boolean conversionComplete,
        boolean profitLossComplete,
        List<AppliedFxRate> fxRates,
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
            BigDecimal averagePurchaseFxRate,
            String purchaseFxBaseCurrency,
            String purchaseFxQuoteCurrency,
            BigDecimal latestPrice,
            Instant priceAsOf,
            String priceSource,
            BigDecimal purchaseAmount,
            BigDecimal evaluationAmount,
            BigDecimal profitLoss,
            BigDecimal returnRate,
            BigDecimal convertedEvaluationAmount,
            BigDecimal convertedPurchaseAmount,
            BigDecimal convertedProfitLoss,
            BigDecimal fxEffectApproximation,
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

    public record AppliedFxRate(
            String pair,
            BigDecimal rate,
            Instant asOf,
            String source,
            String rateType,
            String freshness) {
    }
}
