package com.finwatch.data.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import com.finwatch.data.provider.FinnhubNewsClient;
import com.finwatch.data.provider.KisMarketDataClient;
import com.finwatch.data.provider.NaverNewsSearchClient;
import com.finwatch.data.provider.ProviderException;
import com.finwatch.data.provider.ProviderResponses.Bar;
import com.finwatch.data.provider.ProviderResponses.BarSeries;
import com.finwatch.data.provider.ProviderResponses.CompanyNewsItem;
import com.finwatch.data.provider.ProviderResponses.CompanyNewsResult;
import com.finwatch.data.provider.ProviderResponses.NewsItem;
import com.finwatch.data.provider.ProviderResponses.NewsSearchResult;
import com.finwatch.news.domain.NewsArticle;
import com.finwatch.news.repository.NewsArticleRepository;
import com.finwatch.stock.domain.MarketPrice;
import com.finwatch.stock.domain.Stock;
import com.finwatch.stock.repository.MarketPriceRepository;
import com.finwatch.stock.repository.StockRepository;

class ExternalDataSyncServiceTest {

    private StockRepository stockRepository;
    private MarketPriceRepository marketPriceRepository;
    private NewsArticleRepository newsArticleRepository;
    private KisMarketDataClient kisMarketDataClient;
    private NaverNewsSearchClient naverNewsSearchClient;
    private FinnhubNewsClient finnhubNewsClient;

    @BeforeEach
    void setUp() {
        stockRepository = mock(StockRepository.class);
        marketPriceRepository = mock(MarketPriceRepository.class);
        newsArticleRepository = mock(NewsArticleRepository.class);
        kisMarketDataClient = mock(KisMarketDataClient.class);
        naverNewsSearchClient = mock(NaverNewsSearchClient.class);
        finnhubNewsClient = mock(FinnhubNewsClient.class);
    }

    @Test
    void demoModeNeverCallsExternalProviders() {
        Stock stock = stock(1L, "005930", "삼성전자", "KRX");
        when(stockRepository.findAllByActiveTrueOrderByMarketAscNameAsc()).thenReturn(List.of(stock));
        ExternalDataSyncService service = service("DEMO");

        var result = service.syncAll();

        assertThat(result.mode()).isEqualTo("DEMO");
        assertThat(result.stocks().getFirst().marketPrices().status()).isEqualTo("SKIPPED");
        verifyNoInteractions(kisMarketDataClient, naverNewsSearchClient, finnhubNewsClient);
    }

