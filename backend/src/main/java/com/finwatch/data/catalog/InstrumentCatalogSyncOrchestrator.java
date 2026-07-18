package com.finwatch.data.catalog;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

@Component
public class InstrumentCatalogSyncOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(InstrumentCatalogSyncOrchestrator.class);

    private final boolean active;
    private final Duration initialDelay;
    private final Duration interval;
    private final InstrumentCatalogSyncService syncService;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean syncing = new AtomicBoolean();

    public InstrumentCatalogSyncOrchestrator(
            @Value("${app.data.mode:DEMO}") String dataMode,
            @Value("${app.data.catalog.auto-sync-enabled:true}") boolean enabled,
            @Value("${app.data.catalog.auto-sync-initial-delay:30s}") Duration initialDelay,
            @Value("${app.data.catalog.auto-sync-interval:24h}") Duration interval,
            InstrumentCatalogSyncService syncService) {
        this.active = enabled && "LIVE".equalsIgnoreCase(dataMode);
        this.initialDelay = initialDelay.isNegative() ? Duration.ZERO : initialDelay;
        this.interval = interval.compareTo(Duration.ofHours(1)) < 0 ? Duration.ofHours(1) : interval;
        this.syncService = syncService;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "instrument-catalog-sync");
            thread.setDaemon(true);
            return thread;
        });
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!active) {
            return;
        }
        scheduler.scheduleWithFixedDelay(
                this::safeSync,
                initialDelay.toSeconds(),
                interval.toSeconds(),
                TimeUnit.SECONDS);
    }

    private void safeSync() {
        if (!syncing.compareAndSet(false, true)) {
            return;
        }
        try {
            var result = syncService.syncAll();
            long succeeded = result.providers().stream()
                    .filter(provider -> "SUCCEEDED".equals(provider.status()))
                    .count();
            log.info("Instrument catalog synchronization finished: {}/{} providers succeeded",
                    succeeded,
                    result.providers().size());
        } catch (RuntimeException exception) {
            log.warn("Instrument catalog synchronization failed: {}", safeMessage(exception));
        } finally {
            syncing.set(false);
        }
    }

    private String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        String sanitized = message.replaceAll("[\\r\\n\\t]+", " ").trim();
        return sanitized.length() <= 300 ? sanitized : sanitized.substring(0, 300);
    }

    @PreDestroy
    void stop() {
        scheduler.shutdownNow();
    }
}
