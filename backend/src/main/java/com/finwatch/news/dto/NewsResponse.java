package com.finwatch.news.dto;

import java.time.Instant;

public record NewsResponse(
        Long id,
        String symbol,
        String title,
        String publisher,
        String url,
        Instant publishedAt,
        boolean summaryAvailable,
        String source,
        String contentSource,
        String rightsProfile,
        boolean aiAnalysisAllowed,
        Instant fetchedAt) {
}
