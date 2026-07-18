package com.finwatch.ai.api;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.finwatch.ai.dto.AiSummaryRequest;
import com.finwatch.ai.dto.AiSummaryResponse;
import com.finwatch.ai.service.AiNewsSummaryOrchestrationService;
import com.finwatch.ai.service.AiRequestGuard;
import com.finwatch.common.api.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/ai/news-summaries")
public class AiNewsSummaryController {

    private final AiNewsSummaryOrchestrationService aiNewsSummaryService;
    private final AiRequestGuard requestGuard;

    public AiNewsSummaryController(AiNewsSummaryOrchestrationService aiNewsSummaryService, AiRequestGuard requestGuard) {
        this.aiNewsSummaryService = aiNewsSummaryService;
        this.requestGuard = requestGuard;
    }

    @PostMapping
    public ApiResponse<AiSummaryResponse> summarize(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AiSummaryRequest request) {
        requestGuard.checkRequest(jwt == null ? null : jwt.getSubject());
        return ApiResponse.success(aiNewsSummaryService.summarize(request), "AI 뉴스 요약 완료");
    }
}
