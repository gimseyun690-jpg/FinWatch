package com.finwatch.ai.dto;

import jakarta.validation.constraints.NotNull;

public record AiSummaryRequest(
        @NotNull Long newsId,
        String promptVersion) {
}

