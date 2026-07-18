package com.finwatch.ai.cache;

import java.time.Duration;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("!demo")
public class RedisAiSummaryCacheStore implements AiSummaryCacheStore {

    private final RedisTemplate<Object, Object> redisTemplate;

    public RedisAiSummaryCacheStore(RedisTemplate<Object, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Optional<AiSummaryCacheValue> get(String key) {
        Object value = redisTemplate.opsForValue().get(key);
        return value instanceof AiSummaryCacheValue cacheValue ? Optional.of(cacheValue) : Optional.empty();
    }

    @Override
    public void put(String key, AiSummaryCacheValue value, Duration ttl) {
        redisTemplate.opsForValue().set(key, value, ttl);
    }

    @Override
    public void delete(String key) {
        redisTemplate.delete(key);
    }
}
