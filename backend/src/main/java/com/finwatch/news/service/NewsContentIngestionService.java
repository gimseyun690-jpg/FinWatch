package com.finwatch.news.service;

import java.net.URI;
import java.net.URISyntaxException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.finwatch.news.content.ArticleContentFetcher;
import com.finwatch.news.content.FetchedArticleContent;
import com.finwatch.news.content.NewsContentException;
import com.finwatch.news.content.OpenDartDocumentFetcher;
import com.finwatch.news.content.SourceFetchMode;
import com.finwatch.news.content.SourcePolicyDecision;
import com.finwatch.news.content.SourcePolicyResolver;
import com.finwatch.news.domain.NewsArticle;
import com.finwatch.news.dto.NewsContentRefreshResponse;
import com.finwatch.news.repository.NewsArticleRepository;

@Service
public class NewsContentIngestionService {

    private final NewsArticleRepository newsArticleRepository;
    private final SourcePolicyResolver sourcePolicyResolver;
    private final ArticleContentFetcher articleContentFetcher;
    private final OpenDartDocumentFetcher openDartDocumentFetcher;
    private final NewsContentPersistenceService persistenceService;

    public NewsContentIngestionService(
            NewsArticleRepository newsArticleRepository,
            SourcePolicyResolver sourcePolicyResolver,
            ArticleContentFetcher articleContentFetcher,
            OpenDartDocumentFetcher openDartDocumentFetcher,
            NewsContentPersistenceService persistenceService) {
        this.newsArticleRepository = newsArticleRepository;
        this.sourcePolicyResolver = sourcePolicyResolver;
        this.articleContentFetcher = articleContentFetcher;
        this.openDartDocumentFetcher = openDartDocumentFetcher;
        this.persistenceService = persistenceService;
    }

    public NewsContentRefreshResponse refresh(Long newsId) {
        NewsArticle article = newsArticleRepository.findById(newsId)
                .orElseThrow(() -> new NewsContentException(
                        HttpStatus.NOT_FOUND,
                        "NEWS_NOT_FOUND",
                        "뉴스를 찾을 수 없습니다."));
        URI canonicalUrl = parseUri(article.getCanonicalUrl());
        FetchedArticleContent fetchedContent;

        if ("OPENDART".equalsIgnoreCase(article.getSource())) {
            SourcePolicyDecision policy = sourcePolicyResolver.resolve(OpenDartDocumentFetcher.DOCUMENT_ENDPOINT);
            fetchedContent = openDartDocumentFetcher.fetch(article.getExternalId(), canonicalUrl, policy);
        } else {
            SourcePolicyDecision policy = sourcePolicyResolver.resolve(canonicalUrl);
            if (policy.fetchMode() != SourceFetchMode.ALLOWLIST_FETCH) {
                throw NewsContentException.unavailable();
            }
            fetchedContent = articleContentFetcher.fetch(canonicalUrl);
        }
        return persistenceService.persist(newsId, fetchedContent);
    }

    public NewsContentRefreshResponse refreshForAiSummary(Long newsId) {
        NewsArticle article = newsArticleRepository.findById(newsId)
                .orElseThrow(() -> new NewsContentException(
                        HttpStatus.NOT_FOUND,
                        "NEWS_NOT_FOUND",
                        "뉴스를 찾을 수 없습니다."));
        if (!"NEWS".equals(article.getContentKind())) {
            throw new NewsContentException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "NEWS_REQUIRED",
                    "일반 뉴스만 뉴스 요약 API에서 분석할 수 있습니다.");
        }
        URI canonicalUrl = upgradeToHttps(parseUri(article.getCanonicalUrl()));
        FetchedArticleContent fetchedContent = articleContentFetcher.fetchForAiSummary(canonicalUrl);
        return persistenceService.persist(newsId, fetchedContent);
    }

    private URI upgradeToHttps(URI source) {
        if ("https".equalsIgnoreCase(source.getScheme())) {
            return source;
        }
        if (!"http".equalsIgnoreCase(source.getScheme())
                || source.getHost() == null
                || source.getHost().isBlank()
                || source.getUserInfo() != null
                || (source.getPort() != -1 && source.getPort() != 80)) {
            throw new NewsContentException(
                    HttpStatus.FORBIDDEN,
                    "ARTICLE_URL_SCHEME_BLOCKED",
                    "뉴스 원문은 안전한 HTTPS 주소에서만 수집할 수 있습니다.");
        }
        try {
            return new URI(
                    "https",
                    null,
                    source.getHost(),
                    -1,
                    source.getPath(),
                    source.getQuery(),
                    null);
        } catch (URISyntaxException exception) {
            throw new NewsContentException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "ARTICLE_URL_INVALID",
                    "뉴스 원문 주소를 HTTPS 주소로 변환할 수 없습니다.",
                    exception);
        }
    }

    private URI parseUri(String value) {
        try {
            return URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw new NewsContentException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "ARTICLE_URL_INVALID",
                    "뉴스 원문 주소가 올바르지 않습니다.",
                    exception);
        }
    }
}
