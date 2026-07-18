package com.finwatch.content.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.finwatch.common.api.ApiResponse;
import com.finwatch.content.dto.ContentFeedRequest;
import com.finwatch.content.dto.ContentFeedResponses.ContentFeedPage;
import com.finwatch.content.service.ContentFeedService;

@RestController
@RequestMapping("/api/v1/content-feed")
public class ContentFeedController {

    private final ContentFeedService contentFeedService;

    public ContentFeedController(ContentFeedService contentFeedService) {
        this.contentFeedService = contentFeedService;
    }

    @GetMapping
    public ApiResponse<ContentFeedPage> get(
            @RequestParam(defaultValue = "ALL") String kind,
            @RequestParam(defaultValue = "ALL") String market,
            @RequestParam(required = false) String symbol,
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(defaultValue = "7D") String period,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "ALL") String source,
            @RequestParam(defaultValue = "ALL") String analysis,
            @RequestParam(defaultValue = "0") String page,
            @RequestParam(defaultValue = "20") String size,
            @RequestParam(defaultValue = "publishedAt,desc") String sort) {
        return ApiResponse.success(
                contentFeedService.get(new ContentFeedRequest(
                        kind, market, symbol, query, period, from, to,
                        source, analysis, page, size, sort)),
                "Content feed retrieved successfully.");
    }
}
