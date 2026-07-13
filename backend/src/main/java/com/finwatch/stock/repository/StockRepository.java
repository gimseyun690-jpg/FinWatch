package com.finwatch.stock.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.finwatch.stock.domain.Stock;

public interface StockRepository extends JpaRepository<Stock, Long> {

    List<Stock> findAllByActiveTrueOrderByMarketAscNameAsc();

    Optional<Stock> findFirstBySymbolAndActiveTrue(String symbol);
}

