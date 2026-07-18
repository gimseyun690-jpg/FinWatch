package com.finwatch.ai.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.finwatch.ai.cache.AiSummaryCacheStore;
import com.finwatch.ai.cache.AiSummaryCacheValue;
import com.finwatch.ai.domain.AiAnalysis;
import com.finwatch.ai.domain.AiUsageLog;
import com.finwatch.ai.dto.AiSummaryRequest;
import com.finwatch.ai.dto.AiSummaryResponse;
import com.finwatch.ai.provider.AiProvider;
import com.finwatch.ai.provider.AiProvider.AiProviderResult;
import com.finwatch.ai.provider.AiProviderResponseValidator;
import com.finwatch.ai.repository.AiAnalysisRepository;
import com.finwatch.ai.repository.AiUsageLogRepository;
import com.finwatch.ai.service.NewsContentSegmenter.ContentSegment;
import com.finwatch.ai.service.NewsContentSegmenter.SegmentationResult;
import com.finwatch.news.content.NewsContentException;
import com.finwatch.news.domain.NewsArticle;
import com.finwatch.news.repository.NewsArticleRepository;

@Service
@Transactional
public class AiNewsSummaryService {

    private static final String FEATURE_TYPE = "NEWS_SUMMARY";

    private final NewsArticleRepository newsArticleRepository;
    private final AiAnalysisRepository aiAnalysisRepository;
    private final AiUsageLogRepository aiUsageLogRepository;
    private final NewsTextPreprocessor preprocessor;
    private final NewsContentSegmenter segmenter;
    private final AiProvider aiProvider;
    private final AiProviderResponseValidator providerResponseValidator;
    private final AiSummaryCacheStore cacheStore;
    private final AiCostCalculator costCalculator;
    private final AiRequestGuard requestGuard;
    private final AiSingleFlight singleFlight;
    private final AiUsageLogWriter usageLogWriter;
    private final String activePromptVersion;
    private final Set<String> allowedPromptVersions;
    private final Duration cacheTtl;

    public AiNewsSummaryService(
            NewsArticleRepository newsArticleRepository,
            AiAnalysisRepository aiAnalysisRepository,
            AiUsageLogRepository aiUsageLogRepository,
            NewsTextPreprocessor preprocessor,
            NewsContentSegmenter segmenter,
            AiProvider aiProvider,
            AiProviderResponseValidator providerResponseValidator,
            AiSummaryCacheStore cacheStore,
            AiCostCalculator costCalculator,
            AiRequestGuard requestGuard,
            AiSingleFlight singleFlight,
            AiUsageLogWriter usageLogWriter,
            @Value("${app.ai.prompt-version}") String activePromptVersion,
            @Value("${app.ai.allowed-prompt-versions:}") String allowedPromptVersions,
            @Value("${app.ai.cache-ttl}") Duration cacheTtl) {
        this.newsArticleRepository = newsArticleRepository;
        this.aiAnalysisRepository = aiAnalysisRepository;
        this.aiUsageLogRepository = aiUsageLogRepository;
        this.preprocessor = preprocessor;
        this.segmenter = segmenter;
        this.aiProvider = aiProvider;
        this.providerResponseValidator = providerResponseValidator;
        this.cacheStore = cacheStore;
        this.costCalculator = costCalculator;
        this.requestGuard = requestGuard;
        this.singleFlight = singleFlight;
        this.usageLogWriter = usageLogWriter;
        this.activePromptVersion = activePromptVersion;
        LinkedHashSet<String> configuredVersions = new LinkedHashSet<>();
        configuredVersions.add(activePromptVersion);
        if (allowedPromptVersions != null && !allowedPromptVersions.isBlank()) {
            for (String version : allowedPromptVersions.split("\\s*,\\s*")) {
                if (!version.isBlank()) configuredVersions.add(version.trim());
            }
        }
        this.allowedPromptVersions = Set.copyOf(configuredVersions);
        this.cacheTtl = cacheTtl;
    }

