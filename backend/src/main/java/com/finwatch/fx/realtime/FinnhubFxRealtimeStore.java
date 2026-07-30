package com.finwatch.fx.realtime;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;

/**
 * Keeps only the newest provider-timestamped USD/KRW trade received from the
 * Finnhub WebSocket. This store intentionally does not manufacture timestamps:
 * callers must provide the timestamp contained in the provider frame.
 */
@Component
public class FinnhubFxRealtimeStore {

    private final AtomicReference<Snapshot> latest = new AtomicReference<>();

    public boolean accept(
            String providerSymbol,
            BigDecimal rate,
            Instant providerAsOf,
            Instant receivedAt) {
        String symbol = normalize(providerSymbol);
        if (symbol.isBlank()
                || rate == null
                || rate.signum() <= 0
                || providerAsOf == null
                || receivedAt == null
                || providerAsOf.isAfter(receivedAt.plus(Duration.ofMinutes(5)))) {
            return false;
        }
        Snapshot next = new Snapshot(symbol, rate, providerAsOf, receivedAt);
        while (true) {
            Snapshot current = latest.get();
            if (current != null && !next.asOf().isAfter(current.asOf())) {
                return false;
            }
            if (latest.compareAndSet(current, next)) {
                return true;
            }
        }
    }

    public Optional<Snapshot> latest(String providerSymbol, Duration maxAge, Instant now) {
        Snapshot value = latest.get();
        if (value == null || !value.providerSymbol().equals(normalize(providerSymbol))) {
            return Optional.empty();
        }
        Duration allowedAge = maxAge == null || maxAge.isNegative() || maxAge.isZero()
                ? Duration.ofMinutes(2)
                : maxAge;
        if (value.asOf().isAfter(now.plus(Duration.ofMinutes(5)))
                || value.asOf().isBefore(now.minus(allowedAge))) {
            return Optional.empty();
        }
        return Optional.of(value);
    }

    void clear() {
        latest.set(null);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    public record Snapshot(
            String providerSymbol,
            BigDecimal rate,
            Instant asOf,
            Instant receivedAt) {
    }
}
