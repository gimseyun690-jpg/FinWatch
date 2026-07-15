package com.finwatch.stock.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.finwatch.data.provider.FinnhubMarketDataClient;
import com.finwatch.data.provider.KisMarketDataClient;
import com.finwatch.data.provider.ProviderResponses.Quote;
import com.finwatch.data.sync.DataMode;
import com.finwatch.data.sync.DataSyncResponses.ProviderSyncResult;
import com.finwatch.data.sync.ExternalDataSyncService;
import com.finwatch.disclosure.service.DisclosureSyncService;
import com.finwatch.news.repository.NewsArticleRepository;
import com.finwatch.realtime.LiveQuote;
import com.finwatch.realtime.RealtimeQuoteHub;
import com.finwatch.stock.domain.Stock;
import com.finwatch.stock.dto.StockDataLoadResponses.DataLoadDispatch;
import com.finwatch.stock.dto.StockDataLoadResponses.DataLoadResource;
import com.finwatch.stock.dto.StockDataLoadResponses.DataLoadResponse;
import com.finwatch.stock.dto.StockDataLoadResponses.ResourceLoadResult;
import com.finwatch.stock.repository.MarketPriceRepository;
import com.finwatch.stock.repository.StockRepository;

import jakarta.annotation.PreDestroy;

@Service
public class StockDataLoadService {

    private static final int MAX_RETAINED_JOBS = 500;

    private final DataMode dataMode;
    private final StockRepository stockRepository;
    private final MarketPriceRepository marketPriceRepository;
    private final NewsArticleRepository newsArticleRepository;
    private final ExternalDataSyncService externalDataSyncService;
    private final DisclosureSyncService disclosureSyncService;
    private final KisMarketDataClient kisMarketDataClient;
    private final FinnhubMarketDataClient finnhubMarketDataClient;
    private final RealtimeQuoteHub quoteHub;
    private final Duration quoteFreshness;
    private final Duration dailyPriceFreshness;
    private final Duration newsFreshness;
    private final ExecutorService executor;
    private final ConcurrentHashMap<String, DataLoadJob> inFlight = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, DataLoadJob> jobs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> successfulLoads = new ConcurrentHashMap<>();

