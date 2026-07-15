package com.finwatch.alert.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.finwatch.alert.domain.AlertCondition;
import com.finwatch.alert.domain.AlertStatus;
import com.finwatch.alert.domain.PriceAlert;

public interface PriceAlertRepository extends JpaRepository<PriceAlert, Long> {

    @Query("select a from PriceAlert a join fetch a.stock where a.user.id = :userId order by a.createdAt")
    List<PriceAlert> findAllWithStockByUserId(@Param("userId") Long userId);

    @Query("select a from PriceAlert a join fetch a.stock where a.id = :id and a.user.id = :userId")
    Optional<PriceAlert> findWithStockByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    @Query("select a from PriceAlert a where a.user.id = :userId and a.stock.id = :stockId and a.condition = :condition and a.targetPrice = :targetPrice")
    Optional<PriceAlert> findDuplicate(
            @Param("userId") Long userId,
            @Param("stockId") Long stockId,
            @Param("condition") AlertCondition condition,
            @Param("targetPrice") BigDecimal targetPrice);

    @Query("""
            select a from PriceAlert a join fetch a.stock s
            where a.status = :status
              and s.symbol = :symbol
              and ((a.condition = :above and a.targetPrice <= :price)
                or (a.condition = :below and a.targetPrice >= :price))
            """)
    List<PriceAlert> findMatchingActiveAlerts(
            @Param("symbol") String symbol,
            @Param("price") BigDecimal price,
            @Param("status") AlertStatus status,
            @Param("above") AlertCondition above,
            @Param("below") AlertCondition below);

    @Query("select distinct a.stock from PriceAlert a where a.status = :status and a.stock.active = true")
    List<com.finwatch.stock.domain.Stock> findDistinctActiveStocksForRealtime(@Param("status") AlertStatus status);
}
