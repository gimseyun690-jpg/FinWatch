package com.finwatch.content.dto;

import java.time.Instant;
import java.util.List;

public final class ContentFeedResponses {

    private ContentFeedResponses() {
    }

    public record ContentFeedItem(
            Long id,
            String kind,
            String market,
            String symbol,
            String stockName,
            String title,
            String publisher,
            String source,
            Instant publishedAt,
            String disclosureType,
            String contentSource,
            String rightsProfile,
            boolean aiAnalysisAllowed,
            String aiAnalysisStatus,
            String summaryPreview,
            String url) {
    }

    public record ContentFeedPage(
            List<ContentFeedItem> items,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasPrevious,
            boolean hasNext,
            String sort) {
    }
}
