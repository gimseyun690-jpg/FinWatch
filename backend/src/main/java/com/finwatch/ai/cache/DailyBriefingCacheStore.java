package com.finwatch.ai.cache;

import java.time.Duration;
import java.util.Optional;

public interface DailyBriefingCacheStore {
    Optional<DailyBriefingCacheValue> get(String key);
    void put(String key, DailyBriefingCacheValue value, Duration ttl);
}
