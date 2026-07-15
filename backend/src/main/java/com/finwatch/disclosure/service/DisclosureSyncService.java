package com.finwatch.disclosure.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.finwatch.data.provider.ProviderException;
import com.finwatch.data.sync.DataSyncResponses.ProviderSyncResult;
import com.finwatch.disclosure.provider.DisclosureProvider;
import com.finwatch.news.domain.NewsArticle;
import com.finwatch.news.repository.NewsArticleRepository;
import com.finwatch.stock.domain.Stock;

@Service
public class DisclosureSyncService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final List<DisclosureProvider> providers;
    private final NewsArticleRepository newsArticleRepository;
    private final int lookbackDays;

    public DisclosureSyncService(
            List<DisclosureProvider> providers,
            NewsArticleRepository newsArticleRepository,
            @Value("${app.data.disclosures.lookback-days:365}") int lookbackDays) {
        this.providers = List.copyOf(providers);
        this.newsArticleRepository = newsArticleRepository;
        this.lookbackDays = Math.max(1, Math.min(3650, lookbackDays));
    }

    @Transactional
    public ProviderSyncResult sync(Stock stock) {
        DisclosureProvider provider = providers.stream()
                .filter(candidate -> candidate.supports(stock))
                .findFirst()
                .orElse(null);
        if (provider == null) {
            return ProviderSyncResult.skipped("UNSUPPORTED", "지원하는 공시 공급자가 없습니다.");
        }
        try {
            LocalDate today = LocalDate.now(SEOUL);
            var result = provider.fetch(stock, today.minusDays(lookbackDays), today);
            int imported = 0;
            for (var item : result.items()) {
                if (newsArticleRepository.findBySourceAndExternalId(item.source(), item.externalId()).isPresent()) {
                    continue;
                }
                newsArticleRepository.save(NewsArticle.createDisclosure(
                        stock,
                        item.externalId(),
                        item.title(),
                        item.publisher(),
                        item.url(),
                        item.publishedAt(),
                        item.source(),
                        item.disclosureType()));
                imported++;
            }
            return ProviderSyncResult.success(provider.providerId(), imported);
        } catch (ProviderException exception) {
            return ProviderSyncResult.fallback(
                    provider.providerId(),
                    exception.getCode() + ": 공시 수집에 실패해 기존 DB 데이터를 유지합니다.");
        }
    }
}