    public AiSummaryResponse summarize(AiSummaryRequest request) {
        long startedAt = System.nanoTime();
        NewsArticle news = newsArticleRepository.findById(request.newsId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "뉴스를 찾을 수 없습니다."));
        if (!news.isAiAnalysisAllowed()) {
            throw NewsContentException.unavailable();
        }
        String contentHash = news.getContentHash();
        if (contentHash == null || contentHash.isBlank()) {
            throw NewsContentException.unavailable();
        }
        String promptVersion = normalizePromptVersion(request.promptVersion());
        String cacheKey = cacheKey(news.getId(), contentHash, promptVersion);

        Optional<AiSummaryCacheValue> cached = cacheStore.get(cacheKey);
        if (cached.isPresent()) {
            AiAnalysis analysis = aiAnalysisRepository.findById(cached.get().analysisId())
                    .orElseThrow(() -> new IllegalStateException("캐시 원본 분석을 찾을 수 없습니다."));
            return cachedResponse(cached.get(), analysis, promptVersion, elapsedMillis(startedAt));
        }

        Optional<AiAnalysis> persisted = aiAnalysisRepository
                .findByNewsIdAndFeatureTypeAndPromptVersionAndContentHash(
                        news.getId(), FEATURE_TYPE, promptVersion, contentHash);
        if (persisted.isPresent()) {
            AiSummaryCacheValue value = toCacheValue(persisted.get());
            cacheStore.put(cacheKey, value, cacheTtl);
            return cachedResponse(value, persisted.get(), promptVersion, elapsedMillis(startedAt));
        }

