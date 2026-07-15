package com.finwatch.ai.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.finwatch.ai.cache.DailyBriefingCacheStore;
import com.finwatch.ai.cache.DailyBriefingCacheValue;
import com.finwatch.ai.domain.AiDailyChangeBriefing;
import com.finwatch.ai.domain.AiUsageLog;
import com.finwatch.ai.dto.DailyChangeBriefingInput.BriefingEvidence;
import com.finwatch.ai.dto.DailyChangeBriefingInput.BriefingViewpoint;
import com.finwatch.ai.dto.DailyChangeBriefingRequest;
import com.finwatch.ai.dto.DailyChangeBriefingResponse;
import com.finwatch.ai.dto.DailyChangeBriefingResponse.Audit;
import com.finwatch.ai.dto.DailyChangeBriefingResponse.BriefingStatement;
import com.finwatch.ai.provider.AiProvider;
import com.finwatch.ai.provider.AiProvider.DailyBriefingResult;
import com.finwatch.ai.provider.AiProviderException;
import com.finwatch.ai.provider.DailyBriefingResponseValidator;
import com.finwatch.ai.repository.AiDailyChangeBriefingRepository;
import com.finwatch.ai.repository.AiUsageLogRepository;
import com.finwatch.ai.service.DailyBriefingSnapshotFactory.SnapshotBundle;

import tools.jackson.databind.ObjectMapper;

@Service
public class AiDailyChangeBriefingService {
    private static final String FEATURE = "DAILY_CHANGE_BRIEFING";
    private static final String DISCLAIMER = "AI 브리핑은 투자 권유가 아닌 정보 정리 결과입니다.";

    private final DailyBriefingSnapshotFactory snapshots;
    private final AiProvider provider;
    private final DailyBriefingResponseValidator validator;
    private final AiDailyChangeBriefingRepository repository;
    private final AiUsageLogRepository usageLogs;
    private final AiUsageLogWriter usageLogWriter;
    private final DailyBriefingCacheStore cache;
    private final AiCostCalculator costs;
    private final AiSingleFlight singleFlight;
    private final AiRequestGuard requestGuard;
    private final ObjectMapper objectMapper;
    private final String activePromptVersion;
    private final Set<String> allowedPromptVersions;
    private final Duration cacheTtl;

    public AiDailyChangeBriefingService(DailyBriefingSnapshotFactory snapshots, AiProvider provider,
            DailyBriefingResponseValidator validator, AiDailyChangeBriefingRepository repository,
            AiUsageLogRepository usageLogs, AiUsageLogWriter usageLogWriter, DailyBriefingCacheStore cache,
            AiCostCalculator costs, AiSingleFlight singleFlight, AiRequestGuard requestGuard, ObjectMapper objectMapper,
            @Value("${app.ai.daily-briefing-prompt-version:daily-change-briefing-v1}") String activePromptVersion,
            @Value("${app.ai.daily-briefing-allowed-prompt-versions:}") String allowedPromptVersions,
            @Value("${app.ai.daily-briefing-cache-ttl:24h}") Duration cacheTtl) {
        this.snapshots = snapshots; this.provider = provider; this.validator = validator; this.repository = repository;
        this.usageLogs = usageLogs; this.usageLogWriter = usageLogWriter; this.cache = cache; this.costs = costs;
        this.singleFlight = singleFlight; this.requestGuard = requestGuard; this.objectMapper = objectMapper; this.activePromptVersion = activePromptVersion;
        LinkedHashSet<String> versions = new LinkedHashSet<>(); versions.add(activePromptVersion);
        if (allowedPromptVersions != null && !allowedPromptVersions.isBlank())
            Arrays.stream(allowedPromptVersions.split("\\s*,\\s*")).filter(value -> !value.isBlank()).forEach(versions::add);
        this.allowedPromptVersions = Set.copyOf(versions); this.cacheTtl = cacheTtl;
    }

    public DailyChangeBriefingResponse generate(DailyChangeBriefingRequest request) {
        long started = System.nanoTime();
        String promptVersion = promptVersion(request.promptVersion());
        SnapshotBundle snapshot = snapshots.create(request.market(), request.symbol());
        String key = cacheKey(snapshot, promptVersion);
        Optional<DailyChangeBriefingResponse> existing = findExisting(key, started, promptVersion);
        if (existing.isPresent()) return existing.get();
        return singleFlight.execute(key, () -> findExisting(key, started, promptVersion)
                .orElseGet(() -> generate(snapshot, key, promptVersion, started)));
    }

    @Transactional(readOnly = true)
    public DailyChangeBriefingResponse latest(String market, String symbol) {
        SnapshotBundle current = snapshots.create(market, symbol);
        AiDailyChangeBriefing entity = repository.findFirstByStockIdOrderByGeneratedAtDesc(current.stock().getId())
                .orElseThrow(() -> new DailyBriefingException(HttpStatus.NOT_FOUND, "DAILY_BRIEFING_NOT_FOUND", "저장된 일일 변화 브리핑이 없습니다."));
        return response(entity, false, entity.getInputTokens(), entity.getOutputTokens(), entity.getEstimatedCost(),
                BigDecimal.ZERO.setScale(8), 0, !entity.getInputHash().equals(current.inputHash()));
    }

