package com.finwatch.disclosure.service;

import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.finwatch.disclosure.dto.DisclosureResponse;
import com.finwatch.news.repository.NewsArticleRepository;
import com.finwatch.stock.repository.StockRepository;

@Service
@Transactional(readOnly = true)
public class DisclosureQueryService {

    private final StockRepository stockRepository;
    private final NewsArticleRepository newsArticleRepository;

    public DisclosureQueryService(StockRepository stockRepository, NewsArticleRepository newsArticleRepository) {
        this.stockRepository = stockRepository;
        this.newsArticleRepository = newsArticleRepository;
    }

    public List<DisclosureResponse> get(String market, String symbol) {
        String normalizedMarket = normalize(market);
        String normalizedSymbol = normalize(symbol);
        stockRepository.findByMarketAndSymbolAndActiveTrue(normalizedMarket, normalizedSymbol)
                .orElseThrow(() -> new DisclosureQueryException(
                        HttpStatus.NOT_FOUND,
                        "STOCK_NOT_FOUND",
                        "활성 종목을 찾을 수 없습니다: " + normalizedMarket + ":" + normalizedSymbol));
        return newsArticleRepository
                .findAllByStockMarketAndStockSymbolAndContentKindOrderByPublishedAtDesc(
                        normalizedMarket, normalizedSymbol, "DISCLOSURE")
                .stream()
                .map(article -> new DisclosureResponse(
                        article.getId(),
                        article.getStock().getMarket(),
                        article.getStock().getSymbol(),
                        article.getTitle(),
                        article.getPublisher(),
                        article.getCanonicalUrl(),
                        article.getPublishedAt(),
                        article.getSource(),
                        article.getDisclosureType(),
                        article.isAiAnalysisAllowed(),
                        article.getFetchedAt()))
                .toList();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
