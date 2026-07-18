package com.finwatch.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import com.finwatch.ai.repository.AiAnalysisRepository;
import com.finwatch.content.dto.ContentFeedRequest;
import com.finwatch.news.domain.NewsArticle;
import com.finwatch.news.repository.NewsArticleRepository;
import com.finwatch.stock.repository.StockRepository;

class ContentFeedServiceTest {

    private NewsArticleRepository newsArticleRepository;
    private ContentFeedService service;

    @BeforeEach
    void setUp() {
        newsArticleRepository = mock(NewsArticleRepository.class);
        AiAnalysisRepository aiAnalysisRepository = mock(AiAnalysisRepository.class);
        StockRepository stockRepository = mock(StockRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-07-15T12:00:00Z"), ZoneOffset.UTC);
        service = new ContentFeedService(
                newsArticleRepository,
                aiAnalysisRepository,
                stockRepository,
                clock);
    }

    @Test
    void rejectsInvalidQueryPaginationSortAndSymbolCombinations() {
        assertError(request(null, null, null, "x", null, null, null, null, null, null, null, null),
                "CONTENT_FILTER_INVALID");
        assertError(request(null, null, "000660", null, null, null, null, null, null, null, null, null),
                "CONTENT_FILTER_INVALID");
        assertError(request(null, null, null, null, null, null, null, null, null, "-1", null, null),
                "PAGE_INVALID");
        assertError(request(null, null, null, null, null, null, null, null, null, null, "51", null),
                "PAGE_INVALID");
        assertError(request(null, null, null, null, null, null, null, null, null, "NaN", null, null),
                "PAGE_INVALID");
        assertError(request(null, null, null, null, null, null, null, null, null, null, null, "title,desc"),
                "SORT_NOT_ALLOWED");
    }

    @Test
    void rejectsInvalidCustomPeriodsAndDatesOutsideCustomPeriod() {
        assertError(request(null, null, null, null, "CUSTOM", null, null, null, null, null, null, null),
                "CONTENT_FILTER_INVALID");
        assertError(request(null, null, null, null, "CUSTOM", "2026-07-15", "2026-07-01",
                null, null, null, null, null), "CONTENT_FILTER_INVALID");
        assertError(request(null, null, null, null, "CUSTOM", "2026/07/01", "2026-07-15",
                null, null, null, null, null), "CONTENT_FILTER_INVALID");
        assertError(request(null, null, null, null, "7D", "2026-07-01", "2026-07-15",
                null, null, null, null, null), "CONTENT_FILTER_INVALID");
    }

    @Test
    @SuppressWarnings("unchecked")
    void appliesWhitelistedStableSortAndBoundedPage() {
        when(newsArticleRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.get(request(
                "NEWS", "ALL", null, "memory", "CUSTOM", "2026-07-01", "2026-07-15",
                "DEMO", "ALL", "2", "10", "publishedAt,asc"));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(newsArticleRepository).findAll(any(Specification.class), captor.capture());
        Pageable pageable = captor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(10);
        assertThat(pageable.getSort().getOrderFor("publishedAt").getDirection()).isEqualTo(Sort.Direction.ASC);
        assertThat(pageable.getSort().getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    private void assertError(ContentFeedRequest request, String code) {
        assertThatThrownBy(() -> service.get(request))
                .isInstanceOfSatisfying(ContentFeedException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(code));
    }

    private ContentFeedRequest request(
            String kind,
            String market,
            String symbol,
            String query,
            String period,
            String from,
            String to,
            String source,
            String analysis,
            String page,
            String size,
            String sort) {
        return new ContentFeedRequest(
                kind, market, symbol, query, period, from, to,
                source, analysis, page, size, sort);
    }
}
