package com.finwatch.stock.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.finwatch.stock.domain.MarketPrice;

public interface MarketPriceRepository extends JpaRepository<MarketPrice, Long> {

    Optional<MarketPrice> findTopByStockIdOrderByRecordedAtDesc(Long stockId);

    List<MarketPrice> findAllByStockIdAndIntervalOrderByRecordedAtAsc(Long stockId, String interval);

    List<MarketPrice> findAllByStockIdAndIntervalAndRecordedAtBetween(
            Long stockId,
            String interval,
            java.time.Instant from,
            java.time.Instant to);

    boolean existsByStockId(Long stockId);

    @Query("select distinct p.stock.id from MarketPrice p where p.stock.id in :stockIds")
    List<Long> findStockIdsWithPrices(@Param("stockIds") List<Long> stockIds);
}
