package com.finwatch.data.provider;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ProviderResponses {

    private ProviderResponses() {
    }

    public record Quote(
            String symbol,
            BigDecimal price,
            BigDecimal change,
            BigDecimal changeRate,
            BigDecimal volume,
            String currency,
            String providerId,
            Instant fetchedAt) {
    }

    public record Bar(
            LocalDate sessionDate,
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close,
            BigDecimal volume) {
    }

    public record BarSeries(
            String symbol,
            String interval,
            String providerId,
            Instant fetchedAt,
            List<Bar> items) {
    }

    public record NewsItem(
            String title,
            String description,
            String originalUrl,
            String naverUrl,
            Instant publishedAt,
            String providerId) {
    }

    public record NewsSearchResult(
            String query,
            int total,
            int start,
            int display,
            Instant fetchedAt,
            List<NewsItem> items) {
    }

    public record CompanyNewsItem(
            long externalId,
            String symbol,
            String title,
            String description,
            String originalUrl,
            String source,
            String category,
            Instant publishedAt,
            String providerId) {
    }

    public record CompanyNewsResult(
            String symbol,
            LocalDate from,
            LocalDate to,
            Instant fetchedAt,
            List<CompanyNewsItem> items) {
    }
}
