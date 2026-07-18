package com.finwatch.content.dto;

public record ContentFeedRequest(
        String kind,
        String market,
        String symbol,
        String query,
        String period,
        String from,
        String to,
        String source,
        String analysis,
        String page,
        String size,
        String sort) {
}
