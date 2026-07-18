package com.finwatch.ai.cache;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("demo")
public class InMemoryAiSummaryCacheStore implements AiSummaryCacheStore {

    private final ConcurrentHashMap<String, Entry> cache = new ConcurrentHashMap<>();

    @Override
    public Optional<AiSummaryCacheValue> get(String key) {
        Entry entry = cache.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.expiresAt().isBefore(Instant.now())) {
            cache.remove(key);
            return Optional.empty();
        }
        return Optional.of(entry.value());
    }

    @Override
    public void put(String key, AiSummaryCacheValue value, Duration ttl) {
        cache.put(key, new Entry(value, Instant.now().plus(ttl)));
    }

    @Override
    public void delete(String key) {
        cache.remove(key);
    }

    private record Entry(AiSummaryCacheValue value, Instant expiresAt) {
    }
}