    public StockDataLoadService(
            @Value("${app.data.mode:DEMO}") String dataMode,
            StockRepository stockRepository,
            MarketPriceRepository marketPriceRepository,
            NewsArticleRepository newsArticleRepository,
            ExternalDataSyncService externalDataSyncService,
            DisclosureSyncService disclosureSyncService,
            KisMarketDataClient kisMarketDataClient,
            FinnhubMarketDataClient finnhubMarketDataClient,
            RealtimeQuoteHub quoteHub,
            @Value("${app.data.load.quote-freshness:15s}") Duration quoteFreshness,
            @Value("${app.data.load.daily-price-freshness:12h}") Duration dailyPriceFreshness,
            @Value("${app.data.load.news-freshness:15m}") Duration newsFreshness) {
        this.dataMode = DataMode.from(dataMode);
        this.stockRepository = stockRepository;
        this.marketPriceRepository = marketPriceRepository;
        this.newsArticleRepository = newsArticleRepository;
        this.externalDataSyncService = externalDataSyncService;
        this.disclosureSyncService = disclosureSyncService;
        this.kisMarketDataClient = kisMarketDataClient;
        this.finnhubMarketDataClient = finnhubMarketDataClient;
        this.quoteHub = quoteHub;
        this.quoteFreshness = quoteFreshness;
        this.dailyPriceFreshness = dailyPriceFreshness;
        this.newsFreshness = newsFreshness;
        this.executor = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "stock-data-load");
            thread.setDaemon(true);
            return thread;
        });
    }

    public DataLoadDispatch start(String market, String symbol, Set<DataLoadResource> requestedResources) {
        String normalizedMarket = normalize(market);
        String normalizedSymbol = normalize(symbol);
        Stock stock = stockRepository.findByMarketAndSymbolAndActiveTrue(normalizedMarket, normalizedSymbol)
                .orElseThrow(() -> new StockDataLoadException(
                        HttpStatus.NOT_FOUND,
                        "STOCK_NOT_FOUND",
                        "활성 종목을 찾을 수 없습니다: " + normalizedMarket + ":" + normalizedSymbol));
        List<DataLoadResource> resources = normalizeResources(requestedResources);
        validateSupportedMarket(stock, resources);

        if (dataMode == DataMode.DEMO) {
            return demoResult(stock, resources);
        }

        List<ResourceLoadResult> fresh = freshResults(stock, resources);
        if (fresh.size() == resources.size()) {
            Instant now = Instant.now();
            return new DataLoadDispatch(new DataLoadResponse(
                    UUID.randomUUID().toString(),
                    stock.getMarket(),
                    stock.getSymbol(),
                    "READY",
                    false,
                    now,
                    now,
                    fresh), false);
        }

        String key = loadKey(stock, resources);
        DataLoadJob created = new DataLoadJob(stock, resources, key);
        DataLoadJob existing = inFlight.putIfAbsent(key, created);
        if (existing != null) {
            return new DataLoadDispatch(existing.snapshot(true), true);
        }

        jobs.put(created.jobId, created);
        trimCompletedJobs();
        executor.execute(() -> execute(created));
        return new DataLoadDispatch(created.snapshot(false), true);
    }

    public DataLoadResponse get(String market, String symbol, String jobId) {
        DataLoadJob job = jobs.get(jobId);
        if (job == null || !job.stock.getMarket().equalsIgnoreCase(normalize(market))
                || !job.stock.getSymbol().equalsIgnoreCase(normalize(symbol))) {
            throw new StockDataLoadException(HttpStatus.NOT_FOUND, "DATA_LOAD_NOT_FOUND", "데이터 로드 작업을 찾을 수 없습니다.");
        }
        return job.snapshot(false);
    }

    private DataLoadDispatch demoResult(Stock stock, List<DataLoadResource> resources) {
        List<ResourceLoadResult> results = new ArrayList<>();
        boolean hasPrices = marketPriceRepository.existsByStockId(stock.getId());
        boolean hasNews = newsArticleRepository.existsByStockIdAndContentKind(stock.getId(), "NEWS");
        Instant now = Instant.now();
        for (DataLoadResource resource : resources) {
            boolean ready = switch (resource) {
                case QUOTE, DAILY_PRICES -> hasPrices;
                case NEWS -> hasNews;
                case DISCLOSURES -> false;
            };
            results.add(ready
                    ? new ResourceLoadResult(resource, "READY", "DEMO_DB", 0, "기존 데모 데이터 사용", now)
                    : new ResourceLoadResult(resource, "UNSUPPORTED", "DEMO", 0, "데모 모드에서 제공하지 않는 리소스", null));
        }
        long readyCount = results.stream().filter(result -> "READY".equals(result.status())).count();
        if (readyCount == 0) {
            throw new StockDataLoadException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "DATA_NOT_SUPPORTED",
                    "이 종목은 데모 모드에서 메타데이터만 제공합니다. LIVE 모드에서 실제 데이터를 불러오세요.");
        }
        String status = readyCount == results.size() ? "READY" : "PARTIAL";
        return new DataLoadDispatch(new DataLoadResponse(
                UUID.randomUUID().toString(), stock.getMarket(), stock.getSymbol(), status, false,
                now, now, List.copyOf(results)), false);
    }

    private void execute(DataLoadJob job) {
        Map<DataLoadResource, ResourceLoadResult> results = new EnumMap<>(DataLoadResource.class);
        try {
            for (ResourceLoadResult fresh : freshResults(job.stock, job.resources)) {
                results.put(fresh.resource(), fresh);
            }
            if (job.resources.contains(DataLoadResource.QUOTE) && !results.containsKey(DataLoadResource.QUOTE)) {
                results.put(DataLoadResource.QUOTE, loadQuote(job.stock));
            }

            boolean needsPrices = job.resources.contains(DataLoadResource.DAILY_PRICES)
                    && !results.containsKey(DataLoadResource.DAILY_PRICES);
            boolean needsNews = job.resources.contains(DataLoadResource.NEWS)
                    && !results.containsKey(DataLoadResource.NEWS);
            if (needsPrices || needsNews) {
                var sync = externalDataSyncService.syncStock(job.stock.getMarket(), job.stock.getSymbol());
                var stockResult = sync.stocks().getFirst();
                if (needsPrices) {
                    results.put(DataLoadResource.DAILY_PRICES, providerResult(
                            job.stock, DataLoadResource.DAILY_PRICES, stockResult.marketPrices()));
                }
                if (needsNews) {
                    results.put(DataLoadResource.NEWS, providerResult(
                            job.stock, DataLoadResource.NEWS, stockResult.news()));
                }
            }
            if (job.resources.contains(DataLoadResource.DISCLOSURES)) {
                results.put(DataLoadResource.DISCLOSURES, providerResult(
                        job.stock,
                        DataLoadResource.DISCLOSURES,
                        disclosureSyncService.sync(job.stock)));
            }
            job.complete(completedResponse(job, results));
        } catch (RuntimeException exception) {
            for (DataLoadResource resource : job.resources) {
                results.putIfAbsent(resource, new ResourceLoadResult(
                        resource, "FAILED", null, 0, safeMessage(exception), null));
            }
            job.complete(completedResponse(job, results));
        } finally {
            inFlight.remove(job.key, job);
        }
    }

    private ResourceLoadResult loadQuote(Stock stock) {
        try {
            Quote quote;
            String provider;
            if ("KRX".equalsIgnoreCase(stock.getMarket())) {
                quote = kisMarketDataClient.getDomesticQuote(stock.getSymbol());
                provider = "KIS";
            } else {
                quote = finnhubMarketDataClient.quote(stock.getSymbol());
                provider = "FINNHUB";
            }
            quoteHub.publish(new LiveQuote(
                    quote.symbol(), quote.price(), quote.change(), quote.changeRate(), quote.volume(),
                    quote.currency(), quote.fetchedAt(), provider + "_REST", "SNAPSHOT"));
            markSuccessful(stock, DataLoadResource.QUOTE, quote.fetchedAt());
            return new ResourceLoadResult(
                    DataLoadResource.QUOTE, "READY", provider, 1, "현재가 수집 완료", quote.fetchedAt());
        } catch (RuntimeException exception) {
            return new ResourceLoadResult(
                    DataLoadResource.QUOTE, "FAILED", null, 0, safeMessage(exception), null);
        }
    }

    private ResourceLoadResult providerResult(
            Stock stock,
            DataLoadResource resource,
            ProviderSyncResult providerResult) {
        String status = switch (providerResult.status()) {
            case "SUCCESS" -> "READY";
            case "SKIPPED" -> "UNSUPPORTED";
            default -> "FAILED";
        };
        Instant asOf = "READY".equals(status) ? Instant.now() : null;
        if (asOf != null) {
            markSuccessful(stock, resource, asOf);
        }
        return new ResourceLoadResult(
                resource,
                status,
                providerResult.provider(),
                providerResult.imported(),
                providerResult.message(),
                asOf);
    }

    private DataLoadResponse completedResponse(
            DataLoadJob job,
            Map<DataLoadResource, ResourceLoadResult> results) {
        List<ResourceLoadResult> ordered = job.resources.stream().map(results::get).toList();
        long ready = ordered.stream().filter(result -> "READY".equals(result.status())).count();
        String status = ready == ordered.size() ? "READY" : ready > 0 ? "PARTIAL" : "FAILED";
        return new DataLoadResponse(
                job.jobId,
                job.stock.getMarket(),
                job.stock.getSymbol(),
                status,
                false,
                job.startedAt,
                Instant.now(),
                ordered);
    }

    private List<ResourceLoadResult> freshResults(Stock stock, List<DataLoadResource> resources) {
        Instant now = Instant.now();
        List<ResourceLoadResult> results = new ArrayList<>();
        for (DataLoadResource resource : resources) {
            Instant asOf = freshAsOf(stock, resource, now);
            if (asOf != null) {
                String provider = resource == DataLoadResource.QUOTE ? "REALTIME_CACHE" : "DATA_LOAD_CACHE";
                results.add(new ResourceLoadResult(resource, "READY", provider, 0, "신선한 기존 데이터 사용", asOf));
            }
        }
        return List.copyOf(results);
    }

    private Instant freshAsOf(Stock stock, DataLoadResource resource, Instant now) {
        if (resource == DataLoadResource.DISCLOSURES) {
            return successfulAsOf(stock, resource, newsFreshness, now);
        }
        if (resource == DataLoadResource.QUOTE) {
            return quoteHub.find(stock.getSymbol())
                    .map(LiveQuote::asOf)
                    .filter(asOf -> asOf.isAfter(now.minus(quoteFreshness)))
                    .orElseGet(() -> successfulAsOf(stock, resource, quoteFreshness, now));
        }
        Duration freshness = resource == DataLoadResource.DAILY_PRICES ? dailyPriceFreshness : newsFreshness;
        return successfulAsOf(stock, resource, freshness, now);
    }

    private Instant successfulAsOf(Stock stock, DataLoadResource resource, Duration freshness, Instant now) {
        Instant asOf = successfulLoads.get(resourceKey(stock, resource));
        return asOf != null && asOf.isAfter(now.minus(freshness)) ? asOf : null;
    }

    private void markSuccessful(Stock stock, DataLoadResource resource, Instant asOf) {
        successfulLoads.put(resourceKey(stock, resource), asOf == null ? Instant.now() : asOf);
    }

    private void validateSupportedMarket(Stock stock, List<DataLoadResource> resources) {
        boolean supported = "KRX".equalsIgnoreCase(stock.getMarket())
                || "NASDAQ".equalsIgnoreCase(stock.getMarket())
                || "NYSE".equalsIgnoreCase(stock.getMarket());
        if (!supported) {
            throw new StockDataLoadException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "DATA_NOT_SUPPORTED",
                    "현재 공급자 범위에서 지원하지 않는 데이터 요청입니다.");
        }
    }

    private List<DataLoadResource> normalizeResources(Set<DataLoadResource> resources) {
        if (resources == null || resources.isEmpty()) {
            throw new StockDataLoadException(HttpStatus.BAD_REQUEST, "DATA_LOAD_RESOURCES_REQUIRED", "resources가 필요합니다.");
        }
        return resources.stream().distinct().sorted(Comparator.comparingInt(Enum::ordinal)).toList();
    }

    private String loadKey(Stock stock, List<DataLoadResource> resources) {
        return stock.getMarket() + ":" + stock.getSymbol() + ":"
                + resources.stream().map(Enum::name).collect(java.util.stream.Collectors.joining(","));
    }

    private String resourceKey(Stock stock, DataLoadResource resource) {
        return stock.getMarket() + ":" + stock.getSymbol() + ":" + resource.name();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        String sanitized = message.replaceAll("[\\r\\n\\t]+", " ").trim();
        return sanitized.length() <= 300 ? sanitized : sanitized.substring(0, 300);
    }

    private void trimCompletedJobs() {
        if (jobs.size() <= MAX_RETAINED_JOBS) {
            return;
        }
        jobs.values().stream()
                .filter(DataLoadJob::completed)
                .sorted(Comparator.comparing(job -> job.startedAt))
                .limit(jobs.size() - MAX_RETAINED_JOBS)
                .forEach(job -> jobs.remove(job.jobId, job));
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    private static final class DataLoadJob {
        private final String jobId = UUID.randomUUID().toString();
        private final Stock stock;
        private final List<DataLoadResource> resources;
        private final String key;
        private final Instant startedAt = Instant.now();
        private volatile DataLoadResponse completedResponse;

        private DataLoadJob(Stock stock, List<DataLoadResource> resources, String key) {
            this.stock = stock;
            this.resources = resources;
            this.key = key;
        }

        private void complete(DataLoadResponse response) {
            this.completedResponse = response;
        }

        private boolean completed() {
            return completedResponse != null;
        }

        private DataLoadResponse snapshot(boolean reused) {
            DataLoadResponse response = completedResponse;
            if (response == null) {
                return new DataLoadResponse(
                        jobId,
                        stock.getMarket(),
                        stock.getSymbol(),
                        "SYNCING",
                        reused,
                        startedAt,
                        null,
                        resources.stream().map(ResourceLoadResult::pending).toList());
            }
            return new DataLoadResponse(
                    response.jobId(), response.market(), response.symbol(), response.status(), reused,
                    response.startedAt(), response.finishedAt(), response.resources());
        }
    }
}
