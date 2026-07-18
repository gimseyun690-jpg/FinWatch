package com.finwatch.watchlist.service;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import com.finwatch.realtime.RealtimeSubscriptionManager;
import com.finwatch.stock.domain.Stock;
import com.finwatch.stock.dto.StockDataLoadResponses.DataLoadResource;
import com.finwatch.stock.service.StockDataLoadService;
import com.finwatch.stock.service.StockQueryService;
import com.finwatch.stock.repository.StockRepository;
import com.finwatch.user.domain.AppUser;
import com.finwatch.user.repository.AppUserRepository;
import com.finwatch.watchlist.domain.Watchlist;
import com.finwatch.watchlist.dto.WatchlistItemResponse;
import com.finwatch.watchlist.repository.WatchlistRepository;

@Service
public class WatchlistService {

    private final WatchlistRepository watchlistRepository;
    private final AppUserRepository appUserRepository;
    private final StockRepository stockRepository;
    private final StockQueryService stockQueryService;
    private final StockDataLoadService stockDataLoadService;
    private final RealtimeSubscriptionManager realtimeSubscriptionManager;

    public WatchlistService(
            WatchlistRepository watchlistRepository,
            AppUserRepository appUserRepository,
            StockRepository stockRepository,
            StockQueryService stockQueryService,
            StockDataLoadService stockDataLoadService,
            RealtimeSubscriptionManager realtimeSubscriptionManager) {
        this.watchlistRepository = watchlistRepository;
        this.appUserRepository = appUserRepository;
        this.stockRepository = stockRepository;
        this.stockQueryService = stockQueryService;
        this.stockDataLoadService = stockDataLoadService;
        this.realtimeSubscriptionManager = realtimeSubscriptionManager;
    }

    @Transactional(readOnly = true)
    public List<WatchlistItemResponse> getWatchlist(Long userId) {
        return watchlistRepository.findAllWithStockByUserId(userId).stream()
                .map(item -> WatchlistItemResponse.from(
                        item,
                        stockQueryService.getStock(
                                item.getStock().getMarket(),
                                item.getStock().getSymbol())))
                .toList();
    }

    @Transactional
    public WatchlistItemResponse add(Long userId, String requestedMarket, String requestedSymbol) {
        String symbol = normalize(requestedSymbol);
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "사용자를 찾을 수 없습니다."));
        Stock stock = findStock(requestedMarket, symbol);
        if (watchlistRepository.existsByUserIdAndStockId(userId, stock.getId())) {
            throw WatchlistException.duplicated();
        }
        Watchlist watchlist = watchlistRepository.save(Watchlist.create(user, stock));
        afterCommit(() -> {
            realtimeSubscriptionManager.requestRefresh();
            prepareStockData(stock.getMarket(), stock.getSymbol());
        });
        return WatchlistItemResponse.from(
                watchlist,
                stockQueryService.getStock(stock.getMarket(), stock.getSymbol()));
    }

    @Transactional
    public void remove(Long userId, String symbol) {
        Watchlist watchlist = watchlistRepository.findByUserIdAndSymbol(userId, symbol.trim())
                .orElseThrow(WatchlistException::notFound);
        watchlistRepository.delete(watchlist);
        afterCommit(realtimeSubscriptionManager::requestRefresh);
    }

    @Transactional
    public void remove(Long userId, String market, String symbol) {
        Watchlist watchlist = watchlistRepository.findByUserIdAndMarketAndSymbol(
                        userId,
                        normalize(market),
                        normalize(symbol))
                .orElseThrow(WatchlistException::notFound);
        watchlistRepository.delete(watchlist);
        afterCommit(realtimeSubscriptionManager::requestRefresh);
    }

    private Stock findStock(String requestedMarket, String symbol) {
        if (requestedMarket != null && !requestedMarket.isBlank()) {
            return stockRepository.findByMarketAndSymbolAndActiveTrue(normalize(requestedMarket), symbol)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "종목을 찾을 수 없습니다."));
        }
        List<Stock> candidates = stockRepository.findAllBySymbolIgnoreCaseAndActiveTrueOrderByMarketAsc(symbol);
        if (candidates.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "종목을 찾을 수 없습니다.");
        }
        if (candidates.size() > 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "같은 심볼이 여러 시장에 있어 market이 필요합니다.");
        }
        return candidates.getFirst();
    }

    private void prepareStockData(String market, String symbol) {
        try {
            stockDataLoadService.start(
                    market,
                    symbol,
                    Set.of(
                            DataLoadResource.QUOTE,
                            DataLoadResource.DAILY_PRICES,
                            DataLoadResource.NEWS,
                            DataLoadResource.DISCLOSURES));
        } catch (RuntimeException ignored) {
            // The watchlist remains valid even when a provider is unavailable or DEMO has metadata only.
        }
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
            return;
        }
        action.run();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
