package com.finwatch.news.dto;

import java.time.Instant;

public record NewsContentRefreshResponse(
        Long newsId,
        boolean contentChanged,
        String previousContentHash,
        String contentHash,
        String contentSource,
        String rightsProfile,
        String canonicalUrl,
        String finalUrl,
        String extractorVersion,
        int extractedCharacters,
        Instant fetchedAt) {
}
