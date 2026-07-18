package com.finwatch.news.content;

import java.time.LocalDate;

public record SourcePolicyDecision(
        String host,
        String pathPrefix,
        SourceFetchMode fetchMode,
        RightsProfile rightsProfile,
        ContentSource contentSource,
        int minIntervalMs,
        boolean userAgentRequired,
        String reviewReference,
        LocalDate reviewedAt) {

    public static SourcePolicyDecision metadataOnly(String host) {
        return new SourcePolicyDecision(
                host,
                "/",
                SourceFetchMode.METADATA_ONLY,
                RightsProfile.METADATA_ONLY,
                ContentSource.METADATA_ONLY,
                0,
                false,
                "등록된 출처 정책 없음",
                null);
    }

    public boolean allowsDirectFetch() {
        return fetchMode == SourceFetchMode.ALLOWLIST_FETCH;
    }
}
