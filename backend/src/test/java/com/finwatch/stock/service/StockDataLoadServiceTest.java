package com.finwatch.stock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.finwatch.data.provider.FinnhubMarketDataClient;
import com.finwatch.data.provider.KisMarketDataClient;
import com.finwatch.data.provider.YahooFinanceMarketDataClient;
import com.finwatch.data.sync.DataSyncResponses.DataSyncResponse;
import com.finwatch.data.sync.DataSyncResponses.ProviderSyncResult;
import com.finwatch.data.sync.DataSyncResponses.StockSyncResult;
import com.finwatch.data.sync.ExternalDataSyncService;
import com.finwatch.disclosure.service.DisclosureSyncService;
import com.finwatch.news.repository.NewsArticleRepository;
import com.finwatch.realtime.RealtimeQuoteHub;
import com.finwatch.stock.domain.Stock;
import com.finwatch.stock.dto.StockDataLoadResponses.DataLoadResource;
import com.finwatch.stock.repository.MarketPriceRepository;
import com.finwatch.stock.repository.StockRepository;

class StockDataLoadServiceTest {

    private StockRepository stockRepository;
    private MarketPriceRepository marketPriceRepository;
    private NewsArticleRepository newsArticleRepository;
    private ExternalDataSyncService externalDataSyncService;
    private DisclosureSyncService disclosureSyncService;
    private KisMarketDataClient kisMarketDataClient;
    private FinnhubMarketDataClient finnhubMarketDataClient;
    private YahooFinanceMarketDataClient yahooFinanceMarketDataClient;
    private Stock stock;
    private StockDataLoadService service;

    @BeforeEach
    void setUp() {
        stockRepository = mock(StockRepository.class);
        marketPriceRepository = mock(MarketPriceRepository.class);
        newsArticleRepository = mock(NewsArticleRepository.class);
        externalDataSyncService = mock(ExternalDataSyncService.class);
        disclosureSyncService = mock(DisclosureSyncService.class);
        kisMarketDataClient = mock(KisMarketDataClient.class);
        finnhubMarketDataClient = mock(FinnhubMarketDataClient.class);
        yahooFinanceMarketDataClient = mock(YahooFinanceMarketDataClient.class);
        stock = mock(Stock.class);
        when(stock.getId()).thenReturn(1L);
        when(stock.getMarket()).thenReturn("KRX");
        when(stock.getSymbol()).thenReturn("005930");
        when(stockRepository.findByMarketAndSymbolAndActiveTrue("KRX", "005930"))
                .thenReturn(Optional.of(stock));
    }

    @AfterEach
    void tearDown() {
        if (service != null) {
            service.shutdown();
        }
    }

    @Test
    void concurrentRequestsReuseOneInFlightProviderCallAndJobId() throws Exception {
        CountDownLatch providerEntered = new CountDownLatch(1);
        CountDownLatch releaseProvider = new CountDownLatch(1);
        when(externalDataSyncService.syncStock("KRX", "005930")).thenAnswer(invocation -> {
            providerEntered.countDown();
            releaseProvider.await(3, TimeUnit.SECONDS);
            return syncResult(ProviderSyncResult.success("KIS", 3), ProviderSyncResult.success("NAVER", 0));
        });
        service = service("LIVE");

        var first = service.start("KRX", "005930", Set.of(DataLoadResource.DAILY_PRICES));
        assertThat(providerEntered.await(2, TimeUnit.SECONDS)).isTrue();
        var second = service.start("KRX", "005930", Set.of(DataLoadResource.DAILY_PRICES));

        assertThat(second.response().jobId()).isEqualTo(first.response().jobId());
        assertThat(second.response().reused()).isTrue();
        releaseProvider.countDown();
        var completed = awaitCompleted(first.response().jobId());
        assertThat(completed.status()).isEqualTo("READY");
        verify(externalDataSyncService).syncStock("KRX", "005930");
    }

    @Test
    void demoMetadataOnlyStockIsExplicitlyRejected() {
        service = service("DEMO");
        when(marketPriceRepository.existsByStockId(1L)).thenReturn(false);
        when(newsArticleRepository.existsByStockId(1L)).thenReturn(false);

        assertThatThrownBy(() -> service.start(
                "KRX", "005930", Set.of(DataLoadResource.QUOTE, DataLoadResource.DAILY_PRICES)))
                .isInstanceOf(StockDataLoadException.class)
                .extracting("code")
                .isEqualTo("DATA_NOT_SUPPORTED");
    }

    @Test
    void disclosureResourceUsesOfficialProviderSync() throws Exception {
        service = service("LIVE");
        when(disclosureSyncService.sync(stock)).thenReturn(ProviderSyncResult.success("OPENDART", 2));

        var dispatch = service.start("KRX", "005930", Set.of(DataLoadResource.DISCLOSURES));
        var completed = awaitCompleted(dispatch.response().jobId());

        assertThat(completed.status()).isEqualTo("READY");
        assertThat(completed.resources()).singleElement().satisfies(resource -> {
            assertThat(resource.resource()).isEqualTo(DataLoadResource.DISCLOSURES);
            assertThat(resource.provider()).isEqualTo("OPENDART");
            assertThat(resource.imported()).isEqualTo(2);
        });
        verify(disclosureSyncService).sync(stock);
    }

    private StockDataLoadService service(String mode) {
        return new StockDataLoadService(
                mode,
                stockRepository,
                marketPriceRepository,
                newsArticleRepository,
                externalDataSyncService,
                disclosureSyncService,
                kisMarketDataClient,
                finnhubMarketDataClient,
                yahooFinanceMarketDataClient,
                new RealtimeQuoteHub(),
                Duration.ofSeconds(15),
                Duration.ofHours(12),
                Duration.ofMinutes(15));
    }

    private DataSyncResponse syncResult(ProviderSyncResult prices, ProviderSyncResult news) {
        Instant now = Instant.now();
        return new DataSyncResponse(
                "LIVE", now, now, prices.imported(), news.imported(),
                List.of(new StockSyncResult("005930", "KRX", prices, news)));
    }

    private com.finwatch.stock.dto.StockDataLoadResponses.DataLoadResponse awaitCompleted(String jobId)
            throws InterruptedException {
        for (int attempt = 0; attempt < 100; attempt++) {
            var response = service.get("KRX", "005930", jobId);
            if (!"SYNCING".equals(response.status())) {
                return response;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("data load did not complete");
    }
}