    @Test
    void liveKrxSyncPersistsNewKisBarsAndNaverMetadata() {
        Stock stock = stock(1L, "005930", "삼성전자", "KRX");
        when(stockRepository.findFirstBySymbolAndActiveTrue("005930")).thenReturn(Optional.of(stock));
        when(kisMarketDataClient.getDomesticDailyBars(anyString(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(new BarSeries(
                        "005930",
                        "1D",
                        "kis",
                        Instant.parse("2026-07-13T00:00:00Z"),
                        List.of(new Bar(
                                LocalDate.of(2026, 7, 11),
                                decimal("83000"),
                                decimal("85000"),
                                decimal("82000"),
                                decimal("84500"),
                                decimal("12345")))));
        when(naverNewsSearchClient.search("삼성전자", 30)).thenReturn(new NewsSearchResult(
                "삼성전자",
                1,
                1,
                1,
                Instant.parse("2026-07-13T00:00:00Z"),
                List.of(new NewsItem(
                        "삼성전자 실적 개선",
                        "요약",
                        "https://news.example.com/samsung",
                        "https://n.news.naver.com/samsung",
                        Instant.parse("2026-07-12T00:00:00Z"),
                        "naver-api-hub"))));
        when(newsArticleRepository.findBySourceAndExternalId(anyString(), anyString()))
                .thenReturn(Optional.empty());

        var result = service("LIVE").syncStock("005930");

        assertThat(result.pricesImported()).isEqualTo(1);
        assertThat(result.newsImported()).isEqualTo(1);
        ArgumentCaptor<List<MarketPrice>> prices = ArgumentCaptor.forClass(List.class);
        verify(marketPriceRepository).saveAll(prices.capture());
        assertThat(prices.getValue()).singleElement().extracting(MarketPrice::getSource).isEqualTo("KIS");
        ArgumentCaptor<List<NewsArticle>> news = ArgumentCaptor.forClass(List.class);
        verify(newsArticleRepository).saveAll(news.capture());
        assertThat(news.getValue()).singleElement()
                .satisfies(article -> {
                    assertThat(article.getSource()).isEqualTo("NAVER_API_HUB");
                    assertThat(article.isAiAnalysisAllowed()).isFalse();
                });
    }

    @Test
    void liveUsSyncPersistsFinnhubNewsAndKeepsDatabasePrices() {
        Stock stock = stock(2L, "AAPL", "Apple", "NASDAQ");
        when(stockRepository.findFirstBySymbolAndActiveTrue("AAPL")).thenReturn(Optional.of(stock));
        when(finnhubNewsClient.companyNews(anyString(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(new CompanyNewsResult(
                        "AAPL",
                        LocalDate.of(2026, 6, 13),
                        LocalDate.of(2026, 7, 13),
                        Instant.parse("2026-07-13T00:00:00Z"),
                        List.of(new CompanyNewsItem(
                                123L,
                                "AAPL",
                                "Apple expands AI infrastructure",
                                "Summary",
                                "https://news.example.com/apple",
                                "Reuters",
                                "company",
                                Instant.parse("2026-07-12T00:00:00Z"),
                                "finnhub"))));
        when(newsArticleRepository.findBySourceAndExternalId("FINNHUB", "123"))
                .thenReturn(Optional.empty());

        var result = service("LIVE").syncStock("aapl");

        assertThat(result.stocks().getFirst().marketPrices().status()).isEqualTo("SKIPPED");
        assertThat(result.newsImported()).isEqualTo(1);
        verify(marketPriceRepository, never()).saveAll(any());
        verify(newsArticleRepository).saveAll(any());
    }

    @Test
    void liveKisSyncReplacesSameSessionDemoBarInsteadOfCreatingDuplicateDay() {
        Stock stock = stock(1L, "005930", "삼성전자", "KRX");
        when(stockRepository.findFirstBySymbolAndActiveTrue("005930")).thenReturn(Optional.of(stock));
        Bar bar = new Bar(
                LocalDate.of(2026, 7, 11),
                decimal("83000"), decimal("85000"), decimal("82000"), decimal("84500"), decimal("12345"));
        when(kisMarketDataClient.getDomesticDailyBars(anyString(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(new BarSeries("005930", "1D", "kis", Instant.now(), List.of(bar)));
        MarketPrice existingDemo = MarketPrice.create(
                stock,
                "1D",
                decimal("1"), decimal("1"), decimal("1"), decimal("1"), decimal("1"),
                Instant.parse("2026-07-11T06:00:00Z"),
                "DEMO");
        when(marketPriceRepository.findAllByStockIdAndIntervalAndRecordedAtBetween(any(), anyString(), any(), any()))
                .thenReturn(List.of(existingDemo));
        when(naverNewsSearchClient.search(anyString(), anyInt())).thenReturn(new NewsSearchResult(
                "삼성전자", 0, 1, 0, Instant.now(), List.of()));

        var result = service("LIVE").syncStock("005930");

        assertThat(result.pricesImported()).isEqualTo(1);
        assertThat(existingDemo.getSource()).isEqualTo("KIS");
        assertThat(existingDemo.getClosePrice()).isEqualByComparingTo("84500");
        ArgumentCaptor<List<MarketPrice>> prices = ArgumentCaptor.forClass(List.class);
        verify(marketPriceRepository).saveAll(prices.capture());
        assertThat(prices.getValue()).isEmpty();
    }

    @Test
    void providerFailureReturnsFallbackInsteadOfBreakingExistingApis() {
        Stock stock = stock(1L, "005930", "삼성전자", "KRX");
        when(stockRepository.findFirstBySymbolAndActiveTrue("005930")).thenReturn(Optional.of(stock));
        when(kisMarketDataClient.getDomesticDailyBars(anyString(), any(LocalDate.class), any(LocalDate.class)))
                .thenThrow(new ProviderException(HttpStatus.SERVICE_UNAVAILABLE, "KIS_HTTP_429", "rate limited"));
        when(naverNewsSearchClient.search(anyString(), anyInt()))
                .thenThrow(new ProviderException(HttpStatus.SERVICE_UNAVAILABLE, "NAVER_UNAVAILABLE", "offline"));

        var result = service("LIVE").syncStock("005930");

        assertThat(result.stocks().getFirst().marketPrices().status()).isEqualTo("FALLBACK");
        assertThat(result.stocks().getFirst().news().status()).isEqualTo("FALLBACK");
        verify(marketPriceRepository, never()).saveAll(any());
        verify(newsArticleRepository, never()).saveAll(any());
    }

    private ExternalDataSyncService service(String mode) {
        return new ExternalDataSyncService(
                mode,
                stockRepository,
                marketPriceRepository,
                newsArticleRepository,
                kisMarketDataClient,
                naverNewsSearchClient,
                finnhubNewsClient);
    }

    private Stock stock(Long id, String symbol, String name, String market) {
        Stock stock = mock(Stock.class);
        when(stock.getId()).thenReturn(id);
        when(stock.getSymbol()).thenReturn(symbol);
        when(stock.getName()).thenReturn(name);
        when(stock.getMarket()).thenReturn(market);
        return stock;
    }

    private BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
