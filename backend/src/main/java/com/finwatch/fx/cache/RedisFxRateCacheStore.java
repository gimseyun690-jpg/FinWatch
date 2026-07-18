package com.finwatch.fx.cache;

import java.time.Duration;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("!demo")
public class RedisFxRateCacheStore implements FxRateCacheStore {
    private final RedisTemplate<Object, Object> redisTemplate;

    public RedisFxRateCacheStore(RedisTemplate<Object, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Optional<FxRateCacheValue> get(String key) {
        Object value = redisTemplate.opsForValue().get(key);
        return value instanceof FxRateCacheValue fx ? Optional.of(fx) : Optional.empty();
    }

    @Override
    public void put(String key, FxRateCacheValue value, Duration ttl) {
        redisTemplate.opsForValue().set(key, value, ttl);
    }
}
