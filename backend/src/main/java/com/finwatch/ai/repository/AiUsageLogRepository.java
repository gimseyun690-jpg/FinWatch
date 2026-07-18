package com.finwatch.ai.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.finwatch.ai.domain.AiUsageLog;

public interface AiUsageLogRepository extends JpaRepository<AiUsageLog, Long> {

    List<AiUsageLog> findAllByCreatedAtGreaterThanEqualAndCreatedAtLessThan(Instant from, Instant to);

    Page<AiUsageLog> findAllByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            Instant from, Instant to, Pageable pageable);
}
