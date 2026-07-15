package com.finwatch.ai.provider;

import java.util.List;

import com.finwatch.ai.dto.TechnicalExplanationInput;
import com.finwatch.ai.dto.DailyChangeBriefingInput;

public interface AiProvider {

    AiProviderResult summarize(
            String title,
            String preprocessedContent,
            String segmentId,
            String promptVersion);

    TechnicalExplanationResult explainTechnical(
            TechnicalExplanationInput input,
            String promptVersion);

    DailyBriefingResult generateDailyBriefing(
            DailyChangeBriefingInput input,
            String promptVersion);

    record AiProviderResult(
            String modelName,
            String summary,
            List<String> keyPoints,
            List<String> positiveFactors,
            List<String> riskFactors,
            List<String> mentionedCompanies,
            List<String> keywords,
            String sentiment,
            int inputTokens,
            int outputTokens) {
    }

    record TechnicalSignalExplanation(String text, List<String> evidenceIds) {
    }

    record TechnicalExplanationResult(
            String modelName,
            String summary,
            String trendExplanation,
            String momentumExplanation,
            String volatilityExplanation,
            String volumeExplanation,
            List<TechnicalSignalExplanation> supportingSignals,
            List<TechnicalSignalExplanation> conflictingSignals,
            List<String> riskNotes,
            List<String> dataLimitations,
            int inputTokens,
            int outputTokens) {
    }

    record DailyBriefingStatement(String text, List<String> evidenceIds) { }

    record DailyBriefingResult(
            String modelName,
            String headline,
            List<String> headlineEvidenceIds,
            String changeSummary,
            List<String> changeSummaryEvidenceIds,
            List<DailyBriefingStatement> newStrengths,
            List<DailyBriefingStatement> newRisks,
            List<DailyBriefingStatement> unchangedContext,
            List<DailyBriefingStatement> alignedViews,
            List<DailyBriefingStatement> conflictingViews,
            List<String> dataLimitations,
            int inputTokens,
            int outputTokens) { }
}
