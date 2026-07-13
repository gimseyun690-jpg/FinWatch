package com.finwatch.stock.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class StockResponses {

    private StockResponses() {
    }

    public record StockSummary(
            String symbol,
            String name,
            String market,
            String currency,
            BigDecimal price,
            BigDecimal change,
            BigDecimal changeRate,
            BigDecimal volume,
            Instant asOf,
            String source) {
    }

    public record PricePoint(
            Instant time,
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close,
            BigDecimal volume,
            TechnicalSeriesPoint indicators) {
    }

    public record TechnicalSeriesPoint(
            BigDecimal ma5,
            BigDecimal ma20,
            BigDecimal ma60,
            BigDecimal volumeMa20,
            BigDecimal bollingerUpper,
            BigDecimal bollingerMiddle,
            BigDecimal bollingerLower,
            BigDecimal rsi,
            BigDecimal macd,
            BigDecimal macdSignal,
            BigDecimal macdHistogram,
            BigDecimal atr) {
    }

    public record PriceHistory(String symbol, String interval, String period, List<PricePoint> items) {
    }

    public record MovingAverages(
            BigDecimal ma5,
            BigDecimal ma20,
            BigDecimal ma60,
            String signal) {
    }

    public record Rsi(int period, String method, BigDecimal value, String signal) {
    }

    public record Macd(
            BigDecimal value,
            BigDecimal signalLine,
            BigDecimal histogram,
            String signal) {
    }

    public record BollingerBands(
            int period,
            int deviationMultiplier,
            BigDecimal upper,
            BigDecimal middle,
            BigDecimal lower,
            BigDecimal bandwidthPercent) {
    }

    public record Atr(int period, BigDecimal value, BigDecimal percent) {
    }

    public record TechnicalEvent(
            Instant time,
            String type,
            String signal) {
    }

    public record TechnicalAnalysis(
            String symbol,
            Instant calculatedAt,
            String calculationVersion,
            String summarySignal,
            MovingAverages movingAverages,
            Rsi rsi,
            Macd macd,
            BollingerBands bollingerBands,
            Atr atr,
            BigDecimal volumeMa20,
            List<TechnicalEvent> events,
            String disclaimer) {
    }
}
