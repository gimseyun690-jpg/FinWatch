package com.finwatch.technical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.finwatch.technical.TechnicalAnalysisCalculator.Signal;
import com.finwatch.technical.TechnicalAnalysisCalculator.Candle;

class TechnicalAnalysisCalculatorTest {

    private final TechnicalAnalysisCalculator calculator = new TechnicalAnalysisCalculator();

    @Test
    void longHistoryKeepsMacdPrecisionWithoutUnboundedDecimalGrowth() {
        List<BigDecimal> closes = IntStream.range(0, 10_000)
                .mapToObj(day -> BigDecimal.valueOf(100_000 + day * 17L + day % 19 * 31L))
                .toList();
        double fast = closes.getFirst().doubleValue();
        double slow = fast;
        double signal = 0;
        double macd = 0;
        for (int index = 1; index < closes.size(); index++) {
            double close = closes.get(index).doubleValue();
            fast += (close - fast) * 2 / 13;
            slow += (close - slow) * 2 / 27;
            macd = fast - slow;
            signal += (macd - signal) * 2 / 10;
        }

        TechnicalAnalysisCalculator.Result result = calculator.calculate(closes);

        assertThat(result.macd().value().doubleValue()).isCloseTo(macd, within(0.0001));
        assertThat(result.macd().signalLine().doubleValue()).isCloseTo(signal, within(0.0001));
        assertThat(result.macd().histogram().doubleValue()).isCloseTo(macd - signal, within(0.0001));
        assertThat(result.crossoverEvents()).hasSizeLessThanOrEqualTo(20);
    }

    @Test
    void risingPricesProduceBuyTrendAndHighRsi() {
        List<BigDecimal> closes = IntStream.rangeClosed(1, 90)
                .mapToObj(day -> BigDecimal.valueOf(100 + day))
                .toList();

        TechnicalAnalysisCalculator.Result result = calculator.calculate(closes);

        assertThat(result.movingAverageSignal()).isEqualTo(Signal.BUY);
        assertThat(result.rsi()).isEqualByComparingTo("100.0000");
        assertThat(result.rsiSignal()).isEqualTo(Signal.SELL);
        assertThat(result.macdSignal()).isEqualTo(Signal.BUY);
        assertThat(result.summarySignal()).isEqualTo(Signal.BUY);
    }

    @Test
    void flatPricesProduceNeutralResult() {
        List<BigDecimal> closes = IntStream.range(0, 60)
                .mapToObj(index -> BigDecimal.valueOf(100))
                .toList();

        TechnicalAnalysisCalculator.Result result = calculator.calculate(closes);

        assertThat(result.movingAverageSignal()).isEqualTo(Signal.NEUTRAL);
        assertThat(result.macdSignal()).isEqualTo(Signal.NEUTRAL);
        assertThat(result.summarySignal()).isEqualTo(Signal.NEUTRAL);
    }

    @Test
    void padsShortHistoryButRejectsEmptyHistory() {
        List<BigDecimal> closes = IntStream.range(0, 59)
                .mapToObj(BigDecimal::valueOf)
                .toList();

        assertThat(calculator.calculate(closes).ma60()).isNotNull();
        assertThatThrownBy(() -> calculator.calculate(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("최소 1개");
    }

    @Test
    void matchesIndependentWilderRsiReferenceDataset() {
        List<BigDecimal> closes = List.of(
                "54.8", "56.8", "57.85", "59.85", "60.57", "61.1", "62.17", "60.6",
                "62.35", "62.15", "62.35", "61.45", "62.8", "61.37", "62.5", "62.57",
                "60.8", "59.37", "60.35", "62.35", "62.17", "62.55", "64.55", "64.37",
                "65.3", "64.42", "62.9", "61.6", "62.05", "60.05", "59.7", "60.9",
                "60.25", "58.27", "58.7", "57.72", "58.1", "58.2")
                .stream()
                .map(BigDecimal::new)
                .toList();

        assertThat(calculator.relativeStrengthIndex(closes, 14)).isEqualByComparingTo("44.5247");
    }

    @Test
    void constantRangeDatasetHasStableBollingerAtrAndVolumeAverage() {
        List<Candle> candles = IntStream.range(0, 60)
                .mapToObj(index -> new Candle(
                        BigDecimal.valueOf(100),
                        BigDecimal.valueOf(102),
                        BigDecimal.valueOf(98),
                        BigDecimal.valueOf(100),
                        BigDecimal.valueOf(1_000)))
                .toList();

        TechnicalAnalysisCalculator.Result result = calculator.calculateMarket(candles);

        assertThat(result.bollingerBands().upper()).isEqualByComparingTo("100.0000");
        assertThat(result.bollingerBands().middle()).isEqualByComparingTo("100.0000");
        assertThat(result.bollingerBands().lower()).isEqualByComparingTo("100.0000");
        assertThat(result.atr()).isEqualByComparingTo("4.0000");
        assertThat(result.volumeMa20()).isEqualByComparingTo("1000.0000");
        assertThat(result.rsi()).isEqualByComparingTo("50.0000");
    }

    @Test
    void identifiesMovingAverageCrossoverInReversalDataset() {
        List<Candle> candles = IntStream.range(0, 60)
                .mapToObj(index -> {
                    long close = index < 30 ? 130 - index : 100 + (index - 30) * 3L;
                    BigDecimal value = BigDecimal.valueOf(close);
                    return new Candle(value, value.add(BigDecimal.ONE), value.subtract(BigDecimal.ONE), value, BigDecimal.TEN);
                })
                .toList();

        TechnicalAnalysisCalculator.Result result = calculator.calculateMarket(candles);

        assertThat(result.crossoverEvents())
                .anySatisfy(event -> {
                    assertThat(event.type()).isEqualTo("MA_GOLDEN_CROSS");
                    assertThat(event.signal()).isEqualTo(Signal.BUY);
                });
    }
}
