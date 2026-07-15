package com.finwatch.news.dto;

import java.time.Instant;

public record NewsDetailResponse(
        Long id,
        String market,
        String symbol,
        String externalId,
        String title,
        String publisher,
        String url,
        String canonicalUrl,
        String contentKind,
        String disclosureType,
        Instant publishedAt,
        String source,
        String contentSource,
        String rightsProfile,
        boolean aiAnalysisAllowed,
        boolean contentDisplayAllowed,
        String content,
        String contentHash,
        String extractorVersion,
        Instant fetchedAt,
        boolean summaryAvailable) { }
