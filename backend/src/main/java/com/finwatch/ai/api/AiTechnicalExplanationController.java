package com.finwatch.ai.api;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.finwatch.ai.dto.TechnicalExplanationRequest;
import com.finwatch.ai.dto.TechnicalExplanationResponse;
import com.finwatch.ai.service.AiTechnicalExplanationService;
import com.finwatch.ai.service.AiRequestGuard;
import com.finwatch.common.api.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/ai/technical-explanations")
public class AiTechnicalExplanationController {

    private final AiTechnicalExplanationService service;
    private final AiRequestGuard requestGuard;

    public AiTechnicalExplanationController(AiTechnicalExplanationService service, AiRequestGuard requestGuard) {
        this.service = service;
        this.requestGuard = requestGuard;
    }

    @PostMapping
    public ApiResponse<TechnicalExplanationResponse> explain(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody TechnicalExplanationRequest request) {
        requestGuard.checkRequest(jwt == null ? null : jwt.getSubject());
        return ApiResponse.success(service.explain(request), "AI 기술지표 해설 완료");
    }
}
