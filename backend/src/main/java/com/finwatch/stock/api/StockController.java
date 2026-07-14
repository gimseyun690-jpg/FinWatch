package com.finwatch.stock.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.finwatch.common.api.ApiResponse;
import com.finwatch.stock.dto.StockResponses.PriceHistory;
import com.finwatch.stock.dto.StockResponses.StockSummary;
import com.finwatch.stock.dto.StockResponses.TechnicalAnalysis;
import com.finwatch.stock.service.StockQueryService;

@RestController
@RequestMapping("/api/v1/stocks")
public class StockController {

    private final StockQueryService stockQueryService;

    public StockController(StockQueryService stockQueryService) {
        this.stockQueryService = stockQueryService;
    }

    @GetMapping
    public ApiResponse<List<StockSummary>> stocks() {
        return ApiResponse.success(stockQueryService.getStocks(), "종목 목록 조회 성공");
    }

    @GetMapping("/{symbol}")
    public ApiResponse<StockSummary> stock(@PathVariable String symbol) {
        return ApiResponse.success(stockQueryService.getStock(symbol), "종목 상세 조회 성공");
    }

    @GetMapping("/{symbol}/prices")
    public ApiResponse<PriceHistory> prices(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "3M") String period,
            @RequestParam(defaultValue = "1D") String interval) {
        return ApiResponse.success(
                stockQueryService.getPriceHistory(symbol, period, interval),
                "가격 이력 조회 성공");
    }

    @GetMapping("/{symbol}/intraday")
    public ApiResponse<PriceHistory> intraday(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "390") int limit) {
        return ApiResponse.success(
                stockQueryService.getIntradayPriceHistory(symbol, limit),
                "실시간 1분 봉 조회 성공");
    }

    @GetMapping("/{symbol}/technical")
    public ApiResponse<TechnicalAnalysis> technical(@PathVariable String symbol) {
        return ApiResponse.success(stockQueryService.getTechnicalAnalysis(symbol), "기술적 분석 조회 성공");
    }
}
