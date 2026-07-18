package com.finwatch.data.sync;

import java.time.Instant;
import java.util.List;

public final class DataSyncResponses {

    private DataSyncResponses() {
    }

    public record DataSyncResponse(
            String mode,
            Instant startedAt,
            Instant finishedAt,
            int pricesImported,
            int newsImported,
            List<StockSyncResult> stocks) {
    }

    public record StockSyncResult(
            String symbol,
            String market,
            ProviderSyncResult marketPrices,
            ProviderSyncResult news) {
    }

    public record ProviderSyncResult(
            String provider,
            String status,
            int imported,
            String message) {

        public static ProviderSyncResult success(String provider, int imported) {
            return new ProviderSyncResult(provider, "SUCCESS", imported, "동기화 완료");
        }

        public static ProviderSyncResult skipped(String provider, String message) {
            return new ProviderSyncResult(provider, "SKIPPED", 0, message);
        }

        public static ProviderSyncResult fallback(String provider, String message) {
            return new ProviderSyncResult(provider, "FALLBACK", 0, message);
        }
    }
}
