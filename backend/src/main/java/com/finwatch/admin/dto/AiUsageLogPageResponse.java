package com.finwatch.admin.dto;

import java.util.List;

public record AiUsageLogPageResponse(
        List<AiUsageLogResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
