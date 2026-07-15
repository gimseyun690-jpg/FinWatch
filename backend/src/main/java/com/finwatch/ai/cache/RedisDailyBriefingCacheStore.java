package com.finwatch.ai.cache;

import java.time.Duration;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component @Profile("!demo")
public class RedisDailyBriefingCacheStore implements DailyBriefingCacheStore {
    private final RedisTemplate<Object, Object> redisTemplate;
    public RedisDailyBriefingCacheStore(RedisTemplate<Object, Object> redisTemplate) { this.redisTemplate = redisTemplate; }
    public Optional<DailyBriefingCacheValue> get(String key) {
        Object value = redisTemplate.opsForValue().get(key);
        return value instanceof DailyBriefingCacheValue item ? Optional.of(item) : Optional.empty();
    }
    public void put(String key, DailyBriefingCacheValue value, Duration ttl) { redisTemplate.opsForValue().set(key, value, ttl); }
}
