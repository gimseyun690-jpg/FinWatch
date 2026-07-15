package com.finwatch.realtime;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class RealtimeQuoteOrchestrator {

    private final RealtimeSubscriptionManager subscriptionManager;

    public RealtimeQuoteOrchestrator(RealtimeSubscriptionManager subscriptionManager) {
        this.subscriptionManager = subscriptionManager;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        subscriptionManager.start();
    }
}
