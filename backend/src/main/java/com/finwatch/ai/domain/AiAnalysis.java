package com.finwatch.ai.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import com.finwatch.news.domain.NewsArticle;

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
@Table(name = "ai_analyses")
public class AiAnalysis {

    private static final String LIST_SEPARATOR = "\u001F";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "news_id", nullable = false)
    private NewsArticle news;

    @Column(name = "feature_type", nullable = false, length = 40)
    private String featureType;

    @Column(name = "prompt_version", nullable = false, length = 50)
    private String promptVersion;

    @Column(name = "model_name", nullable = false, length = 100)
    private String modelName;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(name = "key_points", nullable = false, columnDefinition = "TEXT")
    private String keyPoints;

    @Column(name = "positive_factors", nullable = false, columnDefinition = "TEXT")
    private String positiveFactors;

    @Column(name = "risk_factors", nullable = false, columnDefinition = "TEXT")
    private String riskFactors;

    @Column(name = "mentioned_companies", nullable = false, columnDefinition = "TEXT")
    private String mentionedCompanies;

    @Column(name = "evidence_segments", nullable = false, columnDefinition = "TEXT")
    private String evidenceSegments;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String keywords;

    @Column(nullable = false, length = 20)
    private String sentiment;

    @Column(name = "input_tokens", nullable = false)
    private int inputTokens;

    @Column(name = "output_tokens", nullable = false)
    private int outputTokens;

    @Column(name = "estimated_cost", nullable = false, precision = 16, scale = 8)
    private BigDecimal estimatedCost;

    @Column(name = "cache_key", nullable = false, length = 255)
    private String cacheKey;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "analysis_scope", nullable = false, length = 30)
    private String analysisScope;

    @Column(name = "original_characters", nullable = false)
    private int originalCharacters;

    @Column(name = "processed_characters", nullable = false)
    private int processedCharacters;

    @Column(name = "provider_call_count", nullable = false)
    private int providerCallCount;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    protected AiAnalysis() {
    }

    public static AiAnalysis create(
            NewsArticle news,
            String promptVersion,
            String modelName,
            String summary,
            List<String> keyPoints,
            List<String> positiveFactors,
            List<String> riskFactors,
            List<String> mentionedCompanies,
            List<String> evidenceSegments,
            List<String> keywords,
            String sentiment,
            int inputTokens,
            int outputTokens,
            BigDecimal estimatedCost,
            String cacheKey,
            String contentHash,
            String analysisScope,
            int originalCharacters,
            int processedCharacters,
            int providerCallCount,
            Instant generatedAt,
            Instant expiresAt) {
        AiAnalysis analysis = new AiAnalysis();
        analysis.news = news;
        analysis.featureType = "NEWS_SUMMARY";
        analysis.promptVersion = promptVersion;
        analysis.modelName = modelName;
        analysis.summary = summary;
        analysis.keyPoints = String.join(LIST_SEPARATOR, keyPoints);
        analysis.positiveFactors = String.join(LIST_SEPARATOR, positiveFactors);
        analysis.riskFactors = String.join(LIST_SEPARATOR, riskFactors);
        analysis.mentionedCompanies = String.join(LIST_SEPARATOR, mentionedCompanies);
        analysis.evidenceSegments = String.join(LIST_SEPARATOR, evidenceSegments);
        analysis.keywords = String.join(LIST_SEPARATOR, keywords);
        analysis.sentiment = sentiment;
        analysis.inputTokens = inputTokens;
        analysis.outputTokens = outputTokens;
        analysis.estimatedCost = estimatedCost;
        analysis.cacheKey = cacheKey;
        analysis.contentHash = contentHash;
        analysis.analysisScope = analysisScope;
        analysis.originalCharacters = originalCharacters;
        analysis.processedCharacters = processedCharacters;
        analysis.providerCallCount = providerCallCount;
        analysis.generatedAt = generatedAt;
        analysis.expiresAt = expiresAt;
        return analysis;
    }

    public Long getId() {
        return id;
    }

    public NewsArticle getNews() {
        return news;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public String getModelName() {
        return modelName;
    }

    public String getSummary() {
        return summary;
    }

    public List<String> getKeyPoints() {
        return split(keyPoints);
    }

    public List<String> getKeywords() {
        return split(keywords);
    }

    public List<String> getPositiveFactors() {
        return split(positiveFactors);
    }

    public List<String> getRiskFactors() {
        return split(riskFactors);
    }

    public List<String> getMentionedCompanies() {
        return split(mentionedCompanies);
    }

    public List<String> getEvidenceSegments() {
        return split(evidenceSegments);
    }

    public String getSentiment() {
        return sentiment;
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

    public String getCacheKey() {
        return cacheKey;
    }

    public String getContentHash() {
        return contentHash;
    }

    public String getAnalysisScope() {
        return analysisScope;
    }

    public int getOriginalCharacters() {
        return originalCharacters;
    }

    public int getProcessedCharacters() {
        return processedCharacters;
    }

    public int getProviderCallCount() {
        return providerCallCount;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    private List<String> split(String value) {
        return value == null || value.isBlank() ? List.of() : Arrays.asList(value.split(LIST_SEPARATOR, -1));
    }
}
