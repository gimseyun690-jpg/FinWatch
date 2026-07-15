package com.finwatch.portfolio.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.finwatch.portfolio.domain.PortfolioHolding;

public interface PortfolioHoldingRepository extends JpaRepository<PortfolioHolding, Long> {

    @Query("select h from PortfolioHolding h join fetch h.stock where h.user.id = :userId order by h.createdAt")
    List<PortfolioHolding> findAllWithStockByUserId(@Param("userId") Long userId);

    @Query("select h from PortfolioHolding h join fetch h.stock where h.id = :id and h.user.id = :userId")
    Optional<PortfolioHolding> findWithStockByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    boolean existsByUserIdAndStockId(Long userId, Long stockId);

    @Query("select distinct h.stock from PortfolioHolding h where h.stock.active = true")
    List<com.finwatch.stock.domain.Stock> findDistinctActiveStocksForRealtime();
}
