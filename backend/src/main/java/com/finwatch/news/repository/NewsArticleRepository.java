package com.finwatch.news.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.finwatch.news.domain.NewsArticle;

public interface NewsArticleRepository extends JpaRepository<NewsArticle, Long> {

    List<NewsArticle> findAllByStockSymbolOrderByPublishedAtDesc(String symbol);

    Optional<NewsArticle> findByExternalId(String externalId);

    Optional<NewsArticle> findBySourceAndExternalId(String source, String externalId);
}
