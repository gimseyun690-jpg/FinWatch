package com.finwatch.ai.dto;

import jakarta.validation.constraints.NotNull;

public record AiDisclosureSummaryRequest(
        @NotNull Long disclosureId,
        String promptVersion) {
}
