package com.finwatch.ai.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.finwatch.ai.dto.AiSummaryRequest;
import com.finwatch.news.content.NewsContentException;
import com.finwatch.news.domain.NewsArticle;
import com.finwatch.news.repository.NewsArticleRepository;
import com.finwatch.news.service.NewsContentIngestionService;

class AiNewsSummaryOrchestrationServiceTest {

    private final NewsArticleRepository repository = mock(NewsArticleRepository.class);
    private final NewsContentIngestionService ingestionService = mock(NewsContentIngestionService.class);
    private final AiNewsSummaryService newsSummaryService = mock(AiNewsSummaryService.class);
    private AiNewsSummaryOrchestrationService service;

    @BeforeEach
    void setUp() {
        service = new AiNewsSummaryOrchestrationService(repository, ingestionService, newsSummaryService);
    }

    @Test
    void fetchesOriginalArticleBeforeSummarizingMetadataOnlyNews() {
        NewsArticle news = news(false, null);
        when(repository.findById(42L)).thenReturn(Optional.of(news));

        service.summarize(new AiSummaryRequest(42L, null));

        verify(ingestionService).refreshForAiSummary(42L);
        verify(newsSummaryService).summarize(any());
    }

    @Test
    void reusesStoredContentWithoutFetchingOriginalArticleAgain() {
        NewsArticle news = news(true, "content-hash");
        when(repository.findById(42L)).thenReturn(Optional.of(news));

        service.summarize(new AiSummaryRequest(42L, null));

        verify(ingestionService, never()).refreshForAiSummary(42L);
        verify(newsSummaryService).summarize(any());
    }

    @Test
    void rejectsDisclosureAtNewsSummaryEndpoint() {
        NewsArticle disclosure = mock(NewsArticle.class);
        when(disclosure.getContentKind()).thenReturn("DISCLOSURE");
        when(repository.findById(42L)).thenReturn(Optional.of(disclosure));

        assertThatThrownBy(() -> service.summarize(new AiSummaryRequest(42L, null)))
                .isInstanceOfSatisfying(NewsContentException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.getCode())
                                .isEqualTo("NEWS_REQUIRED"));

        verify(ingestionService, never()).refreshForAiSummary(42L);
        verify(newsSummaryService, never()).summarize(any());
    }

    private NewsArticle news(boolean analysisAllowed, String contentHash) {
        NewsArticle news = mock(NewsArticle.class);
        when(news.getId()).thenReturn(42L);
        when(news.getContentKind()).thenReturn("NEWS");
        when(news.isAiAnalysisAllowed()).thenReturn(analysisAllowed);
        when(news.getContentHash()).thenReturn(contentHash);
        return news;
    }
}
