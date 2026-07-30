package com.finwatch.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.finwatch.ai.dto.TechnicalExplanationInput;
import com.finwatch.ai.dto.TechnicalExplanationInput.TechnicalEvidence;

class MockAiProviderTechnicalExplanationTest {

    private final MockAiProvider provider = new MockAiProvider();
    private final TechnicalExplanationResponseValidator validator =
            new TechnicalExplanationResponseValidator();

    @Test
    void preservesExactEvidenceNumbersInTechnicalExplanation() {
        TechnicalExplanationInput input = new TechnicalExplanationInput(
                "KRX",
                "000660",
                "KRW",
                "1D",
                Instant.parse("2026-07-30T06:30:00Z"),
                "DEMO",
                "DEMO",
                false,
                "technical-v2-wilder",
                90,
                "SELL",
                List.of(
                        evidence("I1", "MOVING_AVERAGE", Map.of(
                                "shortPeriod", "5",
                                "mediumPeriod", "20",
                                "longPeriod", "60",
                                "price", "2585592.49",
                                "ma5", "2579001.125",
                                "ma20", "2600123.875",
                                "ma60", "2499999.625")),
                        evidence("I2", "RSI", Map.of(
                                "period", "14",
                                "value", "98.9209",
                                "lowerBoundary", "30",
                                "upperBoundary", "70")),
                        evidence("I3", "MACD", Map.of(
                                "fastPeriod", "12",
                                "slowPeriod", "26",
                                "signalPeriod", "9",
                                "value", "-1234.5678",
                                "signal", "-1200.4321",
                                "histogram", "-34.1357")),
                        evidence("I5", "ATR", Map.of(
                                "period", "14",
                                "value", "45678.9123",
                                "percent", "1.766708")),
                        evidence("I6", "VOLUME", Map.of(
                                "movingAveragePeriod", "20",
                                "current", "12899876",
                                "ma20", "11123456.789",
                                "ratio", "1.1597"))));

        var result = provider.explainTechnical(input, "technical-explanation-v1");

        assertThat(result.trendExplanation())
                .contains("2585592.49", "2579001.125", "2600123.875", "2499999.625");
        assertThat(result.momentumExplanation())
                .contains("98.9209", "-1234.5678", "-1200.4321", "-34.1357");
        assertThat(result.volatilityExplanation()).contains("45678.9123", "1.766708");
        assertThat(result.volumeExplanation()).contains("12899876", "11123456.789", "1.1597");
        assertThat(validator.validate(result, input)).isSameAs(result);
    }

    private TechnicalEvidence evidence(
            String id,
            String indicator,
            Map<String, String> values) {
        return new TechnicalEvidence(id, indicator, values, values.toString());
    }
}
