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

import com.finwatch.ai.cache.PortfolioEvaluationCacheStore;
import com.finwatch.ai.cache.PortfolioEvaluationCacheValue;
import com.finwatch.ai.domain.AiPortfolioEvaluation;
import com.finwatch.ai.domain.AiUsageLog;
import com.finwatch.ai.dto.PortfolioEvaluationInput.Evidence;
import com.finwatch.ai.dto.PortfolioEvaluationRequest;
import com.finwatch.ai.dto.PortfolioEvaluationResponse;
import com.finwatch.ai.dto.PortfolioEvaluationResponse.Audit;
import com.finwatch.ai.dto.PortfolioEvaluationResponse.Statement;
import com.finwatch.ai.provider.AiProvider;
import com.finwatch.ai.provider.AiProvider.PortfolioEvaluationResult;
import com.finwatch.ai.provider.AiProvider.PortfolioEvaluationStatement;
import com.finwatch.ai.provider.AiProviderException;
import com.finwatch.ai.repository.AiPortfolioEvaluationRepository;
import com.finwatch.ai.repository.AiUsageLogRepository;
import com.finwatch.ai.service.PortfolioEvaluationResultResolver.ResolvedPortfolioEvaluation;
import com.finwatch.ai.service.PortfolioEvaluationSnapshotFactory.SnapshotBundle;

import tools.jackson.databind.ObjectMapper;

@Service
public class AiPortfolioEvaluationService {

    private static final String FEATURE_TYPE = "PORTFOLIO_EVALUATION";
    private static final String DISCLAIMER =
            "AI 평가는 투자 권유가 아닌 포트폴리오 구성 점검용 참고 정보입니다.";

    private final PortfolioEvaluationSnapshotFactory snapshotFactory;
    private final AiProvider provider;
    private final PortfolioEvaluationResultResolver resultResolver;
    private final AiPortfolioEvaluationRepository repository;
    private final AiUsageLogRepository usageLogs;
    private final AiUsageLogWriter usageLogWriter;
    private final PortfolioEvaluationCacheStore cache;
    private final AiCostCalculator costs;
    private final AiSingleFlight singleFlight;
    private final AiRequestGuard requestGuard;
    private final ObjectMapper objectMapper;
    private final String activePromptVersion;
    private final Set<String> allowedPromptVersions;
    private final Duration cacheTtl;

    public AiPortfolioEvaluationService(
            PortfolioEvaluationSnapshotFactory snapshotFactory,
            AiProvider provider,
            PortfolioEvaluationResultResolver resultResolver,
            AiPortfolioEvaluationRepository repository,
            AiUsageLogRepository usageLogs,
            AiUsageLogWriter usageLogWriter,
            PortfolioEvaluationCacheStore cache,
            AiCostCalculator costs,
            AiSingleFlight singleFlight,
            AiRequestGuard requestGuard,
            ObjectMapper objectMapper,
            @Value("${app.ai.portfolio-prompt-version:portfolio-evaluation-v2-grounded}") String activePromptVersion,
            @Value("${app.ai.portfolio-allowed-prompt-versions:}") String allowedPromptVersions,
            @Value("${app.ai.portfolio-cache-ttl:15m}") Duration cacheTtl) {
        this.snapshotFactory = snapshotFactory;
        this.provider = provider;
        this.resultResolver = resultResolver;
        this.repository = repository;
        this.usageLogs = usageLogs;
        this.usageLogWriter = usageLogWriter;
        this.cache = cache;
        this.costs = costs;
        this.singleFlight = singleFlight;
        this.requestGuard = requestGuard;
        this.objectMapper = objectMapper;
        this.activePromptVersion = activePromptVersion;
        LinkedHashSet<String> versions = new LinkedHashSet<>();
        versions.add(activePromptVersion);
        if (allowedPromptVersions != null && !allowedPromptVersions.isBlank()) {
            for (String version : allowedPromptVersions.split("\\s*,\\s*")) {
                if (!version.isBlank()) versions.add(version.trim());
            }
        }
        this.allowedPromptVersions = Set.copyOf(versions);
        this.cacheTtl = cacheTtl;
    }

