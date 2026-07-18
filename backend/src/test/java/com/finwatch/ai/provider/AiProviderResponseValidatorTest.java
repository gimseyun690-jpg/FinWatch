package com.finwatch.ai.provider;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.finwatch.ai.provider.AiProvider.AiProviderResult;

class AiProviderResponseValidatorTest {

    private final AiProviderResponseValidator validator = new AiProviderResponseValidator();

    @Test
    void rejectsProviderResponseWithTooManyRiskFactors() {
        AiProviderResult invalid = new AiProviderResult(
                "model", "summary", List.of("point"), List.of(),
                List.of("r1", "r2", "r3", "r4"), List.of(), List.of("keyword"),
                "NEUTRAL", 10, 5);

        assertThatThrownBy(() -> validator.validate(invalid))
                .isInstanceOf(AiProviderException.class)
                .extracting(exception -> ((AiProviderException) exception).getCode())
                .isEqualTo("AI_RESPONSE_INVALID");
    }

    @Test
    void rejectsUnsupportedSentiment() {
        AiProviderResult invalid = new AiProviderResult(
                "model", "summary", List.of("point"), List.of(), List.of(),
                List.of(), List.of("keyword"), "STRONG_BUY", 10, 5);

        assertThatThrownBy(() -> validator.validate(invalid))
                .isInstanceOf(AiProviderException.class);
    }
}
