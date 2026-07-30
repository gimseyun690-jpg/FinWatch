package com.finwatch.ai.cache;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("demo")
public class InMemoryPortfolioEvaluationCacheStore implements PortfolioEvaluationCacheStore {

    private final ConcurrentHashMap<String, Entry> cache = new ConcurrentHashMap<>();

    @Override
    public Optional<PortfolioEvaluationCacheValue> get(String key) {
        Entry entry = cache.get(key);
        if (entry == null) return Optional.empty();
        if (entry.expiresAt().isBefore(Instant.now())) {
            cache.remove(key, entry);
            return Optional.empty();
        }
        return Optional.of(entry.value());
    }

    @Override
    public void put(String key, PortfolioEvaluationCacheValue value, Duration ttl) {
        cache.put(key, new Entry(value, Instant.now().plus(ttl)));
    }

    private record Entry(PortfolioEvaluationCacheValue value, Instant expiresAt) {
    }
}
