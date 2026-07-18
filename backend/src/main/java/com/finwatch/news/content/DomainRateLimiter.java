package com.finwatch.news.content;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class DomainRateLimiter {

    private final ConcurrentHashMap<String, AtomicLong> nextAllowedAt = new ConcurrentHashMap<>();

    public void check(String host, int minIntervalMs) {
        if (minIntervalMs <= 0) {
            return;
        }
        String key = host.toLowerCase(Locale.ROOT);
        AtomicLong next = nextAllowedAt.computeIfAbsent(key, ignored -> new AtomicLong());
        long now = System.currentTimeMillis();
        long nextAt = next.get();
        if (now < nextAt || !next.compareAndSet(nextAt, now + minIntervalMs)) {
            throw new NewsContentException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "PROVIDER_RATE_LIMITED",
                    "출처별 최소 호출 간격이 지나지 않았습니다.");
        }
    }
}
