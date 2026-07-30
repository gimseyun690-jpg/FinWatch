package com.finwatch.ai.api;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.finwatch.ai.dto.PortfolioEvaluationRequest;
import com.finwatch.ai.dto.PortfolioEvaluationResponse;
import com.finwatch.ai.service.AiPortfolioEvaluationService;
import com.finwatch.ai.service.AiRequestGuard;
import com.finwatch.common.api.ApiResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/ai/portfolio-evaluations")
public class AiPortfolioEvaluationController {

    private final AiPortfolioEvaluationService service;
    private final AiRequestGuard requestGuard;

    public AiPortfolioEvaluationController(
            AiPortfolioEvaluationService service,
            AiRequestGuard requestGuard) {
        this.service = service;
        this.requestGuard = requestGuard;
    }

    @PostMapping
    public ApiResponse<PortfolioEvaluationResponse> evaluate(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PortfolioEvaluationRequest request) {
        requestGuard.checkRequest(jwt == null ? null : jwt.getSubject());
        return ApiResponse.success(
                service.evaluate(userId(jwt), request),
                "포트폴리오 AI 평가 완료");
    }

    private Long userId(Jwt jwt) {
        Object claim = jwt.getClaim("userId");
        return claim instanceof Number number
                ? number.longValue()
                : Long.valueOf(String.valueOf(claim));
    }
}
