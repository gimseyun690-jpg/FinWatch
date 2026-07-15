package com.finwatch.fx.cache;

import java.time.Duration;
import java.util.Optional;

public interface FxRateCacheStore {
    Optional<FxRateCacheValue> get(String key);
    void put(String key, FxRateCacheValue value, Duration ttl);
}
