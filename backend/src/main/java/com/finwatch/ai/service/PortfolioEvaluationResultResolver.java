package com.finwatch.ai.service;

import java.util.List;

import org.springframework.stereotype.Component;

import com.finwatch.ai.dto.PortfolioEvaluationInput;
import com.finwatch.ai.provider.AiProvider.PortfolioEvaluationResult;
import com.finwatch.ai.provider.AiProvider.PortfolioEvaluationStatement;
import com.finwatch.ai.provider.AiProviderException;
import com.finwatch.ai.provider.PortfolioEvaluationResponseValidator;

@Component
public class PortfolioEvaluationResultResolver {

    static final String FALLBACK_NOTICE =
            "AI 모델 응답의 근거 연결을 검증할 수 없어 서버가 계산한 근거 기반 설명으로 대체했습니다.";
    private static final String FALLBACK_MODEL_SUFFIX = "+server-grounded-fallback";

    private final PortfolioEvaluationResponseValidator validator;

    public PortfolioEvaluationResultResolver(PortfolioEvaluationResponseValidator validator) {
        this.validator = validator;
    }

    public ResolvedPortfolioEvaluation resolve(
            PortfolioEvaluationResult providerResult,
            PortfolioEvaluationInput input,
            String providerName) {
        try {
            return new ResolvedPortfolioEvaluation(
                    validator.validate(providerResult, input),
                    false);
        } catch (AiProviderException exception) {
            if (!"AI_RESPONSE_INVALID".equals(exception.getCode())) throw exception;
            return new ResolvedPortfolioEvaluation(
                    validator.validate(fallback(providerResult, input, providerName), input),
                    true);
        }
    }

    private PortfolioEvaluationResult fallback(
            PortfolioEvaluationResult providerResult,
            PortfolioEvaluationInput input,
            String providerName) {
        String headline = switch (input.concentrationBand()) {
            case "DIVERSIFIED" -> "여러 보유 종목에 자산이 비교적 고르게 분산되어 있습니다.";
            case "MODERATE_CONCENTRATION" -> "일부 보유 종목의 비중이 포트폴리오 흐름에 영향을 주고 있습니다.";
            case "HIGH_CONCENTRATION" -> "상위 보유 종목의 비중이 전체 평가에 큰 영향을 주는 구조입니다.";
            default -> "가격 또는 환율이 불완전해 전체 자산 구성을 제한적으로 평가했습니다.";
        };
        PortfolioEvaluationStatement diversification = new PortfolioEvaluationStatement(
                input.conversionComplete()
                        ? "서버가 계산한 종목별 기준통화 비중을 바탕으로 분산 상태를 확인했습니다."
                        : "서로 다른 통화를 하나의 전체 비중으로 합치지 않고 데이터 한계를 우선 확인했습니다.",
                List.of("C1"));
        PortfolioEvaluationStatement concentration = new PortfolioEvaluationStatement(
                "최대 보유 종목과 상위 보유 종목 묶음의 비중, 집중도 지표를 함께 살폈습니다.",
                List.of("C1"));
        PortfolioEvaluationStatement currency = new PortfolioEvaluationStatement(
                "통화별 평가 비중을 기준으로 환율 변화에 노출되는 자산 구성을 확인했습니다.",
                List.of("FX1"));
        PortfolioEvaluationStatement performance = new PortfolioEvaluationStatement(
                input.profitLossComplete()
                        ? "현재 평가액과 매입 기준 금액의 차이를 포트폴리오 성과 맥락으로 확인했습니다."
                        : "매수 당시 환율이 부족한 항목은 통합 손익을 확정하지 않고 제한사항으로 분리했습니다.",
                List.of("P1"));
        List<PortfolioEvaluationStatement> strengths = input.conversionComplete()
                ? List.of(new PortfolioEvaluationStatement(
                        "기준통화로 환산된 동일 기준의 평가 비중을 확인할 수 있습니다.",
                        List.of("P1", "FX1")))
                : List.of();
        List<PortfolioEvaluationStatement> risks = List.of(new PortfolioEvaluationStatement(
                "상위 종목 집중도와 통화 노출이 전체 평가액 변동에 미치는 영향을 함께 점검해야 합니다.",
                List.of("C1", "FX1")));
        List<PortfolioEvaluationStatement> reviewPoints = List.of(new PortfolioEvaluationStatement(
                "보유 구성과 환율 기준시각이 바뀔 때 동일한 기준으로 비중 변화를 다시 확인할 수 있습니다.",
                List.of("P1", "C1", "FX1")));
        String summary = headline + " 이 평가는 서버가 산출한 비중과 손익 근거를 설명한 참고 정보입니다.";
        return new PortfolioEvaluationResult(
                fallbackModelName(providerResult, providerName),
                headline,
                summary,
                diversification,
                concentration,
                currency,
                performance,
                strengths,
                risks,
                reviewPoints,
                List.of(FALLBACK_NOTICE),
                safeTokens(providerResult == null ? 0 : providerResult.inputTokens()),
                safeTokens(providerResult == null ? 0 : providerResult.outputTokens()));
    }

    private String fallbackModelName(PortfolioEvaluationResult providerResult, String providerName) {
        String base = providerResult == null ? null : providerResult.modelName();
        if (base == null || base.isBlank()) base = providerName;
        if (base == null || base.isBlank()) base = "ai-provider";
        int maxBaseLength = 100 - FALLBACK_MODEL_SUFFIX.length();
        String normalized = base.trim();
        if (normalized.length() > maxBaseLength) normalized = normalized.substring(0, maxBaseLength);
        return normalized + FALLBACK_MODEL_SUFFIX;
    }

    private int safeTokens(int value) {
        return Math.max(0, value);
    }

    public record ResolvedPortfolioEvaluation(
            PortfolioEvaluationResult result,
            boolean fallbackUsed) {
    }
}
