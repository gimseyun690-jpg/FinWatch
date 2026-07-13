package com.finwatch.technical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.finwatch.technical.TechnicalAnalysisCalculator.Signal;

class TechnicalAnalysisCalculatorTest {

    private final TechnicalAnalysisCalculator calculator = new TechnicalAnalysisCalculator();

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
    void rejectsInsufficientHistory() {
        List<BigDecimal> closes = IntStream.range(0, 59)
                .mapToObj(BigDecimal::valueOf)
                .toList();

        assertThatThrownBy(() -> calculator.calculate(closes))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("최소 60개");
    }
}
