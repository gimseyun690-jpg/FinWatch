package com.finwatch.ai.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.finwatch.ai.dto.DailyChangeBriefingInput.BriefingEvidence;
import com.finwatch.ai.dto.DailyChangeBriefingInput.BriefingViewpoint;

public record DailyChangeBriefingResponse(
        Long briefingId,
        String symbol,
        String market,
        LocalDate currentTradingDate,
        LocalDate previousTradingDate,
        String baselineStatus,
        String relation,
        String headline,
        List<String> headlineEvidenceIds,
        String changeSummary,
        List<String> changeSummaryEvidenceIds,
        List<BriefingViewpoint> viewpoints,
        List<BriefingStatement> newStrengths,
        List<BriefingStatement> newRisks,
        List<BriefingStatement> unchangedContext,
        List<BriefingStatement> alignedViews,
        List<BriefingStatement> conflictingViews,
        List<String> dataLimitations,
        List<BriefingEvidence> evidence,
        Audit audit,
        boolean staleBriefing,
        String disclaimer) implements Serializable {

    public record BriefingStatement(String text, List<String> evidenceIds) implements Serializable { }

    public record Audit(
            Map<String, List<String>> sources,
            Instant latestRecordedAt,
            String calculationVersion,
            String briefingInputVersion,
            String promptVersion,
            String modelName,
            int evidenceCount,
            int excludedContentCount,
            boolean cacheHit,
            int inputTokens,
            int outputTokens,
            BigDecimal estimatedCost,
            BigDecimal savedEstimatedCost,
            String costCurrency,
            int responseTimeMs,
            Instant generatedAt) implements Serializable { }
}
