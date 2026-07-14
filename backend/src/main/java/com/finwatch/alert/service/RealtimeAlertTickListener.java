package com.finwatch.alert.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.finwatch.realtime.LiveQuote;
import com.finwatch.realtime.RealtimeEvent;
import com.finwatch.realtime.RealtimeQuoteHub;

import jakarta.annotation.PreDestroy;

@Component
public class RealtimeAlertTickListener {

    private static final Logger log = LoggerFactory.getLogger(RealtimeAlertTickListener.class);

    private final RealtimeAlertEvaluationService evaluationService;
    private final Map<String, LiveQuote> pendingQuotes = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;

    public RealtimeAlertTickListener(
            RealtimeQuoteHub quoteHub,
            RealtimeAlertEvaluationService evaluationService) {
        this.evaluationService = evaluationService;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "realtime-alert-evaluator");
            thread.setDaemon(true);
            return thread;
        });
        quoteHub.addListener(this::accept);
        scheduler.scheduleWithFixedDelay(this::drain, 250, 250, TimeUnit.MILLISECONDS);
    }

    void accept(RealtimeEvent event) {
        if ("quote".equals(event.type()) && event.data() instanceof LiveQuote quote) {
            pendingQuotes.put(quote.symbol(), quote);
        }
    }

    void drain() {
        for (Map.Entry<String, LiveQuote> entry : pendingQuotes.entrySet()) {
            LiveQuote quote = entry.getValue();
            if (!pendingQuotes.remove(entry.getKey(), quote)) {
                continue;
            }
            try {
                evaluationService.evaluate(quote);
            } catch (RuntimeException exception) {
                log.warn("실시간 가격 알림 평가 실패: symbol={}", quote.symbol(), exception);
            }
        }
    }

    @PreDestroy
    public void stop() {
        scheduler.shutdownNow();
    }
}
