package com.finwatch.ai.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.finwatch.ai.cache.TechnicalExplanationCacheStore;
import com.finwatch.ai.cache.TechnicalExplanationCacheValue;
import com.finwatch.ai.domain.AiTechnicalExplanation;
import com.finwatch.ai.domain.AiUsageLog;
import com.finwatch.ai.dto.TechnicalExplanationInput.TechnicalEvidence;
import com.finwatch.ai.dto.TechnicalExplanationRequest;
import com.finwatch.ai.dto.TechnicalExplanationResponse;
import com.finwatch.ai.dto.TechnicalExplanationResponse.SignalExplanation;
import com.finwatch.ai.provider.AiProvider;
import com.finwatch.ai.provider.AiProvider.TechnicalExplanationResult;
import com.finwatch.ai.provider.AiProviderException;
import com.finwatch.ai.provider.TechnicalExplanationResponseValidator;
import com.finwatch.ai.repository.AiTechnicalExplanationRepository;
import com.finwatch.ai.repository.AiUsageLogRepository;
import com.finwatch.ai.service.TechnicalExplanationSnapshotFactory.SnapshotBundle;

import tools.jackson.databind.ObjectMapper;

@Service
public class AiTechnicalExplanationService {

    private static final String FEATURE_TYPE = "TECHNICAL_EXPLANATION";
    private static final String DISCLAIMER = "AI 해설과 기술적 신호는 투자 권유가 아닌 참고 정보입니다.";

    private final TechnicalExplanationSnapshotFactory snapshotFactory;
    private final AiProvider aiProvider;
    private final TechnicalExplanationResponseValidator validator;
    private final AiTechnicalExplanationRepository explanationRepository;
    private final AiUsageLogRepository usageLogRepository;
    private final AiUsageLogWriter usageLogWriter;
    private final TechnicalExplanationCacheStore cacheStore;
    private final AiCostCalculator costCalculator;
    private final AiSingleFlight singleFlight;
    private final AiRequestGuard requestGuard;
    private final ObjectMapper objectMapper;
    private final String activePromptVersion;
    private final Set<String> allowedPromptVersions;
    private final Duration cacheTtl;

