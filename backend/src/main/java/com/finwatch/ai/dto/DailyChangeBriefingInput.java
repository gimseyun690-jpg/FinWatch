package com.finwatch.ai.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record DailyChangeBriefingInput(
        String market,
        String symbol,
        String currency,
        LocalDate currentTradingDate,
        LocalDate previousTradingDate,
        String baselineStatus,
        Instant latestRecordedAt,
        String calculationVersion,
        String briefingInputVersion,
        String source,
        String freshness,
        BigDecimal priceChange,
        BigDecimal priceChangeRate,
        String relation,
        List<BriefingViewpoint> viewpoints,
        List<BriefingEvidence> evidence,
        int excludedContentCount,
        List<String> serverDataLimitations) implements Serializable {

    public record BriefingViewpoint(
            String viewpoint,
            String status,
            String changeType,
            String headline,
            List<String> evidenceIds) implements Serializable { }

    public record BriefingEvidence(
            String id,
            String domain,
            String kind,
            String currentValue,
            String previousValue,
            String delta,
            String displayValue,
            Map<String, String> sourceRef) implements Serializable { }
}
