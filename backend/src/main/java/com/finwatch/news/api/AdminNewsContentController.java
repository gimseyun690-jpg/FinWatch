package com.finwatch.news.api;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.finwatch.common.api.ApiResponse;
import com.finwatch.news.dto.NewsContentRefreshResponse;
import com.finwatch.news.service.NewsContentIngestionService;

@RestController
@RequestMapping("/api/v1/admin/news")
public class AdminNewsContentController {

    private final NewsContentIngestionService newsContentIngestionService;

    public AdminNewsContentController(NewsContentIngestionService newsContentIngestionService) {
        this.newsContentIngestionService = newsContentIngestionService;
    }

    @PostMapping("/{newsId}/content/refresh")
    public ApiResponse<NewsContentRefreshResponse> refresh(@PathVariable Long newsId) {
        return ApiResponse.success(
                newsContentIngestionService.refresh(newsId),
                "뉴스 원문 수집 및 갱신 완료");
    }
}
