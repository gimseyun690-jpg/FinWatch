package com.finwatch.news.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.finwatch.common.api.ApiResponse;
import com.finwatch.news.dto.NewsResponse;
import com.finwatch.news.dto.NewsDetailResponse;
import com.finwatch.news.service.NewsQueryService;

@RestController
@RequestMapping("/api/v1")
public class NewsController {

    private final NewsQueryService newsQueryService;

    public NewsController(NewsQueryService newsQueryService) {
        this.newsQueryService = newsQueryService;
    }

    @GetMapping("/stocks/{symbol}/news")
    public ApiResponse<List<NewsResponse>> news(@PathVariable String symbol) {
        return ApiResponse.success(newsQueryService.getNews(symbol), "종목 뉴스 조회 성공");
    }

    @GetMapping("/stocks/{market}/{symbol}/news")
    public ApiResponse<List<NewsResponse>> canonicalNews(
            @PathVariable String market,
            @PathVariable String symbol) {
        return ApiResponse.success(newsQueryService.getNews(market, symbol), "종목 뉴스 조회 성공");
    }

    @GetMapping("/news/{newsId}")
    public ApiResponse<NewsDetailResponse> detail(@PathVariable Long newsId) {
        return ApiResponse.success(newsQueryService.getNewsDetail(newsId), "뉴스 상세 조회 성공");
    }
}
