package com.finwatch.news.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.finwatch.ai.cache.AiSummaryCacheStore;
import com.finwatch.ai.domain.AiAnalysis;
import com.finwatch.ai.repository.AiAnalysisRepository;
import com.finwatch.news.content.FetchedArticleContent;
import com.finwatch.news.content.NewsContentException;
import com.finwatch.news.domain.NewsArticle;
import com.finwatch.news.dto.NewsContentRefreshResponse;
import com.finwatch.news.repository.NewsArticleRepository;

@Service
public class NewsContentPersistenceService {

    private final NewsArticleRepository newsArticleRepository;
    private final AiAnalysisRepository aiAnalysisRepository;
    private final AiSummaryCacheStore cacheStore;

    public NewsContentPersistenceService(
            NewsArticleRepository newsArticleRepository,
            AiAnalysisRepository aiAnalysisRepository,
            AiSummaryCacheStore cacheStore) {
        this.newsArticleRepository = newsArticleRepository;
        this.aiAnalysisRepository = aiAnalysisRepository;
        this.cacheStore = cacheStore;
    }

    @Transactional
    public NewsContentRefreshResponse persist(Long newsId, FetchedArticleContent fetchedContent) {
        NewsArticle article = newsArticleRepository.findById(newsId)
                .orElseThrow(() -> new NewsContentException(
                        HttpStatus.NOT_FOUND,
                        "NEWS_NOT_FOUND",
                        "뉴스를 찾을 수 없습니다."));
        String previousHash = article.getContentHash();
        List<AiAnalysis> previousAnalyses = previousHash == null || previousHash.isBlank()
                ? List.of()
                : aiAnalysisRepository.findAllByNewsIdAndContentHash(newsId, previousHash);

        boolean changed = article.applyFetchedContent(fetchedContent);
        if (changed) {
            previousAnalyses.forEach(analysis -> cacheStore.delete(analysis.getCacheKey()));
        }

        return new NewsContentRefreshResponse(
                article.getId(),
                changed,
                previousHash,
                article.getContentHash(),
                article.getContentSource().name(),
                article.getRightsProfile().name(),
                article.getCanonicalUrl(),
                article.getFinalUrl(),
                article.getExtractorVersion(),
                article.getContent().length(),
                article.getFetchedAt());
    }
}
