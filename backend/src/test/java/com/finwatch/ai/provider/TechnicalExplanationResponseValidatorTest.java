package com.finwatch.ai.provider;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.finwatch.ai.dto.TechnicalExplanationInput;
import com.finwatch.ai.dto.TechnicalExplanationInput.TechnicalEvidence;
import com.finwatch.ai.provider.AiProvider.TechnicalExplanationResult;
import com.finwatch.ai.provider.AiProvider.TechnicalSignalExplanation;

class TechnicalExplanationResponseValidatorTest {

    private final TechnicalExplanationResponseValidator validator = new TechnicalExplanationResponseValidator();

    @Test
    void rejectsUnknownEvidenceId() {
        TechnicalExplanationResult result = validResult(
                List.of(new TechnicalSignalExplanation("근거 설명", List.of("I99"))),
                List.of("DEMO 데이터입니다."));

        assertThatThrownBy(() -> validator.validate(result, input()))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("근거 ID");
    }

    @Test
    void rejectsDemoResponseThatHidesDataLimitation() {
        TechnicalExplanationResult result = validResult(
                List.of(new TechnicalSignalExplanation("근거 설명", List.of("I1"))),
                List.of());

        assertThatThrownBy(() -> validator.validate(result, input()))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("DEMO");
    }

    @Test
    void rejectsDirectTradingInstruction() {
        TechnicalExplanationResult original = validResult(
                List.of(new TechnicalSignalExplanation("근거 설명", List.of("I1"))),
                List.of("DEMO 데이터입니다."));
        TechnicalExplanationResult result = new TechnicalExplanationResult(
                original.modelName(),
                "이 신호에서는 매수하세요.",
                original.trendExplanation(),
                original.momentumExplanation(),
                original.volatilityExplanation(),
                original.volumeExplanation(),
                original.supportingSignals(),
                original.conflictingSignals(),
                original.riskNotes(),
                original.dataLimitations(),
                original.inputTokens(),
                original.outputTokens());

        assertThatThrownBy(() -> validator.validate(result, input()))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("투자 권유");
    }

    @Test
    void rejectsNumberThatCannotBeTracedToEvidence() {
        TechnicalExplanationResult original = validResult(
                List.of(new TechnicalSignalExplanation("근거 설명", List.of("I1"))),
                List.of("DEMO 데이터입니다."));
        TechnicalExplanationResult result = new TechnicalExplanationResult(
                original.modelName(),
                "입력에 없는 9999 값을 임의로 추가했습니다.",
                original.trendExplanation(),
                original.momentumExplanation(),
                original.volatilityExplanation(),
                original.volumeExplanation(),
                original.supportingSignals(),
                original.conflictingSignals(),
                original.riskNotes(),
                original.dataLimitations(),
                original.inputTokens(),
                original.outputTokens());

        assertThatThrownBy(() -> validator.validate(result, input()))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("추적할 수 없는 숫자");
    }

    @Test
    void acceptsNumbersCopiedFromSymbolAndSnapshotTimestamp() {
        TechnicalExplanationResult original = validResult(
                List.of(new TechnicalSignalExplanation("근거 설명", List.of("I1"))),
                List.of("DEMO 데이터입니다."));
        TechnicalExplanationResult result = new TechnicalExplanationResult(
                original.modelName(),
                "000660 종목의 2026-07-14 기준 기술지표 설명입니다.",
                original.trendExplanation(),
                original.momentumExplanation(),
                original.volatilityExplanation(),
                original.volumeExplanation(),
                original.supportingSignals(),
                original.conflictingSignals(),
                original.riskNotes(),
                original.dataLimitations(),
                original.inputTokens(),
                original.outputTokens());

        validator.validate(result, input());
    }

    @Test
    void acceptsMacdPeriodsWhenTheyArePartOfServerEvidence() {
        TechnicalExplanationResult original = validResult(
                List.of(new TechnicalSignalExplanation("근거 설명", List.of("I3"))),
                List.of("DEMO 데이터입니다."));
        TechnicalExplanationResult result = new TechnicalExplanationResult(
                original.modelName(),
                "MACD 12·26·9 설정의 서버 계산 결과를 설명합니다.",
                original.trendExplanation(),
                original.momentumExplanation(),
                original.volatilityExplanation(),
                original.volumeExplanation(),
                original.supportingSignals(),
                original.conflictingSignals(),
                original.riskNotes(),
                original.dataLimitations(),
                original.inputTokens(),
                original.outputTokens());

        validator.validate(result, input());
    }

    private TechnicalExplanationResult validResult(
            List<TechnicalSignalExplanation> supporting,
            List<String> limitations) {
        return new TechnicalExplanationResult(
                "fixture-model",
                "기술지표를 근거로 정리한 설명입니다.",
                "추세 설명입니다.",
                "모멘텀 설명입니다.",
                "변동성 설명입니다.",
                "거래량 설명입니다.",
                supporting,
                List.of(),
                List.of("기술지표는 미래 가격을 예측하지 않습니다."),
                limitations,
                100,
                30);
    }

    private TechnicalExplanationInput input() {
        return new TechnicalExplanationInput(
                "KRX", "000660", "KRW", "1D", Instant.parse("2026-07-14T06:00:00Z"),
                "DEMO", "DEMO", false, "technical-v2-wilder", 90, "BUY",
                List.of(
                        new TechnicalEvidence(
                                "I1", "MOVING_AVERAGE", Map.of("ma5", "270100"), "MA5 270100"),
                        new TechnicalEvidence(
                                "I3", "MACD",
                                Map.of("fastPeriod", "12", "slowPeriod", "26", "signalPeriod", "9"),
                                "MACD 12·26·9")));
    }
}
