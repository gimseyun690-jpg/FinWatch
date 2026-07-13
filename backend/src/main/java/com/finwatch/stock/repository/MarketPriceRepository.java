package com.finwatch.stock.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.finwatch.stock.domain.MarketPrice;

public interface MarketPriceRepository extends JpaRepository<MarketPrice, Long> {

    Optional<MarketPrice> findTopByStockIdOrderByRecordedAtDesc(Long stockId);

    List<MarketPrice> findAllByStockIdAndIntervalOrderByRecordedAtAsc(Long stockId, String interval);
}
