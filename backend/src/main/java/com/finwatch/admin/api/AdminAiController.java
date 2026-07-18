package com.finwatch.admin.api;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.finwatch.admin.dto.AiMetricsResponse;
import com.finwatch.admin.dto.AiUsageLogPageResponse;
import com.finwatch.admin.service.AdminAiService;
import com.finwatch.common.api.ApiResponse;

@RestController
@RequestMapping("/api/v1/admin/ai")
public class AdminAiController {

    private final AdminAiService adminAiService;

    public AdminAiController(AdminAiService adminAiService) {
        this.adminAiService = adminAiService;
    }

    @GetMapping("/metrics")
    public ApiResponse<AiMetricsResponse> getMetrics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.success(adminAiService.getMetrics(from, to), "AI 운영 지표 조회 성공");
    }

    @GetMapping("/usage-logs")
    public ApiResponse<AiUsageLogPageResponse> getUsageLogs(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        return ApiResponse.success(
                adminAiService.getUsageLogs(from, to, page, size, sort, direction),
                "AI 사용 로그 조회 성공");
    }
}
