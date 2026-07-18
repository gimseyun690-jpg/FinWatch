package com.finwatch.ai.api;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.finwatch.ai.dto.AiDisclosureSummaryRequest;
import com.finwatch.ai.dto.AiSummaryResponse;
import com.finwatch.ai.service.AiDisclosureSummaryService;
import com.finwatch.ai.service.AiRequestGuard;
import com.finwatch.common.api.ApiResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/ai/disclosure-summaries")
public class AiDisclosureSummaryController {

    private final AiDisclosureSummaryService disclosureSummaryService;
    private final AiRequestGuard requestGuard;

    public AiDisclosureSummaryController(
            AiDisclosureSummaryService disclosureSummaryService,
            AiRequestGuard requestGuard) {
        this.disclosureSummaryService = disclosureSummaryService;
        this.requestGuard = requestGuard;
    }

    @PostMapping
    public ApiResponse<AiSummaryResponse> summarize(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AiDisclosureSummaryRequest request) {
        requestGuard.checkRequest(jwt == null ? null : jwt.getSubject());
        return ApiResponse.success(
                disclosureSummaryService.summarize(request),
                "AI 공시 요약 완료");
    }
}
