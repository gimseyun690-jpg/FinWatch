package com.finwatch.disclosure.provider;

import java.time.Instant;
import java.util.List;

public final class DisclosureProviderResponses {

    private DisclosureProviderResponses() {
    }

    public record DisclosureItem(
            String externalId,
            String title,
            String publisher,
            String url,
            Instant publishedAt,
            String disclosureType,
            String source) {
    }

    public record DisclosureFetchResult(
            String provider,
            Instant fetchedAt,
            List<DisclosureItem> items) {

        public DisclosureFetchResult {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }
}
