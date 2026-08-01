package com.finwatch.realtime;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.springframework.stereotype.Component;

@Component
public class RealtimeQuoteHub {

    private final ConcurrentHashMap<String, LiveQuote> quotes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RealtimeFxRate> fxRates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RealtimeProviderStatus> providerStatuses = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Consumer<RealtimeEvent>> listeners = new CopyOnWriteArrayList<>();

    public void publish(LiveQuote quote) {
        if (quote == null || quote.symbol() == null || quote.price() == null || quote.asOf() == null) {
            return;
        }
        boolean[] accepted = {false};
        quotes.compute(quote.canonicalKey(), (key, current) -> {
            if (current == null
                    || quote.asOf().isAfter(current.asOf())
                    || (quote.asOf().equals(current.asOf()) && !quote.equals(current))) {
                accepted[0] = true;
                return quote;
            }
            return current;
        });
        if (accepted[0]) {
            notifyListeners(new RealtimeEvent("quote", quote));
        }
    }

    public void publish(RealtimeFxRate fxRate) {
        if (!valid(fxRate)) {
            return;
        }
        boolean[] accepted = {false};
        fxRates.compute(fxRate.canonicalKey(), (key, current) -> {
            if (current == null || fxRate.asOf().isAfter(current.asOf())) {
                accepted[0] = true;
                return fxRate;
            }
            return current;
        });
        if (accepted[0]) {
            notifyListeners(new RealtimeEvent("fx", fxRate));
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

    public Optional<LiveQuote> find(String market, String symbol) {
        return Optional.ofNullable(quotes.get(canonical(market, symbol)));
    }

    /**
     * Legacy symbol-only lookup. It deliberately returns empty when the symbol exists in
     * more than one market, so callers can never consume a quote from the wrong market.
     */
    public Optional<LiveQuote> find(String symbol) {
        String normalizedSymbol = normalize(symbol);
        List<LiveQuote> matches = quotes.values().stream()
                .filter(quote -> quote.symbol().equals(normalizedSymbol))
                .limit(2)
                .toList();
        return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
    }

    public Optional<RealtimeFxRate> findFx(String baseCurrency, String quoteCurrency) {
        return Optional.ofNullable(fxRates.get(fxKey(baseCurrency, quoteCurrency)));
    }

    public List<RealtimeFxRate> fxSnapshot() {
        return fxRates.values().stream()
                .sorted(Comparator.comparing(RealtimeFxRate::baseCurrency)
                        .thenComparing(RealtimeFxRate::quoteCurrency))
                .toList();
    }

    public RealtimeSnapshot snapshot() {
        List<LiveQuote> quoteItems = quotes.values().stream()
                .sorted(Comparator.comparing(LiveQuote::market).thenComparing(LiveQuote::symbol))
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

    private String canonical(String market, String symbol) {
        String normalizedMarket = normalize(market);
        return (normalizedMarket.isBlank() ? "UNKNOWN" : normalizedMarket) + ":" + normalize(symbol);
    }

    private String fxKey(String baseCurrency, String quoteCurrency) {
        return normalize(baseCurrency) + "/" + normalize(quoteCurrency);
    }

    private boolean valid(RealtimeFxRate fxRate) {
        return fxRate != null
                && !fxRate.baseCurrency().isBlank()
                && !fxRate.quoteCurrency().isBlank()
                && !fxRate.baseCurrency().equals(fxRate.quoteCurrency())
                && fxRate.rate() != null
                && fxRate.rate().signum() > 0
                && !fxRate.rateType().isBlank()
                && !fxRate.source().isBlank()
                && !fxRate.providerSymbol().isBlank()
                && fxRate.asOf() != null
                && fxRate.fetchedAt() != null
                && !fxRate.asOf().isAfter(fxRate.fetchedAt().plusSeconds(300));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