        return singleFlight.execute(cacheKey, () -> {
            Optional<AiSummaryCacheValue> afterWaitCache = cacheStore.get(cacheKey);
            if (afterWaitCache.isPresent()) {
                AiAnalysis analysis = aiAnalysisRepository.findById(afterWaitCache.get().analysisId())
                        .orElseThrow(() -> new IllegalStateException("캐시 원본 분석을 찾을 수 없습니다."));
                return cachedResponse(afterWaitCache.get(), analysis, promptVersion, elapsedMillis(startedAt));
            }
            Optional<AiAnalysis> afterWaitDb = aiAnalysisRepository
                    .findByNewsIdAndFeatureTypeAndPromptVersionAndContentHash(news.getId(), FEATURE_TYPE, promptVersion, contentHash);
            if (afterWaitDb.isPresent()) {
                AiSummaryCacheValue value = toCacheValue(afterWaitDb.get());
                cacheStore.put(cacheKey, value, cacheTtl);
                return cachedResponse(value, afterWaitDb.get(), promptVersion, elapsedMillis(startedAt));
            }
            return generate(news, contentHash, promptVersion, cacheKey, startedAt);
        });
    }

    private AiSummaryResponse generate(NewsArticle news, String contentHash, String promptVersion,
            String cacheKey, long startedAt) {
        String requestId = UUID.randomUUID().toString();
        try {
        String preprocessedContent = preprocessor.preprocess(news.getContent());
        if (preprocessedContent.isBlank()) {
            throw NewsContentException.unavailable();
        }
        SegmentationResult segmentation = segmenter.segment(preprocessedContent);
        if (segmentation.segments().isEmpty()) {
            throw NewsContentException.unavailable();
        }

        requestGuard.checkBudget();
        AggregatedResult aggregated = analyzeSegments(news.getTitle(), segmentation.segments(), promptVersion);
        BigDecimal estimatedCost = costCalculator.calculate(aggregated.inputTokens(), aggregated.outputTokens());
        String analysisScope = segmentation.truncated() ? "PARTIAL_PROCESSED_TEXT" : "FULL_PROCESSED_TEXT";
        int originalCharacters = news.getContent().length();
        Instant generatedAt = Instant.now();
        AiAnalysis analysis = aiAnalysisRepository.save(AiAnalysis.create(
                news,
                promptVersion,
                aggregated.modelName(),
                aggregated.summary(),
                aggregated.keyPoints(),
                aggregated.positiveFactors(),
                aggregated.riskFactors(),
                aggregated.mentionedCompanies(),
                aggregated.evidenceSegments(),
                aggregated.keywords(),
                aggregated.sentiment(),
                aggregated.inputTokens(),
                aggregated.outputTokens(),
                estimatedCost,
                cacheKey,
                contentHash,
                analysisScope,
                originalCharacters,
                segmentation.processedCharacters(),
                segmentation.segments().size(),
                generatedAt,
                generatedAt.plus(cacheTtl)));

        int responseTimeMs = elapsedMillis(startedAt);
        aiUsageLogRepository.save(AiUsageLog.success(
                requestId,
                analysis,
                news.getId(),
                aggregated.inputTokens(),
                aggregated.outputTokens(),
                estimatedCost,
                BigDecimal.ZERO.setScale(8),
                false,
                responseTimeMs,
                promptVersion));

        AiSummaryCacheValue cacheValue = toCacheValue(analysis);
        cacheStore.put(cacheKey, cacheValue, cacheTtl);
        return response(analysis, false, aggregated.inputTokens(), aggregated.outputTokens(),
                estimatedCost, responseTimeMs);
        } catch (RuntimeException exception) {
            usageLogWriter.saveFailure(AiUsageLog.failure(requestId, FEATURE_TYPE, "NEWS", news.getId(),
                    aiProvider.getClass().getSimpleName(), elapsedMillis(startedAt), promptVersion,
                    exception instanceof com.finwatch.ai.provider.AiProviderException providerException
                            ? providerException.getCode() : "AI_NEWS_SUMMARY_FAILED"));
            throw exception;
        }
    }

    private AggregatedResult analyzeSegments(
            String title,
            List<ContentSegment> segments,
            String promptVersion) {
        List<AiProviderResult> results = new ArrayList<>();
        for (ContentSegment segment : segments) {
            AiProviderResult result = aiProvider.summarize(title, segment.content(), segment.id(), promptVersion);
            results.add(providerResponseValidator.validate(result));
        }

        return new AggregatedResult(
                results.getFirst().modelName(),
                joinSummaries(results),
                collect(results, AiProviderResult::keyPoints, 5),
                collect(results, AiProviderResult::positiveFactors, 5),
                collect(results, AiProviderResult::riskFactors, 5),
                collect(results, AiProviderResult::mentionedCompanies, 8),
                segments.stream().map(ContentSegment::id).toList(),
                collect(results, AiProviderResult::keywords, 8),
                aggregateSentiment(results),
                results.stream().mapToInt(AiProviderResult::inputTokens).sum(),
                results.stream().mapToInt(AiProviderResult::outputTokens).sum());
    }

    private String joinSummaries(List<AiProviderResult> results) {
        String joined = String.join(" ", results.stream()
                .map(AiProviderResult::summary)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList());
        return joined.length() <= 2_000 ? joined : joined.substring(0, 2_000).trim();
    }

    private List<String> collect(
            List<AiProviderResult> results,
            java.util.function.Function<AiProviderResult, List<String>> extractor,
            int limit) {
        Set<String> values = new LinkedHashSet<>();
        for (AiProviderResult result : results) {
            List<String> items = extractor.apply(result);
            if (items == null) {
                continue;
            }
            for (String item : items) {
                if (item != null && !item.isBlank()) {
                    values.add(item.trim());
                }
                if (values.size() >= limit) {
                    return List.copyOf(values);
                }
            }
        }
        return List.copyOf(values);
    }

    private String aggregateSentiment(List<AiProviderResult> results) {
        long positive = results.stream().filter(result -> "POSITIVE".equals(result.sentiment())).count();
        long negative = results.stream().filter(result -> "NEGATIVE".equals(result.sentiment())).count();
        if (positive > negative && positive >= results.size() / 2.0) {
            return "POSITIVE";
        }
        if (negative > positive && negative >= results.size() / 2.0) {
            return "NEGATIVE";
        }
        return "NEUTRAL";
    }

    private AiSummaryResponse cachedResponse(
            AiSummaryCacheValue value,
            AiAnalysis analysis,
            String promptVersion,
            int responseTimeMs) {
        aiUsageLogRepository.save(AiUsageLog.success(
                UUID.randomUUID().toString(),
                analysis,
                value.newsId(),
                0,
                0,
                BigDecimal.ZERO.setScale(8),
                value.originalEstimatedCost(),
                true,
                responseTimeMs,
                promptVersion));
        return new AiSummaryResponse(
                value.analysisId(),
                value.newsId(),
                value.symbol(),
                value.summary(),
                safeList(value.keyPoints()),
                safeList(value.positiveFactors()),
                safeList(value.riskFactors()),
                safeList(value.mentionedCompanies()),
                safeList(value.evidenceSegments()),
                safeList(value.keywords()),
                value.sentiment(),
                value.modelName(),
                value.promptVersion(),
                value.analysisScope(),
                value.originalCharacters(),
                value.processedCharacters(),
                Math.max(1, value.providerCallCount()),
                true,
                0,
                0,
                BigDecimal.ZERO.setScale(8),
                "USD",
                responseTimeMs,
                value.generatedAt());
    }

    private AiSummaryResponse response(
            AiAnalysis analysis,
            boolean cacheHit,
            int inputTokens,
            int outputTokens,
            BigDecimal estimatedCost,
            int responseTimeMs) {
        return new AiSummaryResponse(
                analysis.getId(),
                analysis.getNews().getId(),
                analysis.getNews().getStock().getSymbol(),
                analysis.getSummary(),
                analysis.getKeyPoints(),
                analysis.getPositiveFactors(),
                analysis.getRiskFactors(),
                analysis.getMentionedCompanies(),
                analysis.getEvidenceSegments(),
                analysis.getKeywords(),
                analysis.getSentiment(),
                analysis.getModelName(),
                analysis.getPromptVersion(),
                analysis.getAnalysisScope(),
                analysis.getOriginalCharacters(),
                analysis.getProcessedCharacters(),
                analysis.getProviderCallCount(),
                cacheHit,
                inputTokens,
                outputTokens,
                estimatedCost,
                "USD",
                responseTimeMs,
                analysis.getGeneratedAt());
    }

    private AiSummaryCacheValue toCacheValue(AiAnalysis analysis) {
        return new AiSummaryCacheValue(
                analysis.getId(),
                analysis.getNews().getId(),
                analysis.getNews().getStock().getSymbol(),
                analysis.getSummary(),
                analysis.getKeyPoints(),
                analysis.getPositiveFactors(),
                analysis.getRiskFactors(),
                analysis.getMentionedCompanies(),
                analysis.getEvidenceSegments(),
                analysis.getKeywords(),
                analysis.getSentiment(),
                analysis.getModelName(),
                analysis.getPromptVersion(),
                analysis.getAnalysisScope(),
                analysis.getOriginalCharacters(),
                analysis.getProcessedCharacters(),
                analysis.getProviderCallCount(),
                analysis.getEstimatedCost(),
                analysis.getGeneratedAt());
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }

    private String normalizePromptVersion(String requestedVersion) {
        if (requestedVersion == null || requestedVersion.isBlank()) {
            return activePromptVersion;
        }
        String normalized = requestedVersion.trim();
        if (!allowedPromptVersions.contains(normalized)) {
            throw new com.finwatch.ai.provider.AiProviderException(
                    HttpStatus.BAD_REQUEST,
                    "AI_PROMPT_VERSION_UNSUPPORTED",
                    "지원하지 않는 프롬프트 버전입니다.");
        }
        return normalized;
    }

    private String cacheKey(Long newsId, String contentHash, String promptVersion) {
        return "ai:news-summary:" + newsId + ":" + contentHash + ":" + promptVersion;
    }

    private int elapsedMillis(long startedAt) {
        return (int) Math.min(Integer.MAX_VALUE, (System.nanoTime() - startedAt) / 1_000_000L);
    }

    private record AggregatedResult(
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
            int outputTokens) {
    }
}
