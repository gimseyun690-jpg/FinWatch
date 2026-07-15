package com.finwatch.fx.cache;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("demo")
public class InMemoryFxRateCacheStore implements FxRateCacheStore {
    private final ConcurrentHashMap<String, Entry> values = new ConcurrentHashMap<>();

    @Override
    public Optional<FxRateCacheValue> get(String key) {
        Entry entry = values.get(key);
        if (entry == null) return Optional.empty();
        if (entry.expiresAt().isBefore(Instant.now())) {
            values.remove(key, entry);
            return Optional.empty();
        }
        return Optional.of(entry.value());
    }

    @Override
    public void put(String key, FxRateCacheValue value, Duration ttl) {
        values.put(key, new Entry(value, Instant.now().plus(ttl)));
    }

    private record Entry(FxRateCacheValue value, Instant expiresAt) { }
}