    public PortfolioEvaluationResponse evaluate(Long userId, PortfolioEvaluationRequest request) {
        long started = System.nanoTime();
        String promptVersion = normalizePromptVersion(request.promptVersion());
        SnapshotBundle snapshot = snapshotFactory.create(userId);
        String key = cacheKey(userId, snapshot, promptVersion);
        Optional<PortfolioEvaluationResponse> existing =
                findExisting(userId, key, started, promptVersion);
        if (existing.isPresent()) return existing.get();
        return singleFlight.execute(key, () -> findExisting(userId, key, started, promptVersion)
                .orElseGet(() -> generate(userId, snapshot, key, promptVersion, started)));
    }

    private Optional<PortfolioEvaluationResponse> findExisting(
            Long userId,
            String key,
            long started,
            String promptVersion) {
        Optional<PortfolioEvaluationCacheValue> cached = cache.get(key);
        if (cached.isPresent()) {
            PortfolioEvaluationResponse original = cached.get().originalResponse();
            AiPortfolioEvaluation entity = repository.findById(original.evaluationId()).orElse(null);
            int elapsed = elapsed(started);
            recordHit(userId, entity, original, promptVersion, elapsed);
            return Optional.of(hit(original, elapsed));
        }
        Optional<AiPortfolioEvaluation> persisted = repository.findByCacheKey(key);
        if (persisted.isEmpty()) return Optional.empty();
        PortfolioEvaluationResponse original = original(persisted.get(), 0);
        cache.put(key, new PortfolioEvaluationCacheValue(original), cacheTtl);
        int elapsed = elapsed(started);
        recordHit(userId, persisted.get(), original, promptVersion, elapsed);
        return Optional.of(hit(original, elapsed));
    }

    private PortfolioEvaluationResponse generate(
            Long userId,
            SnapshotBundle snapshot,
            String key,
            String promptVersion,
            long started) {
        String requestId = UUID.randomUUID().toString();
        try {
            requestGuard.checkBudget();
            PortfolioEvaluationResult providerResult =
                    provider.evaluatePortfolio(snapshot.input(), promptVersion);
            ResolvedPortfolioEvaluation resolved = resultResolver.resolve(
                    providerResult,
                    snapshot.input(),
                    provider.getClass().getSimpleName());
            PortfolioEvaluationResult result = resolved.result();
            String persistedCacheKey = resolved.fallbackUsed()
                    ? key + ":fallback:" + requestId
                    : key;
            BigDecimal estimatedCost = costs.calculate(
                    result.inputTokens(),
                    result.outputTokens());
            Instant generatedAt = Instant.now();
            Statement diversification = statement(result.diversification());
            Statement concentration = statement(result.concentration());
            Statement currencyExposure = statement(result.currencyExposure());
            Statement performanceContext = statement(result.performanceContext());
            List<Statement> strengths = statements(result.strengths());
            List<Statement> riskFactors = statements(result.riskFactors());
            List<Statement> reviewPoints = statements(result.reviewPoints());
            List<String> limitations = mergeLimitations(
                    snapshot.input().serverDataLimitations(),
                    result.dataLimitations());
            AiPortfolioEvaluation generated = AiPortfolioEvaluation.create(
                    snapshot.user(),
                    snapshot.input().snapshotAt(),
                    snapshot.input().windowStartedAt(),
                    snapshot.inputHash(),
                    snapshot.positionsHash(),
                    promptVersion,
                    snapshot.input().concentrationBand(),
                    result.headline(),
                    result.summary(),
                    json(diversification),
                    json(concentration),
                    json(currencyExposure),
                    json(performanceContext),
                    json(strengths),
                    json(riskFactors),
                    json(reviewPoints),
                    json(limitations),
                    json(snapshot.input().evidence()),
                    result.modelName(),
                    result.inputTokens(),
                    result.outputTokens(),
                    estimatedCost,
                    persistedCacheKey,
                    generatedAt);
            AiPortfolioEvaluation entity = repository
                    .findByUser_IdAndPositionsHashAndWindowStartedAtAndPromptVersion(
                            userId,
                            snapshot.positionsHash(),
                            snapshot.input().windowStartedAt(),
                            promptVersion)
                    .map(existing -> {
                        existing.replaceGeneratedResult(generated);
                        return repository.save(existing);
                    })
                    .orElseGet(() -> repository.save(generated));
            int elapsed = elapsed(started);
            usageLogs.save(AiUsageLog.portfolioEvaluationSuccess(
                    requestId,
                    entity,
                    result.modelName(),
                    userId,
                    result.inputTokens(),
                    result.outputTokens(),
                    estimatedCost,
                    zeroCost(),
                    false,
                    elapsed,
                    promptVersion));
            PortfolioEvaluationResponse response = response(
                    entity,
                    diversification,
                    concentration,
                    currencyExposure,
                    performanceContext,
                    strengths,
                    riskFactors,
                    reviewPoints,
                    limitations,
                    snapshot.input().evidence(),
                    false,
                    result.inputTokens(),
                    result.outputTokens(),
                    estimatedCost,
                    zeroCost(),
                    elapsed);
            if (!resolved.fallbackUsed()) {
                cache.put(key, new PortfolioEvaluationCacheValue(response), cacheTtl);
            }
            return response;
        } catch (RuntimeException exception) {
            usageLogWriter.saveFailure(AiUsageLog.failure(
                    requestId,
                    FEATURE_TYPE,
                    "PORTFOLIO",
                    userId,
                    provider.getClass().getSimpleName(),
                    elapsed(started),
                    promptVersion,
                    errorCode(exception)));
            throw exception;
        }
    }

