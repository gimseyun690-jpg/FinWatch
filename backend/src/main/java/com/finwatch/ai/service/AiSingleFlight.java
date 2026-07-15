package com.finwatch.ai.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

@Component
public class AiSingleFlight {

    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    public <T> T execute(String key, Supplier<T> action) {
        Object lock = locks.computeIfAbsent(key, ignored -> new Object());
        try {
            synchronized (lock) {
                return action.get();
            }
        } finally {
            locks.remove(key, lock);
        }
    }
}