    public AiTechnicalExplanationService(
            TechnicalExplanationSnapshotFactory snapshotFactory,
            AiProvider aiProvider,
            TechnicalExplanationResponseValidator validator,
            AiTechnicalExplanationRepository explanationRepository,
            AiUsageLogRepository usageLogRepository,
            AiUsageLogWriter usageLogWriter,
            TechnicalExplanationCacheStore cacheStore,
            AiCostCalculator costCalculator,
            AiSingleFlight singleFlight,
            AiRequestGuard requestGuard,
            ObjectMapper objectMapper,
            @Value("${app.ai.technical-prompt-version:technical-explanation-v1}") String activePromptVersion,
            @Value("${app.ai.technical-allowed-prompt-versions:}") String allowedPromptVersions,
            @Value("${app.ai.technical-cache-ttl:24h}") Duration cacheTtl) {
        this.snapshotFactory = snapshotFactory;
        this.aiProvider = aiProvider;
        this.validator = validator;
        this.explanationRepository = explanationRepository;
        this.usageLogRepository = usageLogRepository;
        this.usageLogWriter = usageLogWriter;
        this.cacheStore = cacheStore;
        this.costCalculator = costCalculator;
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

    public TechnicalExplanationResponse explain(TechnicalExplanationRequest request) {
        long startedAt = System.nanoTime();
        String promptVersion = normalizePromptVersion(request.promptVersion());
        SnapshotBundle snapshot = snapshotFactory.create(request.market(), request.symbol(), request.interval());
        String cacheKey = cacheKey(snapshot, promptVersion);

        Optional<TechnicalExplanationResponse> existing = findExisting(cacheKey, startedAt, promptVersion);
        if (existing.isPresent()) return existing.get();

        return singleFlight.execute(cacheKey, () -> {
            Optional<TechnicalExplanationResponse> afterWait = findExisting(cacheKey, startedAt, promptVersion);
            if (afterWait.isPresent()) return afterWait.get();
            return generate(snapshot, cacheKey, promptVersion, startedAt);
        });
    }

    private Optional<TechnicalExplanationResponse> findExisting(
            String cacheKey,
            long startedAt,
            String promptVersion) {
        Optional<TechnicalExplanationCacheValue> cached = cacheStore.get(cacheKey);
        if (cached.isPresent()) {
            TechnicalExplanationResponse original = cached.get().originalResponse();
            AiTechnicalExplanation entity = explanationRepository.findById(original.analysisId()).orElse(null);
            int elapsed = elapsedMillis(startedAt);
            recordHit(entity, original, promptVersion, elapsed);
            return Optional.of(hitResponse(original, elapsed));
        }

        Optional<AiTechnicalExplanation> persisted = explanationRepository.findByCacheKey(cacheKey);
        if (persisted.isEmpty()) return Optional.empty();
        TechnicalExplanationResponse original = toOriginalResponse(persisted.get(), 0);
        cacheStore.put(cacheKey, new TechnicalExplanationCacheValue(original), cacheTtl);
        int elapsed = elapsedMillis(startedAt);
        recordHit(persisted.get(), original, promptVersion, elapsed);
        return Optional.of(hitResponse(original, elapsed));
    }

    private TechnicalExplanationResponse generate(
            SnapshotBundle snapshot,
            String cacheKey,
            String promptVersion,
            long startedAt) {
        String requestId = UUID.randomUUID().toString();
        try {
            requestGuard.checkBudget();
            TechnicalExplanationResult providerResult = validator.validate(
                    aiProvider.explainTechnical(snapshot.input(), promptVersion),
                    snapshot.input());
            BigDecimal estimatedCost = costCalculator.calculate(
                    providerResult.inputTokens(), providerResult.outputTokens());
            Instant generatedAt = Instant.now();
            List<SignalExplanation> supportingSignals = providerResult.supportingSignals().stream()
                    .map(item -> new SignalExplanation(item.text(), item.evidenceIds()))
                    .toList();
            List<SignalExplanation> conflictingSignals = providerResult.conflictingSignals().stream()
                    .map(item -> new SignalExplanation(item.text(), item.evidenceIds()))
                    .toList();
            AiTechnicalExplanation entity = explanationRepository.save(AiTechnicalExplanation.create(
                    snapshot.stock(),
                    snapshot.input().interval(),
                    snapshot.input().latestRecordedAt(),
                    snapshot.input().calculationVersion(),
                    promptVersion,
                    snapshot.inputHash(),
                    snapshot.input().source(),
                    snapshot.input().freshness(),
                    snapshot.input().summarySignal(),
                    providerResult.summary(),
                    providerResult.trendExplanation(),
                    providerResult.momentumExplanation(),
                    providerResult.volatilityExplanation(),
                    providerResult.volumeExplanation(),
                    json(supportingSignals),
                    json(conflictingSignals),
                    json(providerResult.riskNotes()),
                    json(providerResult.dataLimitations()),
                    json(snapshot.input().evidence()),
                    providerResult.modelName(),
                    providerResult.inputTokens(),
                    providerResult.outputTokens(),
                    estimatedCost,
                    cacheKey,
                    generatedAt));
            int elapsed = elapsedMillis(startedAt);
            usageLogRepository.save(AiUsageLog.technicalSuccess(
                    requestId,
                    entity,
                    providerResult.modelName(),
                    snapshot.stock().getId(),
                    providerResult.inputTokens(),
                    providerResult.outputTokens(),
                    estimatedCost,
                    BigDecimal.ZERO.setScale(8),
                    false,
                    elapsed,
                    promptVersion));
            TechnicalExplanationResponse response = response(
                    entity,
                    supportingSignals,
                    conflictingSignals,
                    providerResult.riskNotes(),
                    providerResult.dataLimitations(),
                    snapshot.input().evidence(),
                    false,
                    providerResult.inputTokens(),
                    providerResult.outputTokens(),
                    estimatedCost,
                    elapsed);
            cacheStore.put(cacheKey, new TechnicalExplanationCacheValue(response), cacheTtl);
            return response;
        } catch (RuntimeException exception) {
            usageLogWriter.saveFailure(AiUsageLog.failure(
                    requestId,
                    FEATURE_TYPE,
                    "STOCK",
                    snapshot.stock().getId(),
                    aiProvider.getClass().getSimpleName(),
                    elapsedMillis(startedAt),
                    promptVersion,
                    errorCode(exception)));
            throw exception;
        }
    }

    private void recordHit(
            AiTechnicalExplanation entity,
            TechnicalExplanationResponse original,
            String promptVersion,
            int elapsed) {
        usageLogRepository.save(AiUsageLog.technicalSuccess(
                UUID.randomUUID().toString(),
                entity,
                original.modelName(),
                entity == null ? 0L : entity.getStock().getId(),
                0,
                0,
                BigDecimal.ZERO.setScale(8),
                original.estimatedCost(),
                true,
                elapsed,
                promptVersion));
    }

    private TechnicalExplanationResponse toOriginalResponse(AiTechnicalExplanation entity, int responseTimeMs) {
        return response(
                entity,
                read(entity.getSupportingSignals(), SignalExplanation[].class),
                read(entity.getConflictingSignals(), SignalExplanation[].class),
                read(entity.getRiskNotes(), String[].class),
                read(entity.getDataLimitations(), String[].class),
                read(entity.getEvidence(), TechnicalEvidence[].class),
                false,
                entity.getInputTokens(),
                entity.getOutputTokens(),
                entity.getEstimatedCost(),
                responseTimeMs);
    }

    private TechnicalExplanationResponse response(
            AiTechnicalExplanation entity,
            List<SignalExplanation> supportingSignals,
            List<SignalExplanation> conflictingSignals,
            List<String> riskNotes,
            List<String> dataLimitations,
            List<TechnicalEvidence> evidence,
            boolean cacheHit,
            int inputTokens,
            int outputTokens,
            BigDecimal estimatedCost,
            int responseTimeMs) {
        return new TechnicalExplanationResponse(
                entity.getId(),
                entity.getStock().getSymbol(),
                entity.getStock().getMarket(),
                entity.getInterval(),
                entity.getLatestRecordedAt(),
                entity.getSource(),
                entity.getFreshness(),
                entity.getCalculationVersion(),
                entity.getPromptVersion(),
                entity.getInputHash(),
                entity.getSummarySignal(),
                entity.getSummary(),
                entity.getTrendExplanation(),
                entity.getMomentumExplanation(),
                entity.getVolatilityExplanation(),
                entity.getVolumeExplanation(),
                supportingSignals,
                conflictingSignals,
                riskNotes,
                dataLimitations,
                evidence,
                entity.getModelName(),
                cacheHit,
                inputTokens,
                outputTokens,
                estimatedCost,
                "USD",
                responseTimeMs,
                entity.getGeneratedAt(),
                DISCLAIMER);
    }

    private TechnicalExplanationResponse hitResponse(TechnicalExplanationResponse original, int responseTimeMs) {
        return new TechnicalExplanationResponse(
                original.analysisId(), original.symbol(), original.market(), original.interval(),
                original.latestRecordedAt(), original.source(), original.freshness(),
                original.calculationVersion(), original.promptVersion(), original.inputHash(),
                original.summarySignal(), original.summary(), original.trendExplanation(),
                original.momentumExplanation(), original.volatilityExplanation(), original.volumeExplanation(),
                original.supportingSignals(), original.conflictingSignals(), original.riskNotes(),
                original.dataLimitations(), original.evidence(), original.modelName(), true, 0, 0,
                BigDecimal.ZERO.setScale(8), original.costCurrency(), responseTimeMs,
                original.generatedAt(), original.disclaimer());
    }

    private String normalizePromptVersion(String requested) {
        String version = requested == null || requested.isBlank() ? activePromptVersion : requested.trim();
        if (!allowedPromptVersions.contains(version)) {
            throw new AiProviderException(
                    HttpStatus.BAD_REQUEST,
                    "AI_PROMPT_VERSION_UNSUPPORTED",
                    "지원하지 않는 기술지표 해설 프롬프트 버전입니다.");
        }
        return version;
    }

    private String cacheKey(SnapshotBundle snapshot, String promptVersion) {
        var input = snapshot.input();
        return "ai:technical-explanation:" + input.market() + ":" + input.symbol() + ":"
                + input.interval() + ":" + input.latestRecordedAt() + ":" + input.calculationVersion()
                + ":" + snapshot.inputHash() + ":" + promptVersion;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("AI 기술지표 결과를 저장 형식으로 변환할 수 없습니다.", exception);
        }
    }

    private <T> List<T> read(String value, Class<T[]> arrayType) {
        try {
            return List.of(objectMapper.readValue(value, arrayType));
        } catch (RuntimeException exception) {
            throw new IllegalStateException("저장된 AI 기술지표 결과를 읽을 수 없습니다.", exception);
        }
    }

    private String errorCode(RuntimeException exception) {
        if (exception instanceof AiProviderException providerException) return providerException.getCode();
        if (exception instanceof TechnicalExplanationException technicalException) return technicalException.getCode();
        return "AI_TECHNICAL_EXPLANATION_FAILED";
    }

    private int elapsedMillis(long startedAt) {
        return (int) Math.min(Integer.MAX_VALUE, (System.nanoTime() - startedAt) / 1_000_000L);
    }
}
