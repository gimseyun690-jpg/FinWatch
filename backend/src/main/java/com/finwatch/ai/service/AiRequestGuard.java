package com.finwatch.ai.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.finwatch.ai.provider.AiProviderException;
import com.finwatch.ai.repository.AiUsageLogRepository;

@Component
public class AiRequestGuard {
    private final AiUsageLogRepository usageLogs;
    private final int requestsPerMinute;
    private final BigDecimal dailyBudgetUsd;
    private final ZoneId budgetZone;
    private final ConcurrentHashMap<String, ArrayDeque<Instant>> requests = new ConcurrentHashMap<>();

    public AiRequestGuard(AiUsageLogRepository usageLogs,
            @Value("${app.ai.requests-per-minute:20}") int requestsPerMinute,
            @Value("${app.ai.daily-budget-usd:2.00}") BigDecimal dailyBudgetUsd,
            @Value("${app.ai.budget-zone:Asia/Seoul}") String budgetZone) {
        this.usageLogs = usageLogs; this.requestsPerMinute = requestsPerMinute;
        this.dailyBudgetUsd = dailyBudgetUsd; this.budgetZone = ZoneId.of(budgetZone);
    }

    public void checkRequest(String userKey) {
        if (requestsPerMinute <= 0) return;
        String key = userKey == null || userKey.isBlank() ? "anonymous-authenticated" : userKey;
        Instant now = Instant.now(); Instant threshold = now.minusSeconds(60);
        ArrayDeque<Instant> values = requests.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (values) {
            while (!values.isEmpty() && values.getFirst().isBefore(threshold)) values.removeFirst();
            if (values.size() >= requestsPerMinute) throw new AiProviderException(HttpStatus.TOO_MANY_REQUESTS,
                    "AI_RATE_LIMITED", "AI 요청 한도를 초과했습니다. 잠시 후 다시 시도해 주세요.");
            values.addLast(now);
        }
    }

    public void checkBudget() {
        if (dailyBudgetUsd.signum() <= 0) return;
        LocalDate today = LocalDate.now(budgetZone);
        Instant from = today.atStartOfDay(budgetZone).toInstant();
        Instant to = today.plusDays(1).atStartOfDay(budgetZone).toInstant();
        BigDecimal spent = usageLogs.findAllByCreatedAtGreaterThanEqualAndCreatedAtLessThan(from, to).stream()
                .filter(log -> "SUCCESS".equals(log.getStatus()) && !log.isCacheHit())
                .map(log -> log.getEstimatedCost() == null ? BigDecimal.ZERO : log.getEstimatedCost())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (spent.compareTo(dailyBudgetUsd) >= 0) throw new AiProviderException(HttpStatus.TOO_MANY_REQUESTS,
                "AI_BUDGET_EXCEEDED", "일일 AI 비용 한도에 도달했습니다. 캐시된 결과와 기존 데이터를 이용해 주세요.");
    }
}
