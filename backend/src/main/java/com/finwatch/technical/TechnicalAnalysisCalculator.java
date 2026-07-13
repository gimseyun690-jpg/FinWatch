package com.finwatch.technical;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class TechnicalAnalysisCalculator {

    private static final int SCALE = 4;

    public Result calculate(List<BigDecimal> closes) {
        if (closes.size() < 60) {
            throw new IllegalArgumentException("기술적 분석에는 최소 60개의 종가가 필요합니다.");
        }

        BigDecimal latest = closes.getLast();
        BigDecimal ma5 = simpleMovingAverage(closes, 5);
        BigDecimal ma20 = simpleMovingAverage(closes, 20);
        BigDecimal ma60 = simpleMovingAverage(closes, 60);
        BigDecimal rsi = relativeStrengthIndex(closes, 14);
        MacdValue macd = macd(closes);

        Signal movingAverageSignal = movingAverageSignal(latest, ma5, ma20);
        Signal rsiSignal = rsi.compareTo(BigDecimal.valueOf(70)) >= 0
                ? Signal.SELL
                : rsi.compareTo(BigDecimal.valueOf(30)) <= 0 ? Signal.BUY : Signal.NEUTRAL;
        Signal macdSignal = macd.histogram().signum() > 0
                ? Signal.BUY
                : macd.histogram().signum() < 0 ? Signal.SELL : Signal.NEUTRAL;

        int score = signalScore(movingAverageSignal) + signalScore(rsiSignal) + signalScore(macdSignal);
        Signal summarySignal = score > 0 ? Signal.BUY : score < 0 ? Signal.SELL : Signal.NEUTRAL;

        return new Result(ma5, ma20, ma60, movingAverageSignal, rsi, rsiSignal, macd, macdSignal, summarySignal);
    }

    BigDecimal simpleMovingAverage(List<BigDecimal> values, int period) {
        if (values.size() < period) {
            throw new IllegalArgumentException("이동평균 계산 데이터가 부족합니다.");
        }
        return values.subList(values.size() - period, values.size()).stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(period), SCALE, RoundingMode.HALF_UP);
    }

    BigDecimal relativeStrengthIndex(List<BigDecimal> values, int period) {
        if (values.size() <= period) {
            throw new IllegalArgumentException("RSI 계산 데이터가 부족합니다.");
        }

        BigDecimal gain = BigDecimal.ZERO;
        BigDecimal loss = BigDecimal.ZERO;
        int start = values.size() - period;
        for (int index = start; index < values.size(); index++) {
            BigDecimal delta = values.get(index).subtract(values.get(index - 1));
            if (delta.signum() > 0) {
                gain = gain.add(delta);
            } else {
                loss = loss.add(delta.abs());
            }
        }

        if (gain.signum() == 0 && loss.signum() == 0) {
            return BigDecimal.valueOf(50).setScale(SCALE);
        }
        if (loss.signum() == 0) {
            return BigDecimal.valueOf(100).setScale(SCALE);
        }
        if (gain.signum() == 0) {
            return BigDecimal.ZERO.setScale(SCALE);
        }

        BigDecimal averageGain = gain.divide(BigDecimal.valueOf(period), 10, RoundingMode.HALF_UP);
        BigDecimal averageLoss = loss.divide(BigDecimal.valueOf(period), 10, RoundingMode.HALF_UP);
        BigDecimal relativeStrength = averageGain.divide(averageLoss, 10, RoundingMode.HALF_UP);
        return BigDecimal.valueOf(100)
                .subtract(BigDecimal.valueOf(100).divide(BigDecimal.ONE.add(relativeStrength), 10, RoundingMode.HALF_UP))
                .setScale(SCALE, RoundingMode.HALF_UP);
    }

    MacdValue macd(List<BigDecimal> values) {
        List<BigDecimal> fast = emaSeries(values, 12);
        List<BigDecimal> slow = emaSeries(values, 26);
        List<BigDecimal> macdSeries = new ArrayList<>(values.size());
        for (int index = 0; index < values.size(); index++) {
            macdSeries.add(fast.get(index).subtract(slow.get(index)));
        }
        List<BigDecimal> signalSeries = emaSeries(macdSeries, 9);
        BigDecimal value = macdSeries.getLast();
        BigDecimal signalLine = signalSeries.getLast();
        return new MacdValue(
                value.setScale(SCALE, RoundingMode.HALF_UP),
                signalLine.setScale(SCALE, RoundingMode.HALF_UP),
                value.subtract(signalLine).setScale(SCALE, RoundingMode.HALF_UP));
    }

    private List<BigDecimal> emaSeries(List<BigDecimal> values, int period) {
        BigDecimal multiplier = BigDecimal.valueOf(2)
                .divide(BigDecimal.valueOf(period + 1L), 12, RoundingMode.HALF_UP);
        List<BigDecimal> result = new ArrayList<>(values.size());
        BigDecimal current = values.getFirst();
        result.add(current);
        for (int index = 1; index < values.size(); index++) {
            current = values.get(index).subtract(current).multiply(multiplier).add(current);
            result.add(current);
        }
        return result;
    }

    private Signal movingAverageSignal(BigDecimal latest, BigDecimal ma5, BigDecimal ma20) {
        if (latest.compareTo(ma20) > 0 && ma5.compareTo(ma20) > 0) {
            return Signal.BUY;
        }
        if (latest.compareTo(ma20) < 0 && ma5.compareTo(ma20) < 0) {
            return Signal.SELL;
        }
        return Signal.NEUTRAL;
    }

    private int signalScore(Signal signal) {
        return switch (signal) {
            case BUY -> 1;
            case SELL -> -1;
            case NEUTRAL -> 0;
        };
    }

    public enum Signal {
        BUY, NEUTRAL, SELL
    }

    public record MacdValue(BigDecimal value, BigDecimal signalLine, BigDecimal histogram) {
    }

    public record Result(
            BigDecimal ma5,
            BigDecimal ma20,
            BigDecimal ma60,
            Signal movingAverageSignal,
            BigDecimal rsi,
            Signal rsiSignal,
            MacdValue macd,
            Signal macdSignal,
            Signal summarySignal) {
    }
}