    private Optional<DailyChangeBriefingResponse> findExisting(String key, long started, String promptVersion) {
        Optional<DailyBriefingCacheValue> cached = cache.get(key);
        if (cached.isPresent()) {
            DailyChangeBriefingResponse original = cached.get().originalResponse();
            AiDailyChangeBriefing entity = repository.findById(original.briefingId()).orElse(null);
            int elapsed = elapsed(started); recordHit(entity, original, promptVersion, elapsed);
            return Optional.of(hit(original, elapsed));
        }
        Optional<AiDailyChangeBriefing> persisted = repository.findByCacheKey(key);
        if (persisted.isEmpty()) return Optional.empty();
        DailyChangeBriefingResponse original = response(persisted.get(), false, persisted.get().getInputTokens(),
                persisted.get().getOutputTokens(), persisted.get().getEstimatedCost(), BigDecimal.ZERO.setScale(8), 0, false);
        cache.put(key, new DailyBriefingCacheValue(original), cacheTtl);
        int elapsed = elapsed(started); recordHit(persisted.get(), original, promptVersion, elapsed);
        return Optional.of(hit(original, elapsed));
    }

    private DailyChangeBriefingResponse generate(SnapshotBundle snapshot, String key, String promptVersion, long started) {
        String requestId = UUID.randomUUID().toString();
        try {
            requestGuard.checkBudget();
            DailyBriefingResult result = validator.validate(provider.generateDailyBriefing(snapshot.input(), promptVersion), snapshot.input());
            BigDecimal cost = costs.calculate(result.inputTokens(), result.outputTokens());
            List<BriefingStatement> strengths = statements(result.newStrengths());
            List<BriefingStatement> risks = statements(result.newRisks());
            List<BriefingStatement> unchanged = statements(result.unchangedContext());
            List<BriefingStatement> aligned = statements(result.alignedViews());
            List<BriefingStatement> conflicting = statements(result.conflictingViews());
            List<String> limitations = StreamSupport.merge(snapshot.input().serverDataLimitations(), result.dataLimitations());
            Map<String, List<String>> sources = sources(snapshot.input().evidence());
            Instant generatedAt = Instant.now();
            AiDailyChangeBriefing entity = repository.save(AiDailyChangeBriefing.create(snapshot.stock(),
                    snapshot.input().currentTradingDate(), snapshot.input().previousTradingDate(), snapshot.input().baselineStatus(),
                    snapshot.input().latestRecordedAt(), snapshot.input().calculationVersion(), snapshot.input().briefingInputVersion(),
                    promptVersion, snapshot.inputHash(), snapshot.input().relation(), result.headline(), result.changeSummary(),
                    json(result.headlineEvidenceIds()), json(result.changeSummaryEvidenceIds()), json(snapshot.input().viewpoints()),
                    json(strengths), json(risks), json(unchanged), json(aligned), json(conflicting), json(limitations),
                    json(snapshot.input().evidence()), json(sources), snapshot.input().evidence().size(),
                    snapshot.input().excludedContentCount(), result.modelName(), result.inputTokens(), result.outputTokens(),
                    cost, key, generatedAt));
            int elapsed = elapsed(started);
            usageLogs.save(AiUsageLog.dailyBriefingSuccess(requestId, entity, result.modelName(), snapshot.stock().getId(),
                    result.inputTokens(), result.outputTokens(), cost, BigDecimal.ZERO.setScale(8), false, elapsed, promptVersion));
            DailyChangeBriefingResponse response = response(entity, false, result.inputTokens(), result.outputTokens(),
                    cost, BigDecimal.ZERO.setScale(8), elapsed, false);
            cache.put(key, new DailyBriefingCacheValue(response), cacheTtl);
            return response;
        } catch (RuntimeException exception) {
            usageLogWriter.saveFailure(AiUsageLog.failure(requestId, FEATURE, "STOCK", snapshot.stock().getId(),
                    provider.getClass().getSimpleName(), elapsed(started), promptVersion, errorCode(exception)));
            throw exception;
        }
    }

    private DailyChangeBriefingResponse response(AiDailyChangeBriefing entity, boolean cacheHit,
            int inputTokens, int outputTokens, BigDecimal estimatedCost, BigDecimal savedCost,
            int responseTime, boolean stale) {
        return new DailyChangeBriefingResponse(entity.getId(), entity.getStock().getSymbol(), entity.getMarket(),
                entity.getCurrentTradingDate(), entity.getPreviousTradingDate(), entity.getBaselineStatus(), entity.getRelation(),
                entity.getHeadline(), read(entity.getHeadlineEvidenceIds(), String[].class), entity.getChangeSummary(),
                read(entity.getChangeSummaryEvidenceIds(), String[].class), read(entity.getViewpoints(), BriefingViewpoint[].class),
                read(entity.getNewStrengths(), BriefingStatement[].class), read(entity.getNewRisks(), BriefingStatement[].class),
                read(entity.getUnchangedContext(), BriefingStatement[].class), read(entity.getAlignedViews(), BriefingStatement[].class),
                read(entity.getConflictingViews(), BriefingStatement[].class), read(entity.getDataLimitations(), String[].class),
                read(entity.getEvidence(), BriefingEvidence[].class),
                new Audit(readMap(entity.getSourceSummary()), entity.getLatestRecordedAt(), entity.getCalculationVersion(),
                        entity.getBriefingInputVersion(), entity.getPromptVersion(), entity.getModelName(), entity.getEvidenceCount(),
                        entity.getExcludedContentCount(), cacheHit, inputTokens, outputTokens, estimatedCost, savedCost, "USD",
                        responseTime, entity.getGeneratedAt()), stale, DISCLAIMER);
    }

