package com.finwatch.realtime;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.springframework.stereotype.Component;

@Component
public class RealtimeQuoteHub {

    private final ConcurrentHashMap<String, LiveQuote> quotes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RealtimeProviderStatus> providerStatuses = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Consumer<RealtimeEvent>> listeners = new CopyOnWriteArrayList<>();

    public void publish(LiveQuote quote) {
        if (quote == null || quote.symbol() == null || quote.price() == null || quote.asOf() == null) {
            return;
        }
        boolean[] accepted = {false};
        quotes.compute(quote.symbol(), (symbol, current) -> {
            if (current == null || !quote.asOf().isBefore(current.asOf())) {
                accepted[0] = true;
                return quote;
            }
            return current;
        });
        if (accepted[0]) {
            notifyListeners(new RealtimeEvent("quote", quote));
        }
    }

    public void updateProvider(String provider, String state, String message) {
        RealtimeProviderStatus status = new RealtimeProviderStatus(
                provider,
                state,
                sanitize(message),
                Instant.now());
        providerStatuses.put(provider, status);
        notifyListeners(new RealtimeEvent("status", status));
    }

    public Optional<LiveQuote> find(String symbol) {
        return Optional.ofNullable(quotes.get(symbol));
    }

    public RealtimeSnapshot snapshot() {
        List<LiveQuote> quoteItems = quotes.values().stream()
                .sorted(Comparator.comparing(LiveQuote::symbol))
                .toList();
        List<RealtimeProviderStatus> statuses = providerStatuses.values().stream()
                .sorted(Comparator.comparing(RealtimeProviderStatus::provider))
                .toList();
        return new RealtimeSnapshot(quoteItems, statuses);
    }

    public void addListener(Consumer<RealtimeEvent> listener) {
        listeners.add(listener);
    }

    private void notifyListeners(RealtimeEvent event) {
        for (Consumer<RealtimeEvent> listener : listeners) {
            try {
                listener.accept(event);
            } catch (RuntimeException ignored) {
                // A slow or closed browser session must not stop provider ingestion.
            }
        }
    }

    private String sanitize(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        String sanitized = message.replaceAll("[\\r\\n\\t]+", " ").trim();
        return sanitized.length() <= 240 ? sanitized : sanitized.substring(0, 240);
    }
}
