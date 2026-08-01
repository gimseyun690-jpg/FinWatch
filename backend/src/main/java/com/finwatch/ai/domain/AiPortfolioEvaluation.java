package com.finwatch.ai.domain;

import java.math.BigDecimal;
import java.time.Instant;

import com.finwatch.user.domain.AppUser;

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
@Table(name = "ai_portfolio_evaluations")
public class AiPortfolioEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "snapshot_at", nullable = false)
    private Instant snapshotAt;

    @Column(name = "window_started_at", nullable = false)
    private Instant windowStartedAt;

    @Column(name = "input_hash", nullable = false, length = 64)
    private String inputHash;

    @Column(name = "positions_hash", nullable = false, length = 64)
    private String positionsHash;

    @Column(name = "prompt_version", nullable = false, length = 80)
    private String promptVersion;

    @Column(name = "balance_status", nullable = false, length = 40)
    private String balanceStatus;

    @Column(nullable = false, length = 180)
    private String headline;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String diversification;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String concentration;

    @Column(name = "currency_exposure", nullable = false, columnDefinition = "TEXT")
    private String currencyExposure;

    @Column(name = "performance_context", nullable = false, columnDefinition = "TEXT")
    private String performanceContext;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String strengths;

    @Column(name = "risk_factors", nullable = false, columnDefinition = "TEXT")
    private String riskFactors;

    @Column(name = "review_points", nullable = false, columnDefinition = "TEXT")
    private String reviewPoints;

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

    protected AiPortfolioEvaluation() {
    }

    public static AiPortfolioEvaluation create(
            AppUser user,
            Instant snapshotAt,
            Instant windowStartedAt,
            String inputHash,
            String positionsHash,
            String promptVersion,
            String balanceStatus,
            String headline,
            String summary,
            String diversification,
            String concentration,
            String currencyExposure,
            String performanceContext,
            String strengths,
            String riskFactors,
            String reviewPoints,
            String dataLimitations,
            String evidence,
            String modelName,
            int inputTokens,
            int outputTokens,
            BigDecimal estimatedCost,
            String cacheKey,
            Instant generatedAt) {
        AiPortfolioEvaluation value = new AiPortfolioEvaluation();
        value.user = user;
        value.snapshotAt = snapshotAt;
        value.windowStartedAt = windowStartedAt;
        value.inputHash = inputHash;
        value.positionsHash = positionsHash;
        value.promptVersion = promptVersion;
        value.balanceStatus = balanceStatus;
        value.headline = headline;
        value.summary = summary;
        value.diversification = diversification;
        value.concentration = concentration;
        value.currencyExposure = currencyExposure;
        value.performanceContext = performanceContext;
        value.strengths = strengths;
        value.riskFactors = riskFactors;
        value.reviewPoints = reviewPoints;
        value.dataLimitations = dataLimitations;
        value.evidence = evidence;
        value.modelName = modelName;
        value.inputTokens = inputTokens;
        value.outputTokens = outputTokens;
        value.estimatedCost = estimatedCost;
        value.cacheKey = cacheKey;
        value.generatedAt = generatedAt;
        return value;
    }

    public void replaceGeneratedResult(AiPortfolioEvaluation value) {
        if (!positionsHash.equals(value.positionsHash)
                || !windowStartedAt.equals(value.windowStartedAt)
                || !promptVersion.equals(value.promptVersion)
                || !user.getId().equals(value.user.getId())) {
            throw new IllegalArgumentException("같은 포트폴리오 평가 창의 결과만 교체할 수 있습니다.");
        }
        snapshotAt = value.snapshotAt;
        inputHash = value.inputHash;
        balanceStatus = value.balanceStatus;
        headline = value.headline;
        summary = value.summary;
        diversification = value.diversification;
        concentration = value.concentration;
        currencyExposure = value.currencyExposure;
        performanceContext = value.performanceContext;
        strengths = value.strengths;
        riskFactors = value.riskFactors;
        reviewPoints = value.reviewPoints;
        dataLimitations = value.dataLimitations;
        evidence = value.evidence;
        modelName = value.modelName;
        inputTokens = value.inputTokens;
        outputTokens = value.outputTokens;
        estimatedCost = value.estimatedCost;
        cacheKey = value.cacheKey;
        generatedAt = value.generatedAt;
    }

    public Long getId() { return id; }
    public AppUser getUser() { return user; }
    public Instant getSnapshotAt() { return snapshotAt; }
    public Instant getWindowStartedAt() { return windowStartedAt; }
    public String getInputHash() { return inputHash; }
    public String getPositionsHash() { return positionsHash; }
    public String getPromptVersion() { return promptVersion; }
    public String getBalanceStatus() { return balanceStatus; }
    public String getHeadline() { return headline; }
    public String getSummary() { return summary; }
    public String getDiversification() { return diversification; }
    public String getConcentration() { return concentration; }
    public String getCurrencyExposure() { return currencyExposure; }
    public String getPerformanceContext() { return performanceContext; }
    public String getStrengths() { return strengths; }
    public String getRiskFactors() { return riskFactors; }
    public String getReviewPoints() { return reviewPoints; }
    public String getDataLimitations() { return dataLimitations; }
    public String getEvidence() { return evidence; }
    public String getModelName() { return modelName; }
    public int getInputTokens() { return inputTokens; }
    public int getOutputTokens() { return outputTokens; }
    public BigDecimal getEstimatedCost() { return estimatedCost; }
    public String getCacheKey() { return cacheKey; }
    public Instant getGeneratedAt() { return generatedAt; }
}
