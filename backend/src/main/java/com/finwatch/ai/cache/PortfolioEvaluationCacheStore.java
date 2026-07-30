package com.finwatch.ai.cache;

import java.time.Duration;
import java.util.Optional;

public interface PortfolioEvaluationCacheStore {

    Optional<PortfolioEvaluationCacheValue> get(String key);

    void put(String key, PortfolioEvaluationCacheValue value, Duration ttl);
}
