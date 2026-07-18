package com.finwatch.stock.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.finwatch.common.api.ApiResponse;
import com.finwatch.realtime.RealtimeQuoteHub;
import com.finwatch.realtime.RealtimeSnapshot;

@RestController
@RequestMapping("/api/v1/stocks/realtime")
public class RealtimeQuoteController {

    private final RealtimeQuoteHub hub;

    public RealtimeQuoteController(RealtimeQuoteHub hub) {
        this.hub = hub;
    }

    @GetMapping
    public ApiResponse<RealtimeSnapshot> snapshot() {
        return ApiResponse.success(hub.snapshot(), "실시간 시세 연결 상태 조회 성공");
    }
}
