package com.finwatch.news.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.finwatch.ai.repository.AiAnalysisRepository;
import com.finwatch.news.dto.NewsResponse;
import com.finwatch.news.dto.NewsDetailResponse;
import com.finwatch.news.repository.NewsArticleRepository;
import org.springframework.http.HttpStatus;

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
        return toResponses(newsArticleRepository
                .findAllByStockSymbolAndContentKindOrderByPublishedAtDesc(symbol, "NEWS"));
    }

    public List<NewsResponse> getNews(String market, String symbol) {
        return toResponses(newsArticleRepository
                .findAllByStockMarketAndStockSymbolAndContentKindOrderByPublishedAtDesc(
                        market.trim().toUpperCase(java.util.Locale.ROOT),
                        symbol.trim().toUpperCase(java.util.Locale.ROOT),
                        "NEWS"));
    }

    public NewsDetailResponse getNewsDetail(Long newsId) {
        var article = newsArticleRepository.findById(newsId)
                .orElseThrow(() -> new NewsQueryException(HttpStatus.NOT_FOUND, "NEWS_NOT_FOUND", "뉴스를 찾을 수 없습니다."));
        boolean displayAllowed = article.getRightsProfile() == com.finwatch.news.content.RightsProfile.STORE_AND_DISPLAY;
        return new NewsDetailResponse(article.getId(), article.getStock().getMarket(), article.getStock().getSymbol(),
                article.getExternalId(), article.getTitle(), NewsPublisherName.resolve(article.getPublisher(), article.getCanonicalUrl()), article.getUrl(), article.getCanonicalUrl(),
                article.getContentKind(), article.getDisclosureType(), article.getPublishedAt(), article.getSource(),
                article.getContentSource().name(), article.getRightsProfile().name(), article.isAiSummaryRequestAllowed(),
                displayAllowed, displayAllowed ? article.getContent() : null, article.getContentHash(), article.getExtractorVersion(),
                article.getFetchedAt(), aiAnalysisRepository.existsByNewsId(article.getId()));
    }

    private List<NewsResponse> toResponses(List<com.finwatch.news.domain.NewsArticle> articles) {
        return articles.stream()
                .map(article -> new NewsResponse(
                        article.getId(),
                        article.getStock().getSymbol(),
                        article.getTitle(),
                        NewsPublisherName.resolve(article.getPublisher(), article.getCanonicalUrl()),
                        article.getCanonicalUrl(),
                        article.getPublishedAt(),
                        aiAnalysisRepository.existsByNewsId(article.getId()),
                        article.getSource(),
                        article.getContentSource().name(),
                        article.getRightsProfile().name(),
                        article.isAiSummaryRequestAllowed(),
                        article.getFetchedAt()))
                .toList();
    }
}
