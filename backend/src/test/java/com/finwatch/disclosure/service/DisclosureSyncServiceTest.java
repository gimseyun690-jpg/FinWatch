package com.finwatch.disclosure.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.finwatch.disclosure.provider.DisclosureProvider;
import com.finwatch.disclosure.provider.DisclosureProviderResponses.DisclosureFetchResult;
import com.finwatch.disclosure.provider.DisclosureProviderResponses.DisclosureItem;
import com.finwatch.news.domain.NewsArticle;
import com.finwatch.news.repository.NewsArticleRepository;
import com.finwatch.stock.domain.Stock;

class DisclosureSyncServiceTest {

    @Test
    void persistsOfficialDisclosureMetadataWithoutDuplicates() {
        Stock stock = mock(Stock.class);
        DisclosureProvider provider = mock(DisclosureProvider.class);
        NewsArticleRepository repository = mock(NewsArticleRepository.class);
        when(provider.supports(stock)).thenReturn(true);
        when(provider.providerId()).thenReturn("OPENDART");
        when(provider.fetch(any(), any(LocalDate.class), any(LocalDate.class))).thenReturn(new DisclosureFetchResult(
                "OPENDART",
                Instant.now(),
                List.of(new DisclosureItem(
                        "20260714000123",
                        "반기보고서",
                        "삼성전자",
                        "https://dart.fss.or.kr/dsaf001/main.do?rcpNo=20260714000123",
                        Instant.now(),
                        "A",
                        "OPENDART"))));
        when(repository.findBySourceAndExternalId("OPENDART", "20260714000123"))
                .thenReturn(Optional.empty());

        var result = new DisclosureSyncService(List.of(provider), repository, 365).sync(stock);

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.imported()).isEqualTo(1);
        ArgumentCaptor<NewsArticle> article = ArgumentCaptor.forClass(NewsArticle.class);
        verify(repository).save(article.capture());
        assertThat(article.getValue().getContentKind()).isEqualTo("DISCLOSURE");
        assertThat(article.getValue().getDisclosureType()).isEqualTo("A");
        assertThat(article.getValue().getSource()).isEqualTo("OPENDART");
    }
}