    private void recordHit(
            Long userId,
            AiPortfolioEvaluation entity,
            PortfolioEvaluationResponse original,
            String promptVersion,
            int elapsed) {
        usageLogs.save(AiUsageLog.portfolioEvaluationSuccess(
                UUID.randomUUID().toString(),
                entity,
                original.audit().modelName(),
                userId,
                0,
                0,
                zeroCost(),
                original.audit().estimatedCost(),
                true,
                elapsed,
                promptVersion));
    }

    private PortfolioEvaluationResponse original(AiPortfolioEvaluation entity, int responseTimeMs) {
        return response(
                entity,
                readOne(entity.getDiversification(), Statement.class),
                readOne(entity.getConcentration(), Statement.class),
                readOne(entity.getCurrencyExposure(), Statement.class),
                readOne(entity.getPerformanceContext(), Statement.class),
                readList(entity.getStrengths(), Statement[].class),
                readList(entity.getRiskFactors(), Statement[].class),
                readList(entity.getReviewPoints(), Statement[].class),
                readList(entity.getDataLimitations(), String[].class),
                readList(entity.getEvidence(), Evidence[].class),
                false,
                entity.getInputTokens(),
                entity.getOutputTokens(),
                entity.getEstimatedCost(),
                zeroCost(),
                responseTimeMs);
    }

    private PortfolioEvaluationResponse response(
            AiPortfolioEvaluation entity,
            Statement diversification,
            Statement concentration,
            Statement currencyExposure,
            Statement performanceContext,
            List<Statement> strengths,
            List<Statement> riskFactors,
            List<Statement> reviewPoints,
            List<String> dataLimitations,
            List<Evidence> evidence,
            boolean cacheHit,
            int inputTokens,
            int outputTokens,
            BigDecimal estimatedCost,
            BigDecimal savedEstimatedCost,
            int responseTimeMs) {
        return new PortfolioEvaluationResponse(
                entity.getId(),
                entity.getSnapshotAt(),
                "KRW",
                entity.getBalanceStatus(),
                entity.getHeadline(),
                entity.getSummary(),
                diversification,
                concentration,
                currencyExposure,
                performanceContext,
                strengths,
                riskFactors,
                reviewPoints,
                dataLimitations,
                evidence,
                new Audit(
                        entity.getInputHash(),
                        entity.getPositionsHash(),
                        entity.getPromptVersion(),
                        entity.getModelName(),
                        cacheHit,
                        inputTokens,
                        outputTokens,
                        estimatedCost,
                        savedEstimatedCost,
                        "USD",
                        responseTimeMs,
                        entity.getGeneratedAt()),
                DISCLAIMER);
    }

