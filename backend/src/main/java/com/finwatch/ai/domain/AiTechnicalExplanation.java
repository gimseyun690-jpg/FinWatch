package com.finwatch.ai.domain;

import java.math.BigDecimal;
import java.time.Instant;

import com.finwatch.stock.domain.Stock;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "ai_technical_explanations")
public class AiTechnicalExplanation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Column(name = "analysis_interval", nullable = false, length = 10)
    private String interval;

    @Column(name = "latest_recorded_at", nullable = false)
    private Instant latestRecordedAt;

    @Column(name = "calculation_version", nullable = false, length = 80)
    private String calculationVersion;

    @Column(name = "prompt_version", nullable = false, length = 50)
    private String promptVersion;

    @Column(name = "input_hash", nullable = false, length = 64)
    private String inputHash;

    @Column(nullable = false, length = 50)
    private String source;

    @Column(nullable = false, length = 20)
    private String freshness;

    @Column(name = "summary_signal", nullable = false, length = 20)
    private String summarySignal;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(name = "trend_explanation", nullable = false, columnDefinition = "TEXT")
    private String trendExplanation;

    @Column(name = "momentum_explanation", nullable = false, columnDefinition = "TEXT")
    private String momentumExplanation;

    @Column(name = "volatility_explanation", nullable = false, columnDefinition = "TEXT")
    private String volatilityExplanation;

    @Column(name = "volume_explanation", nullable = false, columnDefinition = "TEXT")
    private String volumeExplanation;

    @Column(name = "supporting_signals", nullable = false, columnDefinition = "TEXT")
    private String supportingSignals;

    @Column(name = "conflicting_signals", nullable = false, columnDefinition = "TEXT")
    private String conflictingSignals;

    @Column(name = "risk_notes", nullable = false, columnDefinition = "TEXT")
    private String riskNotes;

    @Column(name = "data_limitations", nullable = false, columnDefinition = "TEXT")
    private String dataLimitations;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String evidence;

    @Column(name = "model_name", nullable = false, length = 100)
    private String modelName;

    @Column(name = "input_tokens", nullable = false)
    private int inputTokens;

    @Column(name = "output_tokens", nullable = false)
    private int outputTokens;

    @Column(name = "estimated_cost", nullable = false, precision = 16, scale = 8)
    private BigDecimal estimatedCost;

    @Column(name = "cache_key", nullable = false, length = 500)
    private String cacheKey;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    protected AiTechnicalExplanation() {
    }

    public static AiTechnicalExplanation create(
            Stock stock,
            String interval,
            Instant latestRecordedAt,
            String calculationVersion,
            String promptVersion,
            String inputHash,
            String source,
            String freshness,
            String summarySignal,
            String summary,
            String trendExplanation,
            String momentumExplanation,
            String volatilityExplanation,
            String volumeExplanation,
            String supportingSignals,
            String conflictingSignals,
            String riskNotes,
            String dataLimitations,
            String evidence,
            String modelName,
            int inputTokens,
            int outputTokens,
            BigDecimal estimatedCost,
            String cacheKey,
            Instant generatedAt) {
        AiTechnicalExplanation explanation = new AiTechnicalExplanation();
        explanation.stock = stock;
        explanation.interval = interval;
        explanation.latestRecordedAt = latestRecordedAt;
        explanation.calculationVersion = calculationVersion;
        explanation.promptVersion = promptVersion;
        explanation.inputHash = inputHash;
        explanation.source = source;
        explanation.freshness = freshness;
        explanation.summarySignal = summarySignal;
        explanation.summary = summary;
        explanation.trendExplanation = trendExplanation;
        explanation.momentumExplanation = momentumExplanation;
        explanation.volatilityExplanation = volatilityExplanation;
        explanation.volumeExplanation = volumeExplanation;
        explanation.supportingSignals = supportingSignals;
        explanation.conflictingSignals = conflictingSignals;
        explanation.riskNotes = riskNotes;
        explanation.dataLimitations = dataLimitations;
        explanation.evidence = evidence;
        explanation.modelName = modelName;
        explanation.inputTokens = inputTokens;
        explanation.outputTokens = outputTokens;
        explanation.estimatedCost = estimatedCost;
        explanation.cacheKey = cacheKey;
        explanation.generatedAt = generatedAt;
        return explanation;
    }

    public Long getId() { return id; }
    public Stock getStock() { return stock; }
    public String getInterval() { return interval; }
    public Instant getLatestRecordedAt() { return latestRecordedAt; }
    public String getCalculationVersion() { return calculationVersion; }
    public String getPromptVersion() { return promptVersion; }
    public String getInputHash() { return inputHash; }
    public String getSource() { return source; }
    public String getFreshness() { return freshness; }
    public String getSummarySignal() { return summarySignal; }
    public String getSummary() { return summary; }
    public String getTrendExplanation() { return trendExplanation; }
    public String getMomentumExplanation() { return momentumExplanation; }
    public String getVolatilityExplanation() { return volatilityExplanation; }
    public String getVolumeExplanation() { return volumeExplanation; }
    public String getSupportingSignals() { return supportingSignals; }
    public String getConflictingSignals() { return conflictingSignals; }
    public String getRiskNotes() { return riskNotes; }
    public String getDataLimitations() { return dataLimitations; }
    public String getEvidence() { return evidence; }
    public String getModelName() { return modelName; }
    public int getInputTokens() { return inputTokens; }
    public int getOutputTokens() { return outputTokens; }
    public BigDecimal getEstimatedCost() { return estimatedCost; }
    public String getCacheKey() { return cacheKey; }
    public Instant getGeneratedAt() { return generatedAt; }
}
