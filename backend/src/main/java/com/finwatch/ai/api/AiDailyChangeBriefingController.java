package com.finwatch.ai.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.finwatch.ai.dto.DailyChangeBriefingRequest;
import com.finwatch.ai.dto.DailyChangeBriefingResponse;
import com.finwatch.ai.service.AiDailyChangeBriefingService;
import com.finwatch.ai.service.AiRequestGuard;
import com.finwatch.common.api.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
public class AiDailyChangeBriefingController {
    private final AiDailyChangeBriefingService service;
    private final AiRequestGuard requestGuard;
    public AiDailyChangeBriefingController(AiDailyChangeBriefingService service, AiRequestGuard requestGuard) { this.service = service; this.requestGuard = requestGuard; }

    @PostMapping("/ai/daily-change-briefings")
    public ApiResponse<DailyChangeBriefingResponse> generate(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody DailyChangeBriefingRequest request) {
        requestGuard.checkRequest(jwt == null ? null : jwt.getSubject());
        return ApiResponse.success(service.generate(request), "일일 변화 브리핑 생성 완료");
    }

    @GetMapping("/stocks/{symbol}/daily-change-briefings/latest")
    public ApiResponse<DailyChangeBriefingResponse> latest(@PathVariable String symbol,
            @RequestParam(required = false) String market) {
        return ApiResponse.success(service.latest(market, symbol), "최신 일일 변화 브리핑 조회 완료");
    }
}
