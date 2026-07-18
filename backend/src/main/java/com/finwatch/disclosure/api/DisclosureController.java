package com.finwatch.disclosure.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.finwatch.common.api.ApiResponse;
import com.finwatch.disclosure.dto.DisclosureResponse;
import com.finwatch.disclosure.service.DisclosureQueryService;

@RestController
@RequestMapping("/api/v1/stocks")
public class DisclosureController {

    private final DisclosureQueryService disclosureQueryService;

    public DisclosureController(DisclosureQueryService disclosureQueryService) {
        this.disclosureQueryService = disclosureQueryService;
    }

    @GetMapping("/{market}/{symbol}/disclosures")
    public ApiResponse<List<DisclosureResponse>> disclosures(
            @PathVariable String market,
            @PathVariable String symbol) {
        return ApiResponse.success(
                disclosureQueryService.get(market, symbol),
                "종목 공시 목록 조회 성공");
    }
}
