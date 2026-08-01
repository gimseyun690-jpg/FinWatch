package com.finwatch.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.finwatch.ai.provider.PortfolioEvaluationResponseValidator;
import com.finwatch.ai.service.PortfolioEvaluationResultResolver.ResolvedPortfolioEvaluation;

class PortfolioEvaluationResultResolverTest {

    private final PortfolioEvaluationResultResolver resolver =
            new PortfolioEvaluationResultResolver(new PortfolioEvaluationResponseValidator());

    @Test
    void keepsAValidProviderAssessment() {
        PortfolioEvaluationResult valid = providerResult("상위 보유 종목의 집중도를 확인했습니다.");

        ResolvedPortfolioEvaluation resolved = resolver.resolve(valid, input(), "GeminiAiProvider");

        assertThat(resolved.result()).isSameAs(valid);
        assertThat(resolved.fallbackUsed()).isFalse();
    }

    @Test
    void replacesAnUntraceableProviderNumberWithServerGroundedAssessment() {
        PortfolioEvaluationResult invalid = providerResult(
                "상위 1개 종목이 전체 평가액에 큰 영향을 줍니다.");

        ResolvedPortfolioEvaluation resolution = resolver.resolve(
                invalid,
                input(),
                "GeminiAiProvider");
        PortfolioEvaluationResult resolved = resolution.result();

        assertThat(resolution.fallbackUsed()).isTrue();
        assertThat(resolved.modelName()).isEqualTo("gemini-test+server-grounded-fallback");
        assertThat(resolved.inputTokens()).isEqualTo(160);
        assertThat(resolved.outputTokens()).isEqualTo(70);
        assertThat(resolved.dataLimitations())
                .containsExactly(PortfolioEvaluationResultResolver.FALLBACK_NOTICE);
        assertThat(resolved.riskFactors()).isNotEmpty();
        assertThat(resolved.reviewPoints()).isNotEmpty();
        assertThat(resolved.concentration().text()).doesNotContain("1개");
    }

    private PortfolioEvaluationResult providerResult(String concentrationText) {
        PortfolioEvaluationStatement portfolio = new PortfolioEvaluationStatement(
                "현재 평가액과 손익을 함께 확인했습니다.",
                List.of("P1"));
        PortfolioEvaluationStatement concentration = new PortfolioEvaluationStatement(
                concentrationText,
                List.of("C1"));
        PortfolioEvaluationStatement currency = new PortfolioEvaluationStatement(
                "통화별 평가 비중을 확인했습니다.",
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
                160,
                70);
    }

    private PortfolioEvaluationInput input() {
        return new PortfolioEvaluationInput(
                "KRW",
                Instant.parse("2026-08-01T08:00:00Z"),
                Instant.parse("2026-08-01T08:00:00Z"),
                2,
                2,
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
                List.of(new CurrencyExposure("KRW", new BigDecimal("100"), new BigDecimal("100"), 2)),
                List.of(),
                List.of(
                        new Evidence(
                                "P1",
                                "PORTFOLIO_TOTAL",
                                Map.of("evaluationAmount", "100", "profitLoss", "10"),
                                "평가액 100 · 손익 10"),
                        new Evidence(
                                "C1",
                                "CONCENTRATION",
                                Map.of(
                                        "holdingCount", "2",
                                        "topPositionWeight", "60",
                                        "topThreeWeight", "100",
                                        "hhi", "5200"),
                                "보유 2개 · 최대 종목 60% · 상위 3종목 100% · HHI 5200"),
                        new Evidence(
                                "FX1",
                                "CURRENCY_EXPOSURE",
                                Map.of("KRWWeight", "100"),
                                "KRW 100%")),
                List.of());
    }
}
