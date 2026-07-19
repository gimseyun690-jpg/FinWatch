package com.finwatch.stock.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Collection;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.finwatch.stock.domain.Stock;

public interface StockRepository extends JpaRepository<Stock, Long> {

    List<Stock> findAllByActiveTrueOrderByMarketAscNameAsc();

    @Query("""
            select s from Stock s
            where s.active = true
              and exists (select p.id from MarketPrice p where p.stock = s)
            order by s.market asc, s.name asc
            """)
    List<Stock> findAllActiveWithPrices();

    Optional<Stock> findFirstBySymbolAndActiveTrue(String symbol);

    Optional<Stock> findByMarketAndSymbolAndActiveTrue(String market, String symbol);

    Optional<Stock> findByMarketAndSymbol(String market, String symbol);

    Optional<Stock> findByProviderAndProviderInstrumentId(String provider, String providerInstrumentId);

    List<Stock> findAllByProvider(String provider);

    long countByProviderAndActiveTrue(String provider);

    List<Stock> findAllByMarketIn(Collection<String> markets);

    List<Stock> findAllBySymbolIgnoreCaseAndActiveTrueOrderByMarketAsc(String symbol);

    @Query("select max(s.catalogUpdatedAt) from Stock s")
    Optional<Instant> findCatalogAsOf();

    @Query(value = """
            select s from Stock s
            where (:market is null or s.market = :market)
              and (:instrumentType is null or s.instrumentType = :instrumentType)
              and (
                    lower(s.symbol) like concat('%', :query, '%')
                 or s.normalizedName like concat('%', :query, '%')
                 or s.normalizedEnglishName like concat('%', :query, '%')
                 or exists (
                     select a.id from StockAlias a
                     where a.stock = s and a.normalizedAlias like concat('%', :query, '%')
                 )
              )
            order by
              case
                when lower(s.symbol) = :query then 0
                when s.normalizedName = :query
                  or s.normalizedEnglishName = :query
                  or exists (
                      select exactAlias.id from StockAlias exactAlias
                      where exactAlias.stock = s and exactAlias.normalizedAlias = :query
                  ) then 1
                when lower(s.symbol) like concat(:query, '%') then 2
                when s.normalizedName like concat(:query, '%')
                  or s.normalizedEnglishName like concat(:query, '%')
                  or exists (
                      select prefixAlias.id from StockAlias prefixAlias
                      where prefixAlias.stock = s and prefixAlias.normalizedAlias like concat(:query, '%')
                  ) then 3
                else 4
              end,
              s.active desc,
              s.market asc,
              s.name asc
            """,
            countQuery = """
            select count(s) from Stock s
            where (:market is null or s.market = :market)
              and (:instrumentType is null or s.instrumentType = :instrumentType)
              and (
                    lower(s.symbol) like concat('%', :query, '%')
                 or s.normalizedName like concat('%', :query, '%')
                 or s.normalizedEnglishName like concat('%', :query, '%')
                 or exists (
                     select a.id from StockAlias a
                     where a.stock = s and a.normalizedAlias like concat('%', :query, '%')
                 )
              )
            """)
    Page<Stock> searchCatalog(
            @Param("query") String query,
            @Param("market") String market,
            @Param("instrumentType") String instrumentType,
            Pageable pageable);
}
