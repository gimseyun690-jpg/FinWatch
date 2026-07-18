package com.finwatch.ai.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.finwatch.ai.domain.AiDailyChangeBriefing;

public interface AiDailyChangeBriefingRepository extends JpaRepository<AiDailyChangeBriefing, Long> {
    Optional<AiDailyChangeBriefing> findByCacheKey(String cacheKey);
    Optional<AiDailyChangeBriefing> findFirstByStockIdOrderByGeneratedAtDesc(Long stockId);
}
