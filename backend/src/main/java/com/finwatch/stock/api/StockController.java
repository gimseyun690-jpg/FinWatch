package com.finwatch.stock.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.finwatch.common.api.ApiResponse;
import com.finwatch.stock.dto.StockResponses.PriceHistory;
import com.finwatch.stock.dto.StockResponses.StockSummary;
import com.finwatch.stock.dto.StockResponses.TechnicalAnalysis;
import com.finwatch.stock.dto.StockSearchResponses.CanonicalStockDetail;
import com.finwatch.stock.dto.StockSearchResponses.StockSearchPage;
import com.finwatch.stock.dto.StockDataLoadResponses.DataLoadRequest;
import com.finwatch.stock.dto.StockDataLoadResponses.DataLoadResponse;
import com.finwatch.stock.service.StockDataLoadService;
import com.finwatch.stock.service.StockQueryService;
import com.finwatch.stock.service.StockSearchService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/stocks")
public class StockController {

    private final StockQueryService stockQueryService;
    private final StockSearchService stockSearchService;
    private final StockDataLoadService stockDataLoadService;

    public StockController(
            StockQueryService stockQueryService,
            StockSearchService stockSearchService,
            StockDataLoadService stockDataLoadService) {
        this.stockQueryService = stockQueryService;
        this.stockSearchService = stockSearchService;
        this.stockDataLoadService = stockDataLoadService;
    }

    @GetMapping
    public ApiResponse<List<StockSummary>> stocks() {
        return ApiResponse.success(stockQueryService.getStocks(), "종목 목록 조회 성공");
    }

    @GetMapping("/search")
    public ApiResponse<StockSearchPage> search(
            @RequestParam(name = "q") String query,
            @RequestParam(required = false) String market,
            @RequestParam(name = "type", required = false) String instrumentType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(
                stockSearchService.search(query, market, instrumentType, page, size),
                "종목 검색 성공");
    }

    @GetMapping("/{market}/{symbol}")
    public ApiResponse<CanonicalStockDetail> canonicalStock(
            @PathVariable String market,
            @PathVariable String symbol) {
        return ApiResponse.success(stockQueryService.getStock(market, symbol), "종목 상세 조회 성공");
    }

    @GetMapping("/{market}/{symbol}/prices")
    public ApiResponse<PriceHistory> canonicalPrices(
            @PathVariable String market,
            @PathVariable String symbol,
            @RequestParam(defaultValue = "3M") String period,
            @RequestParam(defaultValue = "1D") String interval) {
        return ApiResponse.success(
                stockQueryService.getPriceHistory(market, symbol, period, interval),
                "가격 이력 조회 성공");
    }

    @GetMapping("/{market}/{symbol}/intraday")
    public ApiResponse<PriceHistory> canonicalIntraday(
            @PathVariable String market,
            @PathVariable String symbol,
            @RequestParam(defaultValue = "390") int limit) {
        return ApiResponse.success(
                stockQueryService.getIntradayPriceHistory(market, symbol, limit),
                "실시간 1분 봉 조회 성공");
    }

    @GetMapping("/{market}/{symbol}/technical")
    public ApiResponse<TechnicalAnalysis> canonicalTechnical(
            @PathVariable String market,
            @PathVariable String symbol) {
        return ApiResponse.success(
                stockQueryService.getTechnicalAnalysis(market, symbol),
                "기술적 분석 조회 성공");
    }

    @PostMapping("/{market}/{symbol}/data-loads")
    public ResponseEntity<ApiResponse<DataLoadResponse>> startDataLoad(
            @PathVariable String market,
            @PathVariable String symbol,
            @Valid @RequestBody DataLoadRequest request) {
        var dispatch = stockDataLoadService.start(market, symbol, request.resources());
        HttpStatus status = dispatch.accepted() ? HttpStatus.ACCEPTED : HttpStatus.OK;
        return ResponseEntity.status(status)
                .body(ApiResponse.success(dispatch.response(), "종목 데이터 준비 요청을 처리했습니다."));
    }

    @GetMapping("/{market}/{symbol}/data-loads/{jobId}")
    public ApiResponse<DataLoadResponse> dataLoad(
            @PathVariable String market,
            @PathVariable String symbol,
            @PathVariable String jobId) {
        return ApiResponse.success(
                stockDataLoadService.get(market, symbol, jobId),
                "종목 데이터 준비 상태 조회 성공");
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
