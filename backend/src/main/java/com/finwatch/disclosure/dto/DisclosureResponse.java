package com.finwatch.disclosure.dto;

import java.time.Instant;

public record DisclosureResponse(
        Long id,
        String market,
        String symbol,
        String title,
        String publisher,
        String url,
        Instant publishedAt,
        String source,
        String disclosureType,
        boolean aiAnalysisAllowed,
        Instant fetchedAt) {
}
