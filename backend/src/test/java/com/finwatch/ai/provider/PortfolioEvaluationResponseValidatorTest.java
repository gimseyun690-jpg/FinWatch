package com.finwatch.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.finwatch.ai.dto.PortfolioEvaluationInput;
import com.finwatch.ai.dto.PortfolioEvaluationInput.CurrencyExposure;
import com.finwatch.ai.dto.PortfolioEvaluationInput.Evidence;
import com.finwatch.ai.provider.AiProvider.PortfolioEvaluationResult;
import com.finwatch.ai.provider.AiProvider.PortfolioEvaluationStatement;

class PortfolioEvaluationResponseValidatorTest {

    private final PortfolioEvaluationResponseValidator validator =
            new PortfolioEvaluationResponseValidator();

    @Test
    void acceptsServerEvidenceOnlyAssessment() {
        PortfolioEvaluationResult result = validResult();

        assertThat(validator.validate(result, input())).isSameAs(result);
    }

    @Test
    void rejectsUnknownEvidenceId() {
        PortfolioEvaluationResult original = validResult();
        PortfolioEvaluationResult invalid = new PortfolioEvaluationResult(
                original.modelName(),
                original.headline(),
                original.summary(),
                new PortfolioEvaluationStatement("분산 근거를 확인했습니다.", List.of("UNKNOWN")),
                original.concentration(),
                original.currencyExposure(),
                original.performanceContext(),
                original.strengths(),
                original.riskFactors(),
                original.reviewPoints(),
                original.dataLimitations(),
                original.inputTokens(),
                original.outputTokens());

        assertThatThrownBy(() -> validator.validate(invalid, input()))
                .isInstanceOfSatisfying(AiProviderException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("AI_RESPONSE_INVALID"));
    }

    @Test
    void rejectsDirectTradingAdvice() {
        PortfolioEvaluationResult original = validResult();
        PortfolioEvaluationResult invalid = new PortfolioEvaluationResult(
                original.modelName(),
                "이 종목은 매수하세요.",
                original.summary(),
                original.diversification(),
                original.concentration(),
                original.currencyExposure(),
                original.performanceContext(),
                original.strengths(),
                original.riskFactors(),
                original.reviewPoints(),
                original.dataLimitations(),
                original.inputTokens(),
                original.outputTokens());

        assertThatThrownBy(() -> validator.validate(invalid, input()))
                .isInstanceOfSatisfying(AiProviderException.class, exception ->
                        assertThat(exception.getMessage()).contains("거래 권고"));
    }

    @Test
    void rejectsNumberThatIsNotPresentInTheCitedEvidence() {
        PortfolioEvaluationResult original = validResult();
        PortfolioEvaluationResult invalid = withDiversification(
                original,
                new PortfolioEvaluationStatement(
                        "최대 보유 종목 비중은 50%입니다.",
                        List.of("C1")));

        assertThatThrownBy(() -> validator.validate(invalid, input()))
                .isInstanceOfSatisfying(AiProviderException.class, exception ->
                        assertThat(exception.getMessage()).contains("추적할 수 없는 숫자"));
    }

    @Test
    void rejectsNumberThatExistsOnlyInDifferentEvidence() {
        PortfolioEvaluationResult original = validResult();
        PortfolioEvaluationResult invalid = withDiversification(
                original,
                new PortfolioEvaluationStatement(
                        "집중도 근거의 손익은 10입니다.",
                        List.of("C1")));

        assertThatThrownBy(() -> validator.validate(invalid, input()))
                .isInstanceOfSatisfying(AiProviderException.class, exception ->
                        assertThat(exception.getMessage()).contains("추적할 수 없는 숫자"));
    }

