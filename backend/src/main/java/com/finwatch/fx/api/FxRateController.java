package com.finwatch.fx.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.finwatch.common.api.ApiResponse;
import com.finwatch.fx.dto.FxRateResponses.FxHistory;
import com.finwatch.fx.dto.FxRateResponses.FxPair;
import com.finwatch.fx.dto.FxRateResponses.LatestFxRate;
import com.finwatch.fx.service.FxRateService;

@RestController
@RequestMapping("/api/v1/market/fx-rates")
public class FxRateController {
    private final FxRateService service;

    public FxRateController(FxRateService service) {
        this.service = service;
    }

    @GetMapping("/pairs")
    public ApiResponse<List<FxPair>> pairs() {
        return ApiResponse.success(service.pairs(), "지원 환율 통화쌍 조회 성공");
    }

    @GetMapping("/{base}/{quote}")
    public ApiResponse<LatestFxRate> latest(@PathVariable String base, @PathVariable String quote) {
        return ApiResponse.success(service.latest(base, quote), "환율 조회 성공");
    }

    @GetMapping("/{base}/{quote}/history")
    public ApiResponse<FxHistory> history(
            @PathVariable String base,
            @PathVariable String quote,
            @RequestParam(defaultValue = "1M") String period,
            @RequestParam(defaultValue = "1D") String interval) {
        return ApiResponse.success(service.history(base, quote, period, interval), "환율 이력 조회 성공");
    }
}
