package com.finwatch.ai.dto;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record TechnicalExplanationInput(
        String market,
        String symbol,
        String currency,
        String interval,
        Instant latestRecordedAt,
        String source,
        String freshness,
        boolean adjusted,
        String calculationVersion,
        int sampleCount,
        String summarySignal,
        List<TechnicalEvidence> evidence) implements Serializable {

    public record TechnicalEvidence(
            String id,
            String indicator,
            Map<String, String> values,
            String displayValue) implements Serializable {
    }
}