    @Test
    void rejectsNuancedKoreanAndEnglishTradingAdvice() {
        for (String advice : List.of(
                "특정 종목 비중을 줄이는 편이 좋습니다.",
                "새 종목 편입을 고려하세요.",
                "This position should remain overweight.",
                "The investor should hold this stock.")) {
            PortfolioEvaluationResult original = validResult();
            PortfolioEvaluationResult invalid = new PortfolioEvaluationResult(
                    original.modelName(),
                    advice,
                    original.summary(),
                    original.diversification(),
                    original.concentration(),
                    original.currencyExposure(),
                    original.performanceContext(),
                    original.strengths(),
                    original.riskFactors(),
                    original.reviewPoints(),
                    original.dataLimitations(),
                    original.inputTokens(),
                    original.outputTokens());

            assertThatThrownBy(() -> validator.validate(invalid, input()))
                    .as("advice must be rejected: %s", advice)
                    .isInstanceOfSatisfying(AiProviderException.class, exception ->
                            assertThat(exception.getMessage()).contains("거래 권고"));
        }
    }

    @Test
    void acceptsNonAdviceDisclaimer() {
        PortfolioEvaluationResult original = validResult();
        PortfolioEvaluationResult disclaimer = new PortfolioEvaluationResult(
                original.modelName(),
                original.headline(),
                "매수·매도 권유가 아닌 포트폴리오 구성 점검 결과입니다.",
                original.diversification(),
                original.concentration(),
                original.currencyExposure(),
                original.performanceContext(),
                original.strengths(),
                original.riskFactors(),
                original.reviewPoints(),
                original.dataLimitations(),
                original.inputTokens(),
                original.outputTokens());

        assertThat(validator.validate(disclaimer, input())).isSameAs(disclaimer);
    }

    private PortfolioEvaluationResult validResult() {
        PortfolioEvaluationStatement portfolio = new PortfolioEvaluationStatement(
                "현재 평가액과 손익을 함께 확인했습니다.",
                List.of("P1"));
        PortfolioEvaluationStatement concentration = new PortfolioEvaluationStatement(
                "최대 비중은 60%이며 상위 3개 구성을 함께 확인했습니다.",
                List.of("C1"));
        PortfolioEvaluationStatement currency = new PortfolioEvaluationStatement(
                "KRW 비중은 100%입니다.",
                List.of("FX1"));
        return new PortfolioEvaluationResult(
                "gemini-test",
                "보유 구성의 집중도와 통화 노출을 확인했습니다.",
                "서버 계산 근거만 사용한 포트폴리오 설명입니다.",
                concentration,
                concentration,
                currency,
                portfolio,
                List.of(portfolio),
                List.of(concentration),
                List.of(portfolio),
                List.of(),
                100,
                30);
    }

    private PortfolioEvaluationInput input() {
        return new PortfolioEvaluationInput(
                "KRW",
                Instant.parse("2026-07-30T05:30:00Z"),
                Instant.parse("2026-07-30T05:30:00Z"),
                3,
                3,
                true,
                true,
                new BigDecimal("100"),
                new BigDecimal("90"),
                new BigDecimal("10"),
                new BigDecimal("11.1111"),
                new BigDecimal("60"),
                new BigDecimal("100"),
                new BigDecimal("5200"),
                "HIGH_CONCENTRATION",
                List.of(new CurrencyExposure("KRW", new BigDecimal("100"), new BigDecimal("100"), 3)),
                List.of(),
                List.of(
                        new Evidence("P1", "PORTFOLIO_TOTAL",
                                Map.of("evaluationAmount", "100", "profitLoss", "10"),
                                "평가액 100 · 손익 10"),
                        new Evidence("C1", "CONCENTRATION",
                                Map.of("holdingCount", "3", "topPositionWeight", "60",
                                        "topThreeWeight", "100", "hhi", "5200"),
                                "보유 3개 · 최대 종목 60% · 상위 3종목 100% · HHI 5200"),
                        new Evidence("FX1", "CURRENCY_EXPOSURE",
                                Map.of("KRWWeight", "100"),
                                "KRW 100%")),
                List.of());
    }

    private PortfolioEvaluationResult withDiversification(
            PortfolioEvaluationResult original,
            PortfolioEvaluationStatement diversification) {
        return new PortfolioEvaluationResult(
                original.modelName(),
                original.headline(),
                original.summary(),
                diversification,
                original.concentration(),
                original.currencyExposure(),
                original.performanceContext(),
                original.strengths(),
                original.riskFactors(),
                original.reviewPoints(),
                original.dataLimitations(),
                original.inputTokens(),
                original.outputTokens());
    }
}
