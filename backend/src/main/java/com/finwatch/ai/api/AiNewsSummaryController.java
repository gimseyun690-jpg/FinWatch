package com.finwatch.ai.api;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.finwatch.ai.dto.AiSummaryRequest;
import com.finwatch.ai.dto.AiSummaryResponse;
import com.finwatch.ai.service.AiNewsSummaryService;
import com.finwatch.common.api.ApiResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/ai/news-summaries")
public class AiNewsSummaryController {

    private final AiNewsSummaryService aiNewsSummaryService;

    public AiNewsSummaryController(AiNewsSummaryService aiNewsSummaryService) {
        this.aiNewsSummaryService = aiNewsSummaryService;
    }

    @PostMapping
    public ApiResponse<AiSummaryResponse> summarize(@Valid @RequestBody AiSummaryRequest request) {
        return ApiResponse.success(aiNewsSummaryService.summarize(request), "AI 뉴스 요약 완료");
    }
}

