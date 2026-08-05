package com.finwatch.data.sync;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.finwatch.data.provider.FinnhubNewsClient;
import com.finwatch.data.provider.FinnhubMarketDataClient;
import com.finwatch.data.provider.KisMarketDataClient;
import com.finwatch.data.provider.NaverNewsSearchClient;
import com.finwatch.data.provider.ProviderException;
import com.finwatch.data.provider.ProviderResponses.Bar;
import com.finwatch.data.provider.ProviderResponses.CompanyNewsItem;
import com.finwatch.data.provider.ProviderResponses.NewsItem;
import com.finwatch.data.sync.DataSyncResponses.DataSyncResponse;
import com.finwatch.data.sync.DataSyncResponses.ProviderSyncResult;
import com.finwatch.data.sync.DataSyncResponses.StockSyncResult;
import com.finwatch.news.domain.NewsArticle;
import com.finwatch.news.service.NewsPublisherName;
import com.finwatch.news.repository.NewsArticleRepository;
import com.finwatch.stock.domain.MarketPrice;
import com.finwatch.stock.domain.Stock;
import com.finwatch.stock.repository.MarketPriceRepository;
import com.finwatch.stock.repository.StockRepository;

@Service
public class ExternalDataSyncService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    private static final LocalTime KRX_CLOSE = LocalTime.of(15, 30);
    private static final LocalTime NXT_CLOSE = LocalTime.of(20, 0);
    private static final LocalTime US_CLOSE = LocalTime.of(16, 0);
    private static final int MARKET_LOOKBACK_DAYS = 5 * 366;
    private static final int NEWS_LOOKBACK_DAYS = 30;

    private final DataMode dataMode;
    private final StockRepository stockRepository;
    private final MarketPriceRepository marketPriceRepository;
    private final NewsArticleRepository newsArticleRepository;
    private final KisMarketDataClient kisMarketDataClient;
    private final NaverNewsSearchClient naverNewsSearchClient;
    private final FinnhubNewsClient finnhubNewsClient;
    private final FinnhubMarketDataClient finnhubMarketDataClient;

    public ExternalDataSyncService(
            @Value("${app.data.mode:DEMO}") String dataMode,
            StockRepository stockRepository,
            MarketPriceRepository marketPriceRepository,
            NewsArticleRepository newsArticleRepository,
            KisMarketDataClient kisMarketDataClient,
            NaverNewsSearchClient naverNewsSearchClient,
            FinnhubNewsClient finnhubNewsClient,
            FinnhubMarketDataClient finnhubMarketDataClient) {
        this.dataMode = DataMode.from(dataMode);
        this.stockRepository = stockRepository;
        this.marketPriceRepository = marketPriceRepository;
        this.newsArticleRepository = newsArticleRepository;
        this.kisMarketDataClient = kisMarketDataClient;
        this.naverNewsSearchClient = naverNewsSearchClient;
        this.finnhubNewsClient = finnhubNewsClient;
        this.finnhubMarketDataClient = finnhubMarketDataClient;
    }

    @Transactional
    public DataSyncResponse syncAll() {
        return sync(stockRepository.findAllByActiveTrueOrderByMarketAscNameAsc());
    }

    @Transactional
    public DataSyncResponse syncStock(String symbol) {
        String normalized = symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
        Stock stock = stockRepository.findFirstBySymbolAndActiveTrue(normalized)
                .orElseThrow(() -> new DataSyncException(
                        HttpStatus.NOT_FOUND,
                        "STOCK_NOT_FOUND",
                        "활성 종목을 찾을 수 없습니다: " + normalized));
        return sync(List.of(stock));
    }

    @Transactional
    public DataSyncResponse syncStock(String market, String symbol) {
        String normalizedMarket = market == null ? "" : market.trim().toUpperCase(Locale.ROOT);
        String normalizedSymbol = symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
        Stock stock = stockRepository.findByMarketAndSymbolAndActiveTrue(normalizedMarket, normalizedSymbol)
                .orElseThrow(() -> new DataSyncException(
                        HttpStatus.NOT_FOUND,
                        "STOCK_NOT_FOUND",
                        "활성 종목을 찾을 수 없습니다: " + normalizedMarket + ":" + normalizedSymbol));
        return sync(List.of(stock));
    }

    @Transactional
    public void syncNewsOnly(Stock stock) {
        if (dataMode == DataMode.DEMO) return;
        if ("KRX".equalsIgnoreCase(stock.getMarket())) {
            syncNaverNews(stock);
        } else if (isUsMarket(stock.getMarket())) {
            syncFinnhubNews(stock);
        }
    }

    public com.finwatch.data.provider.ProviderResponses.Quote fetchLiveQuote(Stock stock) {
        if (dataMode == DataMode.DEMO) return null;
        if ("KRX".equalsIgnoreCase(stock.getMarket())) {
            return kisMarketDataClient.getDomesticQuote(stock.getSymbol());
        } else if (isUsMarket(stock.getMarket())) {
            try {
                return kisMarketDataClient.getOverseasQuote(stock.getMarket(), stock.getSymbol());
            } catch (RuntimeException fallbackException) {
                return finnhubMarketDataClient.quote(stock.getSymbol());
            }
        }
        return null;
    }

    private DataSyncResponse sync(List<Stock> stocks) {
        Instant startedAt = Instant.now();
        List<StockSyncResult> results = new ArrayList<>();
        for (Stock stock : stocks) {
            results.add(sync(stock));
        }
        int pricesImported = results.stream().mapToInt(result -> result.marketPrices().imported()).sum();
        int newsImported = results.stream().mapToInt(result -> result.news().imported()).sum();
        return new DataSyncResponse(
                dataMode.name(),
                startedAt,
                Instant.now(),
                pricesImported,
                newsImported,
                List.copyOf(results));
    }

    private StockSyncResult sync(Stock stock) {
        if (dataMode == DataMode.DEMO) {
            ProviderSyncResult skipped = ProviderSyncResult.skipped(
                    "DEMO",
                    "DATA_MODE=DEMO이므로 외부 호출 없이 기존 DB 데이터를 사용합니다.");
            return new StockSyncResult(stock.getSymbol(), stock.getMarket(), skipped, skipped);
        }

        if ("KRX".equalsIgnoreCase(stock.getMarket())) {
            return new StockSyncResult(
                    stock.getSymbol(),
                    stock.getMarket(),
                    syncKisPrices(stock),
                    syncNaverNews(stock));
        }
        if (isUsMarket(stock.getMarket())) {
            return new StockSyncResult(
                    stock.getSymbol(),
                    stock.getMarket(),
                    syncUsPrices(stock),
                    syncFinnhubNews(stock));
        }
        return new StockSyncResult(
                stock.getSymbol(),
                stock.getMarket(),
                ProviderSyncResult.skipped("UNSUPPORTED", "지원하지 않는 시장입니다."),
                ProviderSyncResult.skipped("UNSUPPORTED", "지원하지 않는 시장입니다."));
    }

    private ProviderSyncResult syncKisPrices(Stock stock) {
        try {
            LocalDate today = LocalDate.now(SEOUL);
            var series = kisMarketDataClient
                    .getDomesticDailyBars(stock.getSymbol(), today.minusDays(MARKET_LOOKBACK_DAYS), today);
            var domesticMarket = kisMarketDataClient.domesticMarket();
            return persistBars(
                    stock,
                    series.items(),
                    domesticMarket == null ? "KIS" : domesticMarket.persistenceSource(),
                    SEOUL,
                    domesticMarket != null && domesticMarket.includesNxt() ? NXT_CLOSE : KRX_CLOSE);
        } catch (ProviderException exception) {
            var domesticMarket = kisMarketDataClient.domesticMarket();
            return ProviderSyncResult.fallback(
                    domesticMarket == null ? "KIS" : domesticMarket.persistenceSource(),
                    fallbackMessage(exception));
        }
    }

    private ProviderSyncResult syncNaverNews(Stock stock) {
        try {
            List<NewsArticle> imported = new ArrayList<>();
            var result = naverNewsSearchClient.search(stock.getName(), 30);
            for (NewsItem item : result.items()) {
                String url = firstNonBlank(item.originalUrl(), item.naverUrl());
                if (item.title() == null || item.title().isBlank() || url.isBlank()) {
                    continue;
                }
                String externalId = sha256("NAVER_API_HUB|" + url);
                if (newsArticleRepository.findBySourceAndExternalId("NAVER_API_HUB", externalId).isPresent()) {
                    continue;
                }
                imported.add(NewsArticle.createMetadata(
                        stock,
                        externalId,
                        truncate(item.title(), 500),
                        NewsPublisherName.resolve(null, url),
                        truncate(url, 1000),
                        item.publishedAt() == null ? result.fetchedAt() : item.publishedAt(),
                        "NAVER_API_HUB"));
            }
            newsArticleRepository.saveAll(imported);
            return ProviderSyncResult.success("NAVER_API_HUB", imported.size());
        } catch (ProviderException exception) {
            return ProviderSyncResult.fallback("NAVER_API_HUB", fallbackMessage(exception));
        }
    }

    private ProviderSyncResult syncUsPrices(Stock stock) {
        ProviderException kisFailure = null;
        try {
            LocalDate today = LocalDate.now(NEW_YORK);
            var series = kisMarketDataClient.getOverseasDailyBars(
                    stock.getMarket(),
                    stock.getSymbol(),
                    today.minusDays(MARKET_LOOKBACK_DAYS),
                    today);
            if (!series.items().isEmpty()) {
                return persistBars(stock, series.items(), "KIS_OVERSEAS", NEW_YORK, US_CLOSE);
            }
        } catch (ProviderException exception) {
            kisFailure = exception;
        }

        try {
            LocalDate today = LocalDate.now(NEW_YORK);
            var series = finnhubMarketDataClient.dailyBars(
                    stock.getSymbol(),
                    today.minusDays(MARKET_LOOKBACK_DAYS),
                    today);
            if (!series.items().isEmpty()) {
                return persistBars(stock, series.items(), "FINNHUB", NEW_YORK, US_CLOSE);
            }
            return ProviderSyncResult.fallback(
                    "KIS_OVERSEAS+FINNHUB",
                    "미국 일봉 공급자가 이 종목의 가격 이력을 반환하지 않았습니다.");
        } catch (ProviderException finnhubFailure) {
            String kisMessage = kisFailure == null
                    ? "KIS 해외 일봉 응답이 비어 있습니다."
                    : fallbackMessage(kisFailure);
            return ProviderSyncResult.fallback(
                    "KIS_OVERSEAS+FINNHUB",
                    kisMessage + " Finnhub 해외 일봉 fallback: " + fallbackMessage(finnhubFailure));
        }
    }

    private ProviderSyncResult persistBars(
            Stock stock,
            List<Bar> bars,
            String provider,
            ZoneId zone,
            LocalTime closeTime) {
        List<MarketPrice> imported = new ArrayList<>();
        int updated = 0;
        for (Bar bar : bars) {
            Instant recordedAt = bar.sessionDate().atTime(closeTime).atZone(zone).toInstant();
            Instant sessionStart = bar.sessionDate().atStartOfDay(zone).toInstant();
            Instant sessionEnd = bar.sessionDate().plusDays(1).atStartOfDay(zone).toInstant();
            MarketPrice existing = marketPriceRepository
                    .findAllByStockIdAndIntervalAndRecordedAtBetween(stock.getId(), "1D", sessionStart, sessionEnd)
                    .stream()
                    .findFirst()
                    .orElse(null);
            if (existing != null) {
                existing.applyProviderBar(
                        bar.open(), bar.high(), bar.low(), bar.close(), bar.volume(), provider);
                updated++;
                continue;
            }
            imported.add(MarketPrice.create(
                    stock,
                    "1D",
                    bar.open(),
                    bar.high(),
                    bar.low(),
                    bar.close(),
                    bar.volume(),
                    recordedAt,
                    provider));
        }
        marketPriceRepository.saveAll(imported);
        marketPriceRepository.flush();
        marketPriceRepository.deleteDemoHistory(stock.getId(), "1D");
        return ProviderSyncResult.success(provider, imported.size() + updated);
    }

    private ProviderSyncResult syncFinnhubNews(Stock stock) {
        try {
            LocalDate today = LocalDate.now(SEOUL);
            var result = finnhubNewsClient.companyNews(
                    stock.getSymbol(),
                    today.minusDays(NEWS_LOOKBACK_DAYS),
                    today);
            List<NewsArticle> imported = new ArrayList<>();
            for (CompanyNewsItem item : result.items()) {
                if (item.title() == null || item.title().isBlank()
                        || item.originalUrl() == null || item.originalUrl().isBlank()) {
                    continue;
                }
                String externalId = Long.toString(item.externalId());
                if (newsArticleRepository.findBySourceAndExternalId("FINNHUB", externalId).isPresent()) {
                    continue;
                }
                imported.add(NewsArticle.createMetadata(
                        stock,
                        externalId,
                        truncate(item.title(), 500),
                        truncate(firstNonBlank(item.source(), "Finnhub"), 150),
                        truncate(item.originalUrl(), 1000),
                        item.publishedAt() == null ? result.fetchedAt() : item.publishedAt(),
                        "FINNHUB"));
            }
            newsArticleRepository.saveAll(imported);
            return ProviderSyncResult.success("FINNHUB", imported.size());
        } catch (ProviderException exception) {
            return ProviderSyncResult.fallback("FINNHUB", fallbackMessage(exception));
        }
    }

    private boolean isUsMarket(String market) {
        return "NASDAQ".equalsIgnoreCase(market) || "NYSE".equalsIgnoreCase(market);
    }

    private String fallbackMessage(ProviderException exception) {
        return exception.getCode() + ": 외부 호출에 실패해 기존 DB 데이터를 유지합니다.";
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second == null ? "" : second;
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }
}
