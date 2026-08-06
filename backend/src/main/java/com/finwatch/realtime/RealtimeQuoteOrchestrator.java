package com.finwatch.realtime;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class RealtimeQuoteOrchestrator {

    private final RealtimeSubscriptionManager subscriptionManager;
    private final RealtimeQuoteFallbackPoller fallbackPoller;

    public RealtimeQuoteOrchestrator(
            RealtimeSubscriptionManager subscriptionManager,
            RealtimeQuoteFallbackPoller fallbackPoller) {
        this.subscriptionManager = subscriptionManager;
        this.fallbackPoller = fallbackPoller;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        subscriptionManager.start();
        fallbackPoller.start();
    }
}
