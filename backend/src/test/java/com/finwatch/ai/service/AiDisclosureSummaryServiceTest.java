package com.finwatch.ai.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.finwatch.ai.dto.AiDisclosureSummaryRequest;
import com.finwatch.news.domain.NewsArticle;
import com.finwatch.news.repository.NewsArticleRepository;
import com.finwatch.news.service.NewsContentIngestionService;

class AiDisclosureSummaryServiceTest {

    private final NewsArticleRepository repository = mock(NewsArticleRepository.class);
    private final NewsContentIngestionService ingestionService = mock(NewsContentIngestionService.class);
    private final AiNewsSummaryService newsSummaryService = mock(AiNewsSummaryService.class);
    private AiDisclosureSummaryService service;

    @BeforeEach
    void setUp() {
        service = new AiDisclosureSummaryService(repository, ingestionService, newsSummaryService);
    }

    @Test
    void fetchesOfficialDocumentBeforeSummarizingMetadataOnlyDisclosure() {
        NewsArticle disclosure = disclosure(false, null);
        when(repository.findById(42L)).thenReturn(Optional.of(disclosure));

        service.summarize(new AiDisclosureSummaryRequest(42L, null));

        verify(ingestionService).refresh(42L);
        verify(newsSummaryService).summarize(any());
    }

    @Test
    void reusesStoredContentWithoutFetchingDocumentAgain() {
        NewsArticle disclosure = disclosure(true, "content-hash");
        when(repository.findById(42L)).thenReturn(Optional.of(disclosure));

        service.summarize(new AiDisclosureSummaryRequest(42L, null));

        verify(ingestionService, never()).refresh(42L);
        verify(newsSummaryService).summarize(any());
    }

    private NewsArticle disclosure(boolean analysisAllowed, String contentHash) {
        NewsArticle disclosure = mock(NewsArticle.class);
        when(disclosure.getId()).thenReturn(42L);
        when(disclosure.getContentKind()).thenReturn("DISCLOSURE");
        when(disclosure.isAiAnalysisAllowed()).thenReturn(analysisAllowed);
        when(disclosure.getContentHash()).thenReturn(contentHash);
        return disclosure;
    }
}
