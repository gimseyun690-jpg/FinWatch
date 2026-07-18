package com.finwatch.data.catalog;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class InstrumentCatalogResponses {

    private InstrumentCatalogResponses() {
    }

    public record ProviderInstrument(
            String providerInstrumentId,
            String market,
            String exchange,
            String symbol,
            String name,
            String englishName,
            String instrumentType,
            String currency,
            String isin,
            LocalDate listedAt,
            boolean active,
            boolean tradable,
            String status) {
    }

    public record CatalogSnapshot(
            String provider,
            String scope,
            Instant fetchedAt,
            List<ProviderInstrument> instruments) {

        public CatalogSnapshot {
            instruments = instruments == null ? List.of() : List.copyOf(instruments);
        }
    }

    public record CatalogSyncResponse(
            String mode,
            Instant startedAt,
            Instant finishedAt,
            List<CatalogProviderSyncResult> providers) {
    }

    public record CatalogProviderSyncResult(
            String provider,
            String status,
            int received,
            int inserted,
            int updated,
            int deactivated,
            String message) {

        public static CatalogProviderSyncResult skipped(String provider, String message) {
            return new CatalogProviderSyncResult(provider, "SKIPPED", 0, 0, 0, 0, message);
        }
    }

    public record CatalogWriteResult(int received, int inserted, int updated, int deactivated) {
    }
}
