package com.finwatch.data.api;

import java.time.LocalDate;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.finwatch.common.api.ApiResponse;
import com.finwatch.data.provider.FinnhubNewsClient;
import com.finwatch.data.provider.KisMarketDataClient;
import com.finwatch.data.provider.NaverNewsSearchClient;
import com.finwatch.data.provider.ProviderResponses.BarSeries;
import com.finwatch.data.provider.ProviderResponses.CompanyNewsResult;
import com.finwatch.data.provider.ProviderResponses.NewsSearchResult;
import com.finwatch.data.provider.ProviderResponses.Quote;

@RestController
@RequestMapping("/api/v1/providers")
public class ProviderDiagnosticController {

    private final KisMarketDataClient kisMarketDataClient;
    private final NaverNewsSearchClient naverNewsSearchClient;
    private final FinnhubNewsClient finnhubNewsClient;

    public ProviderDiagnosticController(
            KisMarketDataClient kisMarketDataClient,
            NaverNewsSearchClient naverNewsSearchClient,
            FinnhubNewsClient finnhubNewsClient) {
        this.kisMarketDataClient = kisMarketDataClient;
        this.naverNewsSearchClient = naverNewsSearchClient;
        this.finnhubNewsClient = finnhubNewsClient;
    }

    @GetMapping("/kis/quotes/{symbol}")
    public ApiResponse<Quote> quote(@PathVariable String symbol) {
        return ApiResponse.success(kisMarketDataClient.getDomesticQuote(symbol), "KIS 현재가 조회 성공");
    }

    @GetMapping("/kis/bars/{symbol}")
    public ApiResponse<BarSeries> bars(
            @PathVariable String symbol,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to) {
        return ApiResponse.success(kisMarketDataClient.getDomesticDailyBars(symbol, from, to), "KIS 일봉 조회 성공");
    }

    @GetMapping("/naver/news")
    public ApiResponse<NewsSearchResult> naverNews(
            @RequestParam String query,
            @RequestParam(defaultValue = "10") int display) {
        return ApiResponse.success(naverNewsSearchClient.search(query, display), "NAVER API HUB 뉴스 조회 성공");
    }

    @GetMapping("/finnhub/news")
    public ApiResponse<CompanyNewsResult> finnhubNews(
            @RequestParam String symbol,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to) {
        return ApiResponse.success(finnhubNewsClient.companyNews(symbol, from, to), "Finnhub 회사 뉴스 조회 성공");
    }
}
