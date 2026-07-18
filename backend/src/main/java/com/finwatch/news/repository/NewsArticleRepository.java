package com.finwatch.news.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.finwatch.news.domain.NewsArticle;

public interface NewsArticleRepository extends JpaRepository<NewsArticle, Long>, JpaSpecificationExecutor<NewsArticle> {

    List<NewsArticle> findAllByStockSymbolOrderByPublishedAtDesc(String symbol);

    List<NewsArticle> findAllByStockSymbolAndContentKindOrderByPublishedAtDesc(String symbol, String contentKind);

    List<NewsArticle> findAllByStockMarketAndStockSymbolOrderByPublishedAtDesc(String market, String symbol);

    List<NewsArticle> findAllByStockMarketAndStockSymbolAndContentKindOrderByPublishedAtDesc(
            String market,
            String symbol,
            String contentKind);

    Optional<NewsArticle> findByExternalId(String externalId);

    Optional<NewsArticle> findBySourceAndExternalId(String source, String externalId);

    boolean existsByStockId(Long stockId);

    boolean existsByStockIdAndContentKind(Long stockId, String contentKind);
}
