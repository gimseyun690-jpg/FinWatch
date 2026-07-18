package com.finwatch.alert.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.finwatch.alert.domain.AlertCondition;
import com.finwatch.alert.domain.AlertStatus;

public record AlertResponse(
        Long id,
        String symbol,
        String name,
        String market,
        AlertCondition condition,
        BigDecimal targetPrice,
        String currency,
        AlertStatus status,
        BigDecimal latestPrice,
        Instant priceAsOf,
        String evaluationStatus,
        Instant triggeredAt,
        Instant createdAt) {
}
