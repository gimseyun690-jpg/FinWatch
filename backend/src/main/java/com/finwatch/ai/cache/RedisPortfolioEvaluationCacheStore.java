package com.finwatch.ai.cache;

import java.time.Duration;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("!demo")
public class RedisPortfolioEvaluationCacheStore implements PortfolioEvaluationCacheStore {

    private final RedisTemplate<Object, Object> redisTemplate;

    public RedisPortfolioEvaluationCacheStore(RedisTemplate<Object, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Optional<PortfolioEvaluationCacheValue> get(String key) {
        Object value = redisTemplate.opsForValue().get(key);
        return value instanceof PortfolioEvaluationCacheValue item
                ? Optional.of(item)
                : Optional.empty();
    }

    @Override
    public void put(String key, PortfolioEvaluationCacheValue value, Duration ttl) {
        redisTemplate.opsForValue().set(key, value, ttl);
    }
}