    private DailyChangeBriefingResponse hit(DailyChangeBriefingResponse original, int elapsed) {
        Audit audit = original.audit();
        return new DailyChangeBriefingResponse(original.briefingId(), original.symbol(), original.market(), original.currentTradingDate(),
                original.previousTradingDate(), original.baselineStatus(), original.relation(), original.headline(), original.headlineEvidenceIds(),
                original.changeSummary(), original.changeSummaryEvidenceIds(), original.viewpoints(), original.newStrengths(), original.newRisks(),
                original.unchangedContext(), original.alignedViews(), original.conflictingViews(), original.dataLimitations(), original.evidence(),
                new Audit(audit.sources(), audit.latestRecordedAt(), audit.calculationVersion(), audit.briefingInputVersion(), audit.promptVersion(),
                        audit.modelName(), audit.evidenceCount(), audit.excludedContentCount(), true, 0, 0, BigDecimal.ZERO.setScale(8),
                        original.audit().estimatedCost(), "USD", elapsed, audit.generatedAt()), false, original.disclaimer());
    }

    private void recordHit(AiDailyChangeBriefing entity, DailyChangeBriefingResponse original, String promptVersion, int elapsed) {
        usageLogs.save(AiUsageLog.dailyBriefingSuccess(UUID.randomUUID().toString(), entity, original.audit().modelName(),
                entity == null ? 0L : entity.getStock().getId(), 0, 0, BigDecimal.ZERO.setScale(8),
                original.audit().estimatedCost(), true, elapsed, promptVersion));
    }
    private List<BriefingStatement> statements(List<AiProvider.DailyBriefingStatement> values) { return values.stream().map(value -> new BriefingStatement(value.text(), value.evidenceIds())).toList(); }
    private Map<String, List<String>> sources(List<BriefingEvidence> evidence) { return evidence.stream().collect(Collectors.groupingBy(BriefingEvidence::domain, java.util.LinkedHashMap::new,
            Collectors.mapping(item -> item.sourceRef().getOrDefault("source", item.sourceRef().getOrDefault("type", "UNKNOWN")), Collectors.collectingAndThen(Collectors.toCollection(LinkedHashSet::new), List::copyOf)))); }
    private String cacheKey(SnapshotBundle snapshot, String promptVersion) { var input = snapshot.input(); return "ai:daily-change-briefing:" + input.market() + ":" + input.symbol() + ":" + input.currentTradingDate() + ":" + snapshot.inputHash() + ":" + promptVersion; }
    private String promptVersion(String requested) { String value = requested == null || requested.isBlank() ? activePromptVersion : requested.trim(); if (!allowedPromptVersions.contains(value)) throw new AiProviderException(HttpStatus.BAD_REQUEST, "AI_PROMPT_VERSION_UNSUPPORTED", "지원하지 않는 일일 변화 브리핑 프롬프트 버전입니다."); return value; }
    private String json(Object value) { try { return objectMapper.writeValueAsString(value); } catch (RuntimeException exception) { throw new IllegalStateException("브리핑 결과를 저장 형식으로 변환할 수 없습니다.", exception); } }
    private <T> List<T> read(String value, Class<T[]> type) { try { return Arrays.asList(objectMapper.readValue(value, type)); } catch (RuntimeException exception) { throw new IllegalStateException("저장된 브리핑을 읽을 수 없습니다.", exception); } }
    @SuppressWarnings("unchecked") private Map<String, List<String>> readMap(String value) { try { return objectMapper.readValue(value, Map.class); } catch (RuntimeException exception) { throw new IllegalStateException("저장된 출처를 읽을 수 없습니다.", exception); } }
    private int elapsed(long started) { return (int) Math.min(Integer.MAX_VALUE, (System.nanoTime() - started) / 1_000_000L); }
    private String errorCode(RuntimeException exception) { if (exception instanceof AiProviderException value) return value.getCode(); if (exception instanceof DailyBriefingException value) return value.getCode(); return "AI_DAILY_BRIEFING_FAILED"; }

    private static final class StreamSupport {
        static List<String> merge(List<String> first, List<String> second) { LinkedHashSet<String> values = new LinkedHashSet<>(); if (first != null) values.addAll(first); if (second != null) values.addAll(second); return new ArrayList<>(values).stream().limit(5).toList(); }
    }
}
