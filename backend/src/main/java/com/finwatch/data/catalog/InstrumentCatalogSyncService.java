package com.finwatch.data.catalog;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.finwatch.data.catalog.InstrumentCatalogResponses.CatalogProviderSyncResult;
import com.finwatch.data.catalog.InstrumentCatalogResponses.CatalogSnapshot;
import com.finwatch.data.catalog.InstrumentCatalogResponses.CatalogSyncResponse;
import com.finwatch.data.catalog.InstrumentCatalogResponses.CatalogWriteResult;
import com.finwatch.data.provider.ProviderException;
import com.finwatch.data.sync.DataMode;
import com.finwatch.stock.repository.StockRepository;

@Service
public class InstrumentCatalogSyncService {

    private final DataMode dataMode;
    private final Map<String, InstrumentCatalogProvider> providers;
    private final InstrumentCatalogSyncRunRepository runRepository;
    private final InstrumentCatalogWriter writer;
    private final StockRepository stockRepository;
    private final int minimumReceived;
    private final double minimumRetentionRatio;

    public InstrumentCatalogSyncService(
            @Value("${app.data.mode:DEMO}") String dataMode,
            List<InstrumentCatalogProvider> providers,
            InstrumentCatalogSyncRunRepository runRepository,
            InstrumentCatalogWriter writer,
            StockRepository stockRepository,
            @Value("${app.data.catalog.minimum-received:100}") int minimumReceived,
            @Value("${app.data.catalog.minimum-retention-ratio:0.50}") double minimumRetentionRatio) {
        this.dataMode = DataMode.from(dataMode);
        this.providers = providers.stream().collect(Collectors.toUnmodifiableMap(
                provider -> provider.providerId().toUpperCase(Locale.ROOT),
                Function.identity()));
        this.runRepository = runRepository;
        this.writer = writer;
        this.stockRepository = stockRepository;
        this.minimumReceived = Math.max(1, minimumReceived);
        this.minimumRetentionRatio = Math.max(0.0, Math.min(1.0, minimumRetentionRatio));
    }

    public CatalogSyncResponse syncAll() {
        Instant startedAt = Instant.now();
        List<CatalogProviderSyncResult> results = providers.values().stream()
                .sorted(Comparator.comparing(InstrumentCatalogProvider::providerId))
                .map(this::syncProvider)
                .toList();
        return new CatalogSyncResponse(dataMode.name(), startedAt, Instant.now(), results);
    }

    public CatalogSyncResponse syncProvider(String providerId) {
        Instant startedAt = Instant.now();
        String normalized = providerId == null ? "" : providerId.trim().toUpperCase(Locale.ROOT);
        InstrumentCatalogProvider provider = providers.get(normalized);
        if (provider == null) {
            return new CatalogSyncResponse(
                    dataMode.name(),
                    startedAt,
                    Instant.now(),
                    List.of(new CatalogProviderSyncResult(
                            normalized,
                            "FAILED",
                            0, 0, 0, 0,
                            "지원하지 않는 종목 카탈로그 공급자입니다.")));
        }
        return new CatalogSyncResponse(dataMode.name(), startedAt, Instant.now(), List.of(syncProvider(provider)));
    }

    private CatalogProviderSyncResult syncProvider(InstrumentCatalogProvider provider) {
        if (dataMode == DataMode.DEMO) {
            return CatalogProviderSyncResult.skipped(
                    provider.providerId(),
                    "DATA_MODE=DEMO에서는 외부 종목 마스터를 호출하지 않습니다.");
        }

        InstrumentCatalogSyncRun run = runRepository.save(
                InstrumentCatalogSyncRun.start(provider.providerId(), Instant.now()));
        int received = 0;
        try {
            CatalogSnapshot snapshot = provider.fetchCatalog();
            received = snapshot.instruments().size();
            validateSnapshot(provider, snapshot);
            CatalogWriteResult writeResult = writer.replaceSnapshot(snapshot);
            run.succeed(writeResult, Instant.now());
            runRepository.save(run);
            return new CatalogProviderSyncResult(
                    provider.providerId(),
                    "SUCCEEDED",
                    writeResult.received(),
                    writeResult.inserted(),
                    writeResult.updated(),
                    writeResult.deactivated(),
                    "종목 마스터 동기화 완료");
        } catch (RuntimeException exception) {
            String code = exception instanceof ProviderException providerException
                    ? providerException.getCode()
                    : "CATALOG_SYNC_FAILED";
            String message = safeMessage(exception);
            run.fail(code, message, received, Instant.now());
            runRepository.save(run);
            return new CatalogProviderSyncResult(
                    provider.providerId(), "FAILED", received, 0, 0, 0, message);
        }
    }

    private void validateSnapshot(InstrumentCatalogProvider provider, CatalogSnapshot snapshot) {
        if (snapshot == null || !provider.providerId().equalsIgnoreCase(snapshot.provider())) {
            throw new IllegalStateException("종목 마스터 공급자 식별자가 일치하지 않습니다.");
        }
        int received = snapshot.instruments().size();
        if (received < minimumReceived) {
            throw new IllegalStateException(
                    "종목 마스터 수가 안전 기준보다 적습니다: " + received + " < " + minimumReceived);
        }
        long currentActive = stockRepository.countByProviderAndActiveTrue(provider.providerId());
        if (currentActive > 0 && received < Math.ceil(currentActive * minimumRetentionRatio)) {
            throw new IllegalStateException(
                    "종목 마스터가 기존 활성 종목 대비 급감하여 반영을 중단했습니다: " + received + "/" + currentActive);
        }

        Set<String> providerIds = new HashSet<>();
        Set<String> canonicalKeys = new HashSet<>();
        List<String> invalid = new ArrayList<>();
        snapshot.instruments().forEach(instrument -> {
            if (blank(instrument.providerInstrumentId()) || blank(instrument.market()) || blank(instrument.symbol())
                    || blank(instrument.name()) || blank(instrument.instrumentType()) || blank(instrument.currency())) {
                invalid.add(String.valueOf(instrument.symbol()));
            }
            if (!providerIds.add(instrument.providerInstrumentId())) {
                invalid.add("providerId:" + instrument.providerInstrumentId());
            }
            String key = instrument.market().toUpperCase(Locale.ROOT) + ":"
                    + instrument.symbol().toUpperCase(Locale.ROOT);
            if (!canonicalKeys.add(key)) {
                invalid.add("canonical:" + key);
            }
        });
        if (!invalid.isEmpty()) {
            throw new IllegalStateException("중복 또는 유효하지 않은 종목 마스터가 포함되어 있습니다: " + invalid.get(0));
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        String sanitized = message.replaceAll("[\\r\\n\\t]+", " ").trim();
        return sanitized.length() <= 500 ? sanitized : sanitized.substring(0, 500);
    }
}
