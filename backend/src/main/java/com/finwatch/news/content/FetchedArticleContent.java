package com.finwatch.news.content;

import java.net.URI;
import java.time.Instant;

public record FetchedArticleContent(
        URI canonicalUrl,
        URI finalUrl,
        String title,
        String extractedText,
        String contentType,
        Instant fetchedAt,
        String etag,
        String lastModified,
        String contentHash,
        String extractorVersion,
        ContentSource contentSource,
        RightsProfile rightsProfile) {
}
