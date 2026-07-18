package com.finwatch.news.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.finwatch.news.content.ArticleContentFetcher;
import com.finwatch.news.content.FetchedArticleContent;
import com.finwatch.news.content.OpenDartDocumentFetcher;
import com.finwatch.news.content.SourcePolicyResolver;
import com.finwatch.news.domain.NewsArticle;
import com.finwatch.news.repository.NewsArticleRepository;

class NewsContentIngestionServiceTest {

    private final NewsArticleRepository repository = mock(NewsArticleRepository.class);
    private final SourcePolicyResolver policyResolver = mock(SourcePolicyResolver.class);
    private final ArticleContentFetcher articleContentFetcher = mock(ArticleContentFetcher.class);
    private final OpenDartDocumentFetcher openDartDocumentFetcher = mock(OpenDartDocumentFetcher.class);
    private final NewsContentPersistenceService persistenceService = mock(NewsContentPersistenceService.class);
    private NewsContentIngestionService service;

    @BeforeEach
    void setUp() {
        service = new NewsContentIngestionService(
                repository,
                policyResolver,
                articleContentFetcher,
                openDartDocumentFetcher,
                persistenceService);
    }

    @Test
    void upgradesDefaultPortHttpNewsUrlToHttpsBeforeOnDemandFetch() {
        NewsArticle news = mock(NewsArticle.class);
        when(news.getId()).thenReturn(42L);
        when(news.getContentKind()).thenReturn("NEWS");
        when(news.getCanonicalUrl()).thenReturn("http://www.breaknews.com/12345?from=naver");
        when(repository.findById(42L)).thenReturn(Optional.of(news));
        FetchedArticleContent fetchedContent = mock(FetchedArticleContent.class);
        when(articleContentFetcher.fetchForAiSummary(any())).thenReturn(fetchedContent);

        service.refreshForAiSummary(42L);

        ArgumentCaptor<URI> uri = ArgumentCaptor.forClass(URI.class);
        verify(articleContentFetcher).fetchForAiSummary(uri.capture());
        assertThat(uri.getValue().getScheme()).isEqualTo("https");
        assertThat(uri.getValue().getHost()).isEqualTo("www.breaknews.com");
        assertThat(uri.getValue().getPath()).isEqualTo("/12345");
        assertThat(uri.getValue().getQuery()).isEqualTo("from=naver");
        assertThat(uri.getValue().getPort()).isEqualTo(-1);
        verify(persistenceService).persist(42L, fetchedContent);
    }
}
