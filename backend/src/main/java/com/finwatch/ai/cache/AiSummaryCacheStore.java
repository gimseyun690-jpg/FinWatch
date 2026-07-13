package com.finwatch.ai.cache;

import java.time.Duration;
import java.util.Optional;

public interface AiSummaryCacheStore {

    Optional<AiSummaryCacheValue> get(String key);

    void put(String key, AiSummaryCacheValue value, Duration ttl);

    void delete(String key);
}
