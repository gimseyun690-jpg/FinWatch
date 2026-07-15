package com.finwatch.watchlist.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.finwatch.watchlist.domain.Watchlist;

public interface WatchlistRepository extends JpaRepository<Watchlist, Long> {

    @Query("""
            select w from Watchlist w
            join fetch w.stock s
            where w.user.id = :userId
            order by w.createdAt asc
            """)
    List<Watchlist> findAllWithStockByUserId(@Param("userId") Long userId);

    @Query("""
            select w from Watchlist w
            join fetch w.stock s
            where w.user.id = :userId and upper(s.symbol) = upper(:symbol)
            """)
    Optional<Watchlist> findByUserIdAndSymbol(
            @Param("userId") Long userId,
            @Param("symbol") String symbol);

    boolean existsByUserIdAndStockId(Long userId, Long stockId);

    @Query("select distinct w.stock from Watchlist w where w.stock.active = true")
    List<com.finwatch.stock.domain.Stock> findDistinctActiveStocksForRealtime();
}
