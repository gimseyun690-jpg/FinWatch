package com.finwatch.ai.dto;

import jakarta.validation.constraints.Size;

public record PortfolioEvaluationRequest(
        @Size(max = 80) String promptVersion) {
}
