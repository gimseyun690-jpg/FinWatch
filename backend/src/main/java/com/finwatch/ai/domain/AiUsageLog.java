package com.finwatch.ai.domain;

import java.math.BigDecimal;
import java.time.Instant;

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
@Table(name = "ai_usage_logs")
public class AiUsageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false, length = 64)
    private String requestId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analysis_id")
    private AiAnalysis analysis;

    @Column(name = "feature_type", nullable = false, length = 40)
    private String featureType;

    @Column(name = "target_type", nullable = false, length = 40)
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "model_name", length = 100)
    private String modelName;

    @Column(name = "input_tokens", nullable = false)
    private int inputTokens;

    @Column(name = "output_tokens", nullable = false)
    private int outputTokens;

    @Column(name = "estimated_cost", nullable = false, precision = 16, scale = 8)
    private BigDecimal estimatedCost;

    @Column(name = "saved_estimated_cost", nullable = false, precision = 16, scale = 8)
    private BigDecimal savedEstimatedCost;

    @Column(name = "cache_hit", nullable = false)
    private boolean cacheHit;

    @Column(name = "response_time_ms", nullable = false)
    private int responseTimeMs;

    @Column(name = "prompt_version", nullable = false, length = 50)
    private String promptVersion;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AiUsageLog() {
    }

    public static AiUsageLog success(
            String requestId,
            AiAnalysis analysis,
            Long newsId,
            int inputTokens,
            int outputTokens,
            BigDecimal estimatedCost,
            BigDecimal savedEstimatedCost,
            boolean cacheHit,
            int responseTimeMs,
            String promptVersion) {
        AiUsageLog log = new AiUsageLog();
        log.requestId = requestId;
        log.analysis = analysis;
        log.featureType = "NEWS_SUMMARY";
        log.targetType = "NEWS";
        log.targetId = newsId;
        log.modelName = analysis.getModelName();
        log.inputTokens = inputTokens;
        log.outputTokens = outputTokens;
        log.estimatedCost = estimatedCost;
        log.savedEstimatedCost = savedEstimatedCost;
        log.cacheHit = cacheHit;
        log.responseTimeMs = responseTimeMs;
        log.promptVersion = promptVersion;
        log.status = "SUCCESS";
        log.createdAt = Instant.now();
        return log;
    }

    public Long getId() {
        return id;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getFeatureType() {
        return featureType;
    }

    public String getTargetType() {
        return targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public String getModelName() {
        return modelName;
    }

    public int getInputTokens() {
        return inputTokens;
    }

    public int getOutputTokens() {
        return outputTokens;
    }

    public BigDecimal getEstimatedCost() {
        return estimatedCost;
    }

    public BigDecimal getSavedEstimatedCost() {
        return savedEstimatedCost;
    }

    public boolean isCacheHit() {
        return cacheHit;
    }

    public int getResponseTimeMs() {
        return responseTimeMs;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
