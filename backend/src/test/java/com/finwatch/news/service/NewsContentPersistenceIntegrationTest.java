package com.finwatch.news.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.finwatch.ai.cache.AiSummaryCacheStore;
import com.finwatch.ai.dto.AiSummaryRequest;
import com.finwatch.ai.repository.AiAnalysisRepository;
import com.finwatch.ai.service.AiNewsSummaryService;
import com.finwatch.news.content.ContentSource;
import com.finwatch.news.content.FetchedArticleContent;
import com.finwatch.news.content.RightsProfile;
import com.finwatch.news.domain.NewsArticle;
import com.finwatch.news.dto.NewsContentRefreshResponse;
import com.finwatch.news.repository.NewsArticleRepository;

@SpringBootTest(properties = "app.ai.allowed-prompt-versions=content-refresh-test-v1")
@ActiveProfiles("demo")
@Transactional
class NewsContentPersistenceIntegrationTest {

    @Autowired
    private NewsArticleRepository newsArticleRepository;

    @Autowired
    private AiAnalysisRepository aiAnalysisRepository;

    @Autowired
    private AiNewsSummaryService aiNewsSummaryService;

    @Autowired
    private NewsContentPersistenceService persistenceService;

    @Autowired
    private AiSummaryCacheStore cacheStore;

    @Test
    void changedContentHashInvalidatesOldCacheAndCreatesNewAnalysisVersion() {
        NewsArticle article = newsArticleRepository.findAllByStockSymbolOrderByPublishedAtDesc("000660")
                .stream()
                .filter(NewsArticle::isAiAnalysisAllowed)
                .findFirst()
                .orElseThrow();
        String promptVersion = "content-refresh-test-v1";
        String previousHash = article.getContentHash();
        String previousCacheKey = cacheKey(article.getId(), previousHash, promptVersion);

        var first = aiNewsSummaryService.summarize(new AiSummaryRequest(article.getId(), promptVersion));
        assertThat(first.cacheHit()).isFalse();
        assertThat(cacheStore.get(previousCacheKey)).isPresent();

        String newHash = "b".repeat(64);
        FetchedArticleContent fetched = new FetchedArticleContent(
                URI.create(article.getCanonicalUrl()),
                URI.create(article.getCanonicalUrl()),
                article.getTitle(),
                "새 공시 본문입니다. 실적 개선과 함께 원가 변동 위험이 명시되었습니다.",
                "text/html",
                Instant.parse("2026-07-13T06:00:00Z"),
                "fixture-v2",
                null,
                newHash,
                "fixture-extractor-v2",
                ContentSource.OFFICIAL_DISCLOSURE,
                RightsProfile.STORE_FOR_AI);

        NewsContentRefreshResponse refreshed = persistenceService.persist(article.getId(), fetched);
        assertThat(refreshed.contentChanged()).isTrue();
        assertThat(refreshed.previousContentHash()).isEqualTo(previousHash);
        assertThat(refreshed.contentHash()).isEqualTo(newHash);
        assertThat(cacheStore.get(previousCacheKey)).isEmpty();

        var second = aiNewsSummaryService.summarize(new AiSummaryRequest(article.getId(), promptVersion));
        assertThat(second.cacheHit()).isFalse();
        assertThat(aiAnalysisRepository.findByNewsIdAndFeatureTypeAndPromptVersionAndContentHash(
                article.getId(), "NEWS_SUMMARY", promptVersion, previousHash)).isPresent();
        assertThat(aiAnalysisRepository.findByNewsIdAndFeatureTypeAndPromptVersionAndContentHash(
                article.getId(), "NEWS_SUMMARY", promptVersion, newHash)).isPresent();

        cacheStore.delete(cacheKey(article.getId(), newHash, promptVersion));
    }

    private String cacheKey(Long newsId, String contentHash, String promptVersion) {
        return "ai:news-summary:" + newsId + ":" + contentHash + ":" + promptVersion;
    }
}
