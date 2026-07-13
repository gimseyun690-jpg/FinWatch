package com.finwatch.portfolio.api;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.finwatch.common.api.ApiResponse;
import com.finwatch.portfolio.dto.PortfolioCreateRequest;
import com.finwatch.portfolio.dto.PortfolioResponse;
import com.finwatch.portfolio.dto.PortfolioResponse.Holding;
import com.finwatch.portfolio.dto.PortfolioUpdateRequest;
import com.finwatch.portfolio.service.PortfolioService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/portfolios")
public class PortfolioController {

    private final PortfolioService portfolioService;

    public PortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @GetMapping
    public ApiResponse<PortfolioResponse> get(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(portfolioService.getPortfolio(userId(jwt)), "포트폴리오 조회 성공");
    }

    @PostMapping("/holdings")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Holding> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PortfolioCreateRequest request) {
        return ApiResponse.success(portfolioService.create(userId(jwt), request), "보유 종목 등록 성공");
    }

    @PatchMapping("/holdings/{holdingId}")
    public ApiResponse<Holding> update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long holdingId,
            @Valid @RequestBody PortfolioUpdateRequest request) {
        return ApiResponse.success(portfolioService.update(userId(jwt), holdingId, request), "보유 종목 수정 성공");
    }

    @DeleteMapping("/holdings/{holdingId}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long holdingId) {
        portfolioService.delete(userId(jwt), holdingId);
        return ApiResponse.success(null, "보유 종목 삭제 성공");
    }

    private Long userId(Jwt jwt) {
        Object claim = jwt.getClaim("userId");
        return claim instanceof Number number ? number.longValue() : Long.valueOf(String.valueOf(claim));
    }
}
