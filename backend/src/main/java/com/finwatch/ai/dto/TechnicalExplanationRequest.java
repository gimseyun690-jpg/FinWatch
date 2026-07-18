package com.finwatch.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TechnicalExplanationRequest(
        @Size(max = 30) @Pattern(regexp = "[A-Za-z0-9._\\-]+") String market,
        @NotBlank @Size(max = 30) @Pattern(regexp = "[A-Za-z0-9.\\-]+") String symbol,
        @Size(max = 10) String interval,
        @Size(max = 50) String promptVersion) {
}
