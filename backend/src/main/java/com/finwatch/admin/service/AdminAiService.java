package com.finwatch.admin.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.finwatch.admin.dto.AiFeatureUsageResponse;
import com.finwatch.admin.dto.AiMetricsResponse;
import com.finwatch.admin.dto.AiUsageLogPageResponse;
import com.finwatch.admin.dto.AiUsageLogResponse;
import com.finwatch.ai.domain.AiUsageLog;
import com.finwatch.ai.repository.AiUsageLogRepository;

@Service
@Transactional(readOnly = true)
public class AdminAiService {

    private static final BigDecimal ZERO_COST = BigDecimal.ZERO.setScale(8);
    private static final int MAX_PAGE_SIZE = 100;

    private final AiUsageLogRepository aiUsageLogRepository;

    public AdminAiService(AiUsageLogRepository aiUsageLogRepository) {
        this.aiUsageLogRepository = aiUsageLogRepository;
    }

    public AiMetricsResponse getMetrics(LocalDate requestedFrom, LocalDate requestedTo) {
        DateRange range = dateRange(requestedFrom, requestedTo);
        List<AiUsageLog> logs = aiUsageLogRepository
                .findAllByCreatedAtGreaterThanEqualAndCreatedAtLessThan(range.fromInstant(), range.toExclusive());

        long requestCount = logs.size();
        List<AiUsageLog> successful = logs.stream().filter(log -> "SUCCESS".equals(log.getStatus())).toList();
        long successCount = successful.size();
        long failedCount = requestCount - successCount;
        long cacheHitCount = successful.stream().filter(AiUsageLog::isCacheHit).count();
        long modelCallCount = successful.stream().filter(log -> !log.isCacheHit()).count();
        long inputTokens = logs.stream().mapToLong(AiUsageLog::getInputTokens).sum();
        long outputTokens = logs.stream().mapToLong(AiUsageLog::getOutputTokens).sum();
        BigDecimal estimatedCost = sum(logs, false);
        BigDecimal savedCost = sum(logs, true);
        BigDecimal hitRate = successCount == 0
                ? BigDecimal.ZERO.setScale(2)
                : BigDecimal.valueOf(cacheHitCount * 100.0 / successCount).setScale(2, RoundingMode.HALF_UP);
        BigDecimal averageResponseTime = requestCount == 0
                ? BigDecimal.ZERO.setScale(2)
                : BigDecimal.valueOf(logs.stream().mapToLong(AiUsageLog::getResponseTimeMs).average().orElse(0))
                        .setScale(2, RoundingMode.HALF_UP);

        Map<String, List<AiUsageLog>> featureLogs = logs.stream()
                .collect(Collectors.groupingBy(AiUsageLog::getFeatureType));
        List<AiFeatureUsageResponse> featureUsage = featureLogs.entrySet().stream()
                .map(entry -> featureUsage(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingLong(AiFeatureUsageResponse::requestCount).reversed())
                .toList();

        return new AiMetricsResponse(
                range.from(),
                range.to(),
                requestCount,
                successCount,
                failedCount,
                modelCallCount,
                cacheHitCount,
                modelCallCount,
                inputTokens,
                outputTokens,
                inputTokens + outputTokens,
                estimatedCost,
                hitRate,
                savedCost,
                averageResponseTime,
                "USD",
                featureUsage);
    }

    private AiFeatureUsageResponse featureUsage(String feature, List<AiUsageLog> logs) {
        long successes = logs.stream().filter(log -> "SUCCESS".equals(log.getStatus())).count();
        long failures = logs.size() - successes;
        long hits = logs.stream().filter(log -> "SUCCESS".equals(log.getStatus()) && log.isCacheHit()).count();
        long modelCalls = logs.stream().filter(log -> "SUCCESS".equals(log.getStatus()) && !log.isCacheHit()).count();
        long tokens = logs.stream().mapToLong(log -> log.getInputTokens() + log.getOutputTokens()).sum();
        return new AiFeatureUsageResponse(
                feature,
                logs.size(),
                successes,
                failures,
                modelCalls,
                hits,
                tokens,
                sum(logs, false),
                sum(logs, true));
    }

    public AiUsageLogPageResponse getUsageLogs(
            LocalDate requestedFrom,
            LocalDate requestedTo,
            int requestedPage,
            int requestedSize,
            String sortField,
            String sortDirection) {
        DateRange range = dateRange(requestedFrom, requestedTo);
        int page = Math.max(0, requestedPage);
        int size = Math.min(MAX_PAGE_SIZE, Math.max(1, requestedSize));
        String[] sortParts = sortField.split(",", 2);
        String resolvedSortField = sortParts[0];
        String resolvedSortDirection = sortParts.length == 2 ? sortParts[1] : sortDirection;
        Sort.Direction direction = "asc".equalsIgnoreCase(resolvedSortDirection)
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        String property = switch (resolvedSortField) {
            case "estimatedCost" -> "estimatedCost";
            case "savedEstimatedCost" -> "savedEstimatedCost";
            case "responseTimeMs" -> "responseTimeMs";
            default -> "createdAt";
        };

        Page<AiUsageLog> result = aiUsageLogRepository
                .findAllByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        range.fromInstant(),
                        range.toExclusive(),
                        PageRequest.of(page, size, Sort.by(direction, property)));
        return new AiUsageLogPageResponse(
                result.getContent().stream().map(AiUsageLogResponse::from).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    private BigDecimal sum(List<AiUsageLog> logs, boolean saved) {
        return logs.stream()
                .map(saved ? AiUsageLog::getSavedEstimatedCost : AiUsageLog::getEstimatedCost)
                .reduce(ZERO_COST, BigDecimal::add)
                .setScale(8, RoundingMode.HALF_UP);
    }

    private DateRange dateRange(LocalDate requestedFrom, LocalDate requestedTo) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate from = requestedFrom == null ? today.minusDays(29) : requestedFrom;
        LocalDate to = requestedTo == null ? today : requestedTo;
        if (to.isBefore(from)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "종료일은 시작일보다 빠를 수 없습니다.");
        }
        return new DateRange(
                from,
                to,
                from.atStartOfDay().toInstant(ZoneOffset.UTC),
                to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC));
    }

    private record DateRange(LocalDate from, LocalDate to, Instant fromInstant, Instant toExclusive) {
    }
}
