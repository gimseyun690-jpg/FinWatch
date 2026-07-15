package com.finwatch.ai.cache;

import java.time.Duration;
import java.util.Optional;

public interface TechnicalExplanationCacheStore {

    Optional<TechnicalExplanationCacheValue> get(String key);

    void put(String key, TechnicalExplanationCacheValue value, Duration ttl);
}
