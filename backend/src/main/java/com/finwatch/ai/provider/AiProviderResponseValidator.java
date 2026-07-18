package com.finwatch.ai.provider;

import java.util.List;

import org.springframework.stereotype.Component;

import com.finwatch.ai.provider.AiProvider.AiProviderResult;

@Component
public class AiProviderResponseValidator {

    public AiProviderResult validate(AiProviderResult result) {
        if (result == null) throw AiProviderException.invalid("AI 공급자 응답이 비어 있습니다.");
        requireText(result.modelName(), 100, "modelName");
        requireText(result.summary(), 2_000, "summary");
        validateList(result.keyPoints(), 1, 3, 500, "keyPoints");
        validateList(result.positiveFactors(), 0, 3, 500, "positiveFactors");
        validateList(result.riskFactors(), 0, 3, 500, "riskFactors");
        validateList(result.mentionedCompanies(), 0, 5, 100, "mentionedCompanies");
        validateList(result.keywords(), 1, 5, 100, "keywords");
        if (!List.of("POSITIVE", "NEUTRAL", "NEGATIVE").contains(result.sentiment())) {
            throw AiProviderException.invalid("sentiment 값이 계약과 다릅니다.");
        }
        if (result.inputTokens() < 0 || result.outputTokens() < 0) {
            throw AiProviderException.invalid("토큰 수는 0 이상이어야 합니다.");
        }
        return result;
    }

    private void requireText(String value, int maxLength, String field) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) {
            throw AiProviderException.invalid(field + " 값이 계약과 다릅니다.");
        }
    }

    private void validateList(List<String> values, int min, int max, int itemMaxLength, String field) {
        if (values == null || values.size() < min || values.size() > max) {
            throw AiProviderException.invalid(field + " 항목 수가 계약과 다릅니다.");
        }
        for (String value : values) {
            if (value == null || value.isBlank() || value.trim().length() > itemMaxLength) {
                throw AiProviderException.invalid(field + " 항목이 계약과 다릅니다.");
            }
        }
    }
}
