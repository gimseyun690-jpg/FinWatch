package com.finwatch.alert.api;

import java.util.List;

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

import com.finwatch.alert.dto.AlertCreateRequest;
import com.finwatch.alert.dto.AlertResponse;
import com.finwatch.alert.dto.AlertUpdateRequest;
import com.finwatch.alert.service.AlertService;
import com.finwatch.common.api.ApiResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) { this.alertService = alertService; }

    @GetMapping
    public ApiResponse<List<AlertResponse>> get(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(alertService.getAlerts(userId(jwt)), "가격 알림 조회 성공");
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AlertResponse> create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AlertCreateRequest request) {
        return ApiResponse.success(alertService.create(userId(jwt), request), "가격 알림 생성 성공");
    }

    @PatchMapping("/{alertId}")
    public ApiResponse<AlertResponse> update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long alertId,
            @Valid @RequestBody AlertUpdateRequest request) {
        return ApiResponse.success(alertService.update(userId(jwt), alertId, request), "가격 알림 수정 성공");
    }

    @DeleteMapping("/{alertId}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long alertId) {
        alertService.delete(userId(jwt), alertId);
        return ApiResponse.success(null, "가격 알림 삭제 성공");
    }

    private Long userId(Jwt jwt) {
        Object claim = jwt.getClaim("userId");
        return claim instanceof Number number ? number.longValue() : Long.valueOf(String.valueOf(claim));
    }
}
