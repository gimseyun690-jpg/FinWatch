package com.finwatch.technical;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class TechnicalAnalysisCalculator {

    private static final int SCALE = 4;
    public static final int MOVING_AVERAGE_SHORT_PERIOD = 5;
    public static final int MOVING_AVERAGE_MEDIUM_PERIOD = 20;
    public static final int MOVING_AVERAGE_LONG_PERIOD = 60;
    public static final int RSI_PERIOD = 14;
    public static final int ATR_PERIOD = 14;
    public static final int BOLLINGER_PERIOD = 20;
    public static final int BOLLINGER_DEVIATION_MULTIPLIER = 2;
    public static final int MACD_FAST_PERIOD = 12;
    public static final int MACD_SLOW_PERIOD = 26;
    public static final int MACD_SIGNAL_PERIOD = 9;
    public static final int VOLUME_MOVING_AVERAGE_PERIOD = 20;

    public Result calculate(List<BigDecimal> closes) {
        return calculateMarket(closes.stream()
                .map(close -> new Candle(close, close, close, close, BigDecimal.ZERO))
                .toList());
    }

    public Result calculateMarket(List<Candle> candles) {
        if (candles == null || candles.isEmpty()) {
            throw new IllegalArgumentException("기술적 분석에는 최소 1개의 가격 데이터가 필요합니다.");
        }

        List<Candle> paddedCandles = candles;
        if (candles.size() < 60) {
            paddedCandles = new ArrayList<>(60);
            Candle first = candles.getFirst();
            int needed = 60 - candles.size();
            for (int i = 0; i < needed; i++) {
                paddedCandles.add(first);
            }
            paddedCandles.addAll(candles);
        }

        List<BigDecimal> closes = paddedCandles.stream().map(Candle::close).toList();
        List<BigDecimal> volumes = paddedCandles.stream().map(Candle::volume).toList();
        BigDecimal latest = closes.getLast();
        BigDecimal ma5 = simpleMovingAverage(closes, MOVING_AVERAGE_SHORT_PERIOD);
        BigDecimal ma20 = simpleMovingAverage(closes, MOVING_AVERAGE_MEDIUM_PERIOD);
        BigDecimal ma60 = simpleMovingAverage(closes, MOVING_AVERAGE_LONG_PERIOD);
        BigDecimal rsi = relativeStrengthIndex(closes, RSI_PERIOD);
        MacdComputation macd = macdComputation(closes);
        BollingerBands bollingerBands = bollingerBands(
                closes,
                BOLLINGER_PERIOD,
                BOLLINGER_DEVIATION_MULTIPLIER);
        BigDecimal atr = averageTrueRange(candles, ATR_PERIOD);
        BigDecimal volumeMa20 = simpleMovingAverage(volumes, VOLUME_MOVING_AVERAGE_PERIOD);

        Signal movingAverageSignal = movingAverageSignal(latest, ma5, ma20);
        Signal rsiSignal = rsi.compareTo(BigDecimal.valueOf(70)) >= 0
                ? Signal.SELL
                : rsi.compareTo(BigDecimal.valueOf(30)) <= 0 ? Signal.BUY : Signal.NEUTRAL;
        Signal macdSignal = macd.value().histogram().signum() > 0
                ? Signal.BUY
                : macd.value().histogram().signum() < 0 ? Signal.SELL : Signal.NEUTRAL;

        int score = signalScore(movingAverageSignal) + signalScore(rsiSignal) + signalScore(macdSignal);
        Signal summarySignal = score > 0 ? Signal.BUY : score < 0 ? Signal.SELL : Signal.NEUTRAL;

        BigDecimal atrPercent = latest.signum() == 0
                ? BigDecimal.ZERO.setScale(SCALE)
                : atr.multiply(BigDecimal.valueOf(100)).divide(latest, SCALE, RoundingMode.HALF_UP);
        BigDecimal bandwidthPercent = bollingerBands.middle().signum() == 0
                ? BigDecimal.ZERO.setScale(SCALE)
                : bollingerBands.upper().subtract(bollingerBands.lower())
                        .multiply(BigDecimal.valueOf(100))
                        .divide(bollingerBands.middle(), SCALE, RoundingMode.HALF_UP);

        return new Result(
                ma5,
                ma20,
                ma60,
                movingAverageSignal,
                rsi,
                rsiSignal,
                macd.value(),
                macdSignal,
                bollingerBands,
                bandwidthPercent,
                atr,
                atrPercent,
                volumeMa20,
                crossoverEvents(closes, macd.histogramSeries(), relativeStrengthIndexSeries(closes, RSI_PERIOD)),
                summarySignal);
    }

    public BigDecimal simpleMovingAverage(List<BigDecimal> values, int period) {
        if (values.size() < period) {
            throw new IllegalArgumentException("이동평균 계산 데이터가 부족합니다.");
        }
        return average(values.subList(values.size() - period, values.size()));
    }

    public BigDecimal relativeStrengthIndex(List<BigDecimal> values, int period) {
        if (values.size() <= period) {
            throw new IllegalArgumentException("RSI 계산 데이터가 부족합니다.");
        }

        return relativeStrengthIndexSeries(values, period)[values.size() - 1];
    }

    private BigDecimal[] relativeStrengthIndexSeries(List<BigDecimal> values, int period) {
        BigDecimal[] result = new BigDecimal[values.size()];
        BigDecimal averageGain = BigDecimal.ZERO;
        BigDecimal averageLoss = BigDecimal.ZERO;
        for (int index = 1; index <= period; index++) {
            BigDecimal delta = values.get(index).subtract(values.get(index - 1));
            averageGain = averageGain.add(delta.max(BigDecimal.ZERO));
            averageLoss = averageLoss.add(delta.min(BigDecimal.ZERO).abs());
        }
        averageGain = divide(averageGain, period);
        averageLoss = divide(averageLoss, period);
        result[period] = rsiFromAverages(averageGain, averageLoss);

        for (int index = period + 1; index < values.size(); index++) {
            BigDecimal delta = values.get(index).subtract(values.get(index - 1));
            BigDecimal gain = delta.max(BigDecimal.ZERO);
            BigDecimal loss = delta.min(BigDecimal.ZERO).abs();
            averageGain = divide(averageGain.multiply(BigDecimal.valueOf(period - 1L)).add(gain), period);
            averageLoss = divide(averageLoss.multiply(BigDecimal.valueOf(period - 1L)).add(loss), period);
            result[index] = rsiFromAverages(averageGain, averageLoss);
        }
        return result;
    }

    public MacdValue macd(List<BigDecimal> values) {
        return macdComputation(values).value();
    }

    public BollingerBands bollingerBands(List<BigDecimal> values, int period, int deviationMultiplier) {
        if (values.size() < period) {
            throw new IllegalArgumentException("볼린저 밴드 계산 데이터가 부족합니다.");
        }
        List<BigDecimal> window = values.subList(values.size() - period, values.size());
        BigDecimal middle = average(window);
        BigDecimal variance = window.stream()
                .map(value -> value.subtract(middle).pow(2))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(period), 12, RoundingMode.HALF_UP);
        BigDecimal standardDeviation = BigDecimal.valueOf(Math.sqrt(variance.doubleValue()));
        BigDecimal width = standardDeviation.multiply(BigDecimal.valueOf(deviationMultiplier));
        return new BollingerBands(
                middle.add(width).setScale(SCALE, RoundingMode.HALF_UP),
                middle.setScale(SCALE, RoundingMode.HALF_UP),
                middle.subtract(width).setScale(SCALE, RoundingMode.HALF_UP),
                period,
                deviationMultiplier);
    }

    public BigDecimal averageTrueRange(List<Candle> candles, int period) {
        if (candles.size() <= period) {
            throw new IllegalArgumentException("ATR 계산 데이터가 부족합니다.");
        }
        List<BigDecimal> trueRanges = new ArrayList<>(candles.size() - 1);
        for (int index = 1; index < candles.size(); index++) {
            Candle current = candles.get(index);
            BigDecimal previousClose = candles.get(index - 1).close();
            BigDecimal highLow = current.high().subtract(current.low()).abs();
            BigDecimal highPreviousClose = current.high().subtract(previousClose).abs();
            BigDecimal lowPreviousClose = current.low().subtract(previousClose).abs();
            trueRanges.add(highLow.max(highPreviousClose).max(lowPreviousClose));
        }
        BigDecimal atr = average(trueRanges.subList(0, period));
        for (int index = period; index < trueRanges.size(); index++) {
            atr = divide(atr.multiply(BigDecimal.valueOf(period - 1L)).add(trueRanges.get(index)), period);
        }
        return atr.setScale(SCALE, RoundingMode.HALF_UP);
    }

    private MacdComputation macdComputation(List<BigDecimal> values) {
        List<BigDecimal> fast = emaSeries(values, MACD_FAST_PERIOD);
        List<BigDecimal> slow = emaSeries(values, MACD_SLOW_PERIOD);
        List<BigDecimal> macdSeries = new ArrayList<>(values.size());
        for (int index = 0; index < values.size(); index++) {
            macdSeries.add(fast.get(index).subtract(slow.get(index)));
        }
        List<BigDecimal> signalSeries = emaSeries(macdSeries, MACD_SIGNAL_PERIOD);
        List<BigDecimal> histogramSeries = new ArrayList<>(values.size());
        for (int index = 0; index < values.size(); index++) {
            histogramSeries.add(macdSeries.get(index).subtract(signalSeries.get(index)));
        }
        BigDecimal value = macdSeries.getLast();
        BigDecimal signalLine = signalSeries.getLast();
        return new MacdComputation(
                new MacdValue(
                        value.setScale(SCALE, RoundingMode.HALF_UP),
                        signalLine.setScale(SCALE, RoundingMode.HALF_UP),
                        histogramSeries.getLast().setScale(SCALE, RoundingMode.HALF_UP)),
                histogramSeries);
    }

    private List<CrossoverEvent> crossoverEvents(
            List<BigDecimal> closes,
            List<BigDecimal> macdHistogram,
            BigDecimal[] rsiSeries) {
        List<CrossoverEvent> events = new ArrayList<>();
        for (int index = 20; index < closes.size(); index++) {
            BigDecimal previousMaDifference = movingAverageAt(closes, index - 1, 5)
                    .subtract(movingAverageAt(closes, index - 1, 20));
            BigDecimal currentMaDifference = movingAverageAt(closes, index, 5)
                    .subtract(movingAverageAt(closes, index, 20));
            if (previousMaDifference.signum() <= 0 && currentMaDifference.signum() > 0) {
                events.add(new CrossoverEvent(index, "MA_GOLDEN_CROSS", Signal.BUY));
            } else if (previousMaDifference.signum() >= 0 && currentMaDifference.signum() < 0) {
                events.add(new CrossoverEvent(index, "MA_DEAD_CROSS", Signal.SELL));
            }

            BigDecimal previousHistogram = macdHistogram.get(index - 1);
            BigDecimal currentHistogram = macdHistogram.get(index);
            if (previousHistogram.signum() <= 0 && currentHistogram.signum() > 0) {
                events.add(new CrossoverEvent(index, "MACD_BULLISH_CROSS", Signal.BUY));
            } else if (previousHistogram.signum() >= 0 && currentHistogram.signum() < 0) {
                events.add(new CrossoverEvent(index, "MACD_BEARISH_CROSS", Signal.SELL));
            }

            if (rsiSeries[index - 1] != null && rsiSeries[index] != null) {
                BigDecimal previousRsi = rsiSeries[index - 1];
                BigDecimal currentRsi = rsiSeries[index];
                if (previousRsi.compareTo(BigDecimal.valueOf(30)) > 0
                        && currentRsi.compareTo(BigDecimal.valueOf(30)) <= 0) {
                    events.add(new CrossoverEvent(index, "RSI_OVERSOLD_ENTER", Signal.NEUTRAL));
                } else if (previousRsi.compareTo(BigDecimal.valueOf(30)) <= 0
                        && currentRsi.compareTo(BigDecimal.valueOf(30)) > 0) {
                    events.add(new CrossoverEvent(index, "RSI_OVERSOLD_EXIT", Signal.NEUTRAL));
                }
                if (previousRsi.compareTo(BigDecimal.valueOf(70)) < 0
                        && currentRsi.compareTo(BigDecimal.valueOf(70)) >= 0) {
                    events.add(new CrossoverEvent(index, "RSI_OVERBOUGHT_ENTER", Signal.NEUTRAL));
                } else if (previousRsi.compareTo(BigDecimal.valueOf(70)) >= 0
                        && currentRsi.compareTo(BigDecimal.valueOf(70)) < 0) {
                    events.add(new CrossoverEvent(index, "RSI_OVERBOUGHT_EXIT", Signal.NEUTRAL));
                }
            }
        }
        return events.size() <= 20 ? List.copyOf(events) : List.copyOf(events.subList(events.size() - 20, events.size()));
    }

    private BigDecimal movingAverageAt(List<BigDecimal> values, int endIndex, int period) {
        return average(values.subList(endIndex - period + 1, endIndex + 1));
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

    private BigDecimal rsiFromAverages(BigDecimal averageGain, BigDecimal averageLoss) {
        if (averageGain.signum() == 0 && averageLoss.signum() == 0) {
            return BigDecimal.valueOf(50).setScale(SCALE);
        }
        if (averageLoss.signum() == 0) {
            return BigDecimal.valueOf(100).setScale(SCALE);
        }
        if (averageGain.signum() == 0) {
            return BigDecimal.ZERO.setScale(SCALE);
        }
        BigDecimal relativeStrength = averageGain.divide(averageLoss, 12, RoundingMode.HALF_UP);
        return BigDecimal.valueOf(100)
                .subtract(BigDecimal.valueOf(100)
                        .divide(BigDecimal.ONE.add(relativeStrength), 12, RoundingMode.HALF_UP))
                .setScale(SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal average(List<BigDecimal> values) {
        return values.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal divide(BigDecimal value, int divisor) {
        return value.divide(BigDecimal.valueOf(divisor), 12, RoundingMode.HALF_UP);
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

    public record Candle(
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close,
            BigDecimal volume) {
    }

    public record MacdValue(BigDecimal value, BigDecimal signalLine, BigDecimal histogram) {
    }

    public record BollingerBands(
            BigDecimal upper,
            BigDecimal middle,
            BigDecimal lower,
            int period,
            int deviationMultiplier) {
    }

    public record CrossoverEvent(int index, String type, Signal signal) {
    }

    private record MacdComputation(MacdValue value, List<BigDecimal> histogramSeries) {
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
            BollingerBands bollingerBands,
            BigDecimal bollingerBandwidthPercent,
            BigDecimal atr,
            BigDecimal atrPercent,
            BigDecimal volumeMa20,
            List<CrossoverEvent> crossoverEvents,
            Signal summarySignal) {
    }
}
