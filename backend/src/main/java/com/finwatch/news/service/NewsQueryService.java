package com.finwatch.news.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.finwatch.ai.repository.AiAnalysisRepository;
import com.finwatch.news.dto.NewsResponse;
import com.finwatch.news.repository.NewsArticleRepository;

@Service
@Transactional(readOnly = true)
public class NewsQueryService {

    private final NewsArticleRepository newsArticleRepository;
    private final AiAnalysisRepository aiAnalysisRepository;

    public NewsQueryService(
            NewsArticleRepository newsArticleRepository,
            AiAnalysisRepository aiAnalysisRepository) {
        this.newsArticleRepository = newsArticleRepository;
        this.aiAnalysisRepository = aiAnalysisRepository;
    }

    public List<NewsResponse> getNews(String symbol) {
        return newsArticleRepository.findAllByStockSymbolOrderByPublishedAtDesc(symbol).stream()
                .map(article -> new NewsResponse(
                        article.getId(),
                        article.getStock().getSymbol(),
                        article.getTitle(),
                        article.getPublisher(),
                        article.getCanonicalUrl(),
                        article.getPublishedAt(),
                        aiAnalysisRepository.existsByNewsId(article.getId()),
                        article.getSource(),
                        article.getContentSource().name(),
                        article.getRightsProfile().name(),
                        article.isAiAnalysisAllowed(),
                        article.getFetchedAt()))
                .toList();
    }
}
