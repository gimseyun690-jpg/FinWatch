package com.finwatch.news.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.finwatch.common.api.ApiResponse;
import com.finwatch.news.dto.NewsResponse;
import com.finwatch.news.service.NewsQueryService;

@RestController
@RequestMapping("/api/v1/stocks/{symbol}/news")
public class NewsController {

    private final NewsQueryService newsQueryService;

    public NewsController(NewsQueryService newsQueryService) {
        this.newsQueryService = newsQueryService;
    }

    @GetMapping
    public ApiResponse<List<NewsResponse>> news(@PathVariable String symbol) {
        return ApiResponse.success(newsQueryService.getNews(symbol), "종목 뉴스 조회 성공");
    }
}

