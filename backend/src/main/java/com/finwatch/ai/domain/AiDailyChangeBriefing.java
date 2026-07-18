package com.finwatch.ai.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.finwatch.stock.domain.Stock;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "ai_daily_change_briefings")
public class AiDailyChangeBriefing {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "stock_id", nullable = false) private Stock stock;
    @Column(nullable = false, length = 30) private String market;
    @Column(name = "current_trading_date", nullable = false) private LocalDate currentTradingDate;
    @Column(name = "previous_trading_date") private LocalDate previousTradingDate;
    @Column(name = "baseline_status", nullable = false, length = 30) private String baselineStatus;
    @Column(name = "latest_recorded_at", nullable = false) private Instant latestRecordedAt;
    @Column(name = "calculation_version", nullable = false, length = 80) private String calculationVersion;
    @Column(name = "briefing_input_version", nullable = false, length = 80) private String briefingInputVersion;
    @Column(name = "prompt_version", nullable = false, length = 80) private String promptVersion;
    @Column(name = "input_hash", nullable = false, length = 64) private String inputHash;
    @Column(nullable = false, length = 30) private String relation;
    @Column(nullable = false, length = 120) private String headline;
    @Column(name = "change_summary", nullable = false, columnDefinition = "TEXT") private String changeSummary;
    @Column(name = "headline_evidence_ids", nullable = false, columnDefinition = "TEXT") private String headlineEvidenceIds;
    @Column(name = "change_summary_evidence_ids", nullable = false, columnDefinition = "TEXT") private String changeSummaryEvidenceIds;
    @Column(nullable = false, columnDefinition = "TEXT") private String viewpoints;
    @Column(name = "new_strengths", nullable = false, columnDefinition = "TEXT") private String newStrengths;
    @Column(name = "new_risks", nullable = false, columnDefinition = "TEXT") private String newRisks;
    @Column(name = "unchanged_context", nullable = false, columnDefinition = "TEXT") private String unchangedContext;
    @Column(name = "aligned_views", nullable = false, columnDefinition = "TEXT") private String alignedViews;
    @Column(name = "conflicting_views", nullable = false, columnDefinition = "TEXT") private String conflictingViews;
    @Column(name = "data_limitations", nullable = false, columnDefinition = "TEXT") private String dataLimitations;
    @Column(nullable = false, columnDefinition = "TEXT") private String evidence;
    @Column(name = "source_summary", nullable = false, columnDefinition = "TEXT") private String sourceSummary;
    @Column(name = "evidence_count", nullable = false) private int evidenceCount;
    @Column(name = "excluded_content_count", nullable = false) private int excludedContentCount;
    @Column(name = "model_name", nullable = false, length = 100) private String modelName;
    @Column(name = "input_tokens", nullable = false) private int inputTokens;
    @Column(name = "output_tokens", nullable = false) private int outputTokens;
    @Column(name = "estimated_cost", nullable = false, precision = 16, scale = 8) private BigDecimal estimatedCost;
    @Column(name = "cache_key", nullable = false, length = 500) private String cacheKey;
    @Column(name = "generated_at", nullable = false) private Instant generatedAt;

    protected AiDailyChangeBriefing() { }

    public static AiDailyChangeBriefing create(Stock stock, LocalDate currentDate, LocalDate previousDate,
            String baselineStatus, Instant latestRecordedAt, String calculationVersion, String inputVersion,
            String promptVersion, String inputHash, String relation, String headline, String changeSummary,
            String headlineEvidenceIds, String changeSummaryEvidenceIds, String viewpoints, String newStrengths,
            String newRisks, String unchangedContext, String alignedViews, String conflictingViews,
            String dataLimitations, String evidence, String sourceSummary, int evidenceCount,
            int excludedContentCount, String modelName, int inputTokens, int outputTokens,
            BigDecimal estimatedCost, String cacheKey, Instant generatedAt) {
        AiDailyChangeBriefing value = new AiDailyChangeBriefing();
        value.stock = stock; value.market = stock.getMarket(); value.currentTradingDate = currentDate;
        value.previousTradingDate = previousDate; value.baselineStatus = baselineStatus;
        value.latestRecordedAt = latestRecordedAt; value.calculationVersion = calculationVersion;
        value.briefingInputVersion = inputVersion; value.promptVersion = promptVersion; value.inputHash = inputHash;
        value.relation = relation; value.headline = headline; value.changeSummary = changeSummary;
        value.headlineEvidenceIds = headlineEvidenceIds; value.changeSummaryEvidenceIds = changeSummaryEvidenceIds;
        value.viewpoints = viewpoints; value.newStrengths = newStrengths; value.newRisks = newRisks;
        value.unchangedContext = unchangedContext; value.alignedViews = alignedViews;
        value.conflictingViews = conflictingViews; value.dataLimitations = dataLimitations; value.evidence = evidence;
        value.sourceSummary = sourceSummary; value.evidenceCount = evidenceCount;
        value.excludedContentCount = excludedContentCount; value.modelName = modelName;
        value.inputTokens = inputTokens; value.outputTokens = outputTokens; value.estimatedCost = estimatedCost;
        value.cacheKey = cacheKey; value.generatedAt = generatedAt; return value;
    }

    public Long getId() { return id; } public Stock getStock() { return stock; } public String getMarket() { return market; }
    public LocalDate getCurrentTradingDate() { return currentTradingDate; } public LocalDate getPreviousTradingDate() { return previousTradingDate; }
    public String getBaselineStatus() { return baselineStatus; } public Instant getLatestRecordedAt() { return latestRecordedAt; }
    public String getCalculationVersion() { return calculationVersion; } public String getBriefingInputVersion() { return briefingInputVersion; }
    public String getPromptVersion() { return promptVersion; } public String getInputHash() { return inputHash; }
    public String getRelation() { return relation; } public String getHeadline() { return headline; } public String getChangeSummary() { return changeSummary; }
    public String getHeadlineEvidenceIds() { return headlineEvidenceIds; } public String getChangeSummaryEvidenceIds() { return changeSummaryEvidenceIds; }
    public String getViewpoints() { return viewpoints; } public String getNewStrengths() { return newStrengths; } public String getNewRisks() { return newRisks; }
    public String getUnchangedContext() { return unchangedContext; } public String getAlignedViews() { return alignedViews; }
    public String getConflictingViews() { return conflictingViews; } public String getDataLimitations() { return dataLimitations; }
    public String getEvidence() { return evidence; } public String getSourceSummary() { return sourceSummary; }
    public int getEvidenceCount() { return evidenceCount; } public int getExcludedContentCount() { return excludedContentCount; }
    public String getModelName() { return modelName; } public int getInputTokens() { return inputTokens; }
    public int getOutputTokens() { return outputTokens; } public BigDecimal getEstimatedCost() { return estimatedCost; }
    public String getCacheKey() { return cacheKey; } public Instant getGeneratedAt() { return generatedAt; }
}