    private PortfolioEvaluationResponse hit(
            PortfolioEvaluationResponse original,
            int responseTimeMs) {
        Audit audit = original.audit();
        return new PortfolioEvaluationResponse(
                original.evaluationId(),
                original.snapshotAt(),
                original.baseCurrency(),
                original.balanceStatus(),
                original.headline(),
                original.summary(),
                original.diversification(),
                original.concentration(),
                original.currencyExposure(),
                original.performanceContext(),
                original.strengths(),
                original.riskFactors(),
                original.reviewPoints(),
                original.dataLimitations(),
                original.evidence(),
                new Audit(
                        audit.inputHash(),
                        audit.positionsHash(),
                        audit.promptVersion(),
                        audit.modelName(),
                        true,
                        0,
                        0,
                        zeroCost(),
                        audit.estimatedCost(),
                        audit.costCurrency(),
                        responseTimeMs,
                        audit.generatedAt()),
                original.disclaimer());
    }

    private Statement statement(PortfolioEvaluationStatement value) {
        return new Statement(value.text(), value.evidenceIds());
    }

    private List<Statement> statements(List<PortfolioEvaluationStatement> values) {
        return values.stream().map(this::statement).toList();
    }

    private List<String> mergeLimitations(List<String> server, List<String> providerValues) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (server != null) values.addAll(server);
        if (providerValues != null) values.addAll(providerValues);
        return new ArrayList<>(values).stream().limit(8).toList();
    }

    private String cacheKey(Long userId, SnapshotBundle snapshot, String promptVersion) {
        return "ai:portfolio-evaluation:" + userId + ":" + snapshot.positionsHash() + ":"
                + snapshot.input().windowStartedAt() + ":" + promptVersion;
    }

    private String normalizePromptVersion(String requested) {
        String version = requested == null || requested.isBlank()
                ? activePromptVersion
                : requested.trim();
        if (!allowedPromptVersions.contains(version)) {
            throw new AiProviderException(
                    HttpStatus.BAD_REQUEST,
                    "AI_PROMPT_VERSION_UNSUPPORTED",
                    "지원하지 않는 포트폴리오 평가 프롬프트 버전입니다.");
        }
        return version;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("포트폴리오 AI 결과를 저장 형식으로 변환할 수 없습니다.", exception);
        }
    }

    private <T> T readOne(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("저장된 포트폴리오 AI 결과를 읽을 수 없습니다.", exception);
        }
    }

    private <T> List<T> readList(String value, Class<T[]> arrayType) {
        try {
            return List.of(objectMapper.readValue(value, arrayType));
        } catch (RuntimeException exception) {
            throw new IllegalStateException("저장된 포트폴리오 AI 목록을 읽을 수 없습니다.", exception);
        }
    }

    private String errorCode(RuntimeException exception) {
        if (exception instanceof AiProviderException value) return value.getCode();
        if (exception instanceof PortfolioEvaluationException value) return value.getCode();
        return "AI_PORTFOLIO_EVALUATION_FAILED";
    }

    private BigDecimal zeroCost() {
        return BigDecimal.ZERO.setScale(8);
    }

    private int elapsed(long started) {
        return (int) Math.min(
                Integer.MAX_VALUE,
                (System.nanoTime() - started) / 1_000_000L);
    }
}
