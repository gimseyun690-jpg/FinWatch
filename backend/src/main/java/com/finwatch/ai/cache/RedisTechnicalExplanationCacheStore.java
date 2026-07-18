package com.finwatch.ai.cache;

import java.time.Duration;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("!demo")
public class RedisTechnicalExplanationCacheStore implements TechnicalExplanationCacheStore {

    private final RedisTemplate<Object, Object> redisTemplate;

    public RedisTechnicalExplanationCacheStore(RedisTemplate<Object, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Optional<TechnicalExplanationCacheValue> get(String key) {
        Object value = redisTemplate.opsForValue().get(key);
        return value instanceof TechnicalExplanationCacheValue cacheValue
                ? Optional.of(cacheValue)
                : Optional.empty();
    }

    @Override
    public void put(String key, TechnicalExplanationCacheValue value, Duration ttl) {
        redisTemplate.opsForValue().set(key, value, ttl);
    }
}
