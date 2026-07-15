package com.finwatch.stock.dto;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public final class StockDataLoadResponses {

    private StockDataLoadResponses() {
    }

    public enum DataLoadResource {
        QUOTE,
        DAILY_PRICES,
        NEWS,
        DISCLOSURES
    }

    public record DataLoadRequest(
            @NotEmpty Set<@NotNull DataLoadResource> resources) {
    }

    public record ResourceLoadResult(
            DataLoadResource resource,
            String status,
            String provider,
            int imported,
            String message,
            Instant asOf) {

        public static ResourceLoadResult pending(DataLoadResource resource) {
            return new ResourceLoadResult(resource, "PENDING", null, 0, "수집 대기 중", null);
        }
    }

    public record DataLoadResponse(
            String jobId,
            String market,
            String symbol,
            String status,
            boolean reused,
            Instant startedAt,
            Instant finishedAt,
            List<ResourceLoadResult> resources) {
    }

    public record DataLoadDispatch(DataLoadResponse response, boolean accepted) {
    }
}
