package com.finwatch.watchlist.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.finwatch.stock.domain.Stock;
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

    public WatchlistService(
            WatchlistRepository watchlistRepository,
            AppUserRepository appUserRepository,
            StockRepository stockRepository,
            StockQueryService stockQueryService) {
        this.watchlistRepository = watchlistRepository;
        this.appUserRepository = appUserRepository;
        this.stockRepository = stockRepository;
        this.stockQueryService = stockQueryService;
    }

    @Transactional(readOnly = true)
    public List<WatchlistItemResponse> getWatchlist(Long userId) {
        return watchlistRepository.findAllWithStockByUserId(userId).stream()
                .map(item -> WatchlistItemResponse.from(
                        item,
                        stockQueryService.getStock(item.getStock().getSymbol())))
                .toList();
    }

    @Transactional
    public WatchlistItemResponse add(Long userId, String requestedSymbol) {
        String symbol = requestedSymbol.trim().toUpperCase();
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "사용자를 찾을 수 없습니다."));
        Stock stock = stockRepository.findFirstBySymbolAndActiveTrue(symbol)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "종목을 찾을 수 없습니다."));
        if (watchlistRepository.existsByUserIdAndStockId(userId, stock.getId())) {
            throw WatchlistException.duplicated();
        }
        Watchlist watchlist = watchlistRepository.save(Watchlist.create(user, stock));
        return WatchlistItemResponse.from(watchlist, stockQueryService.getStock(stock.getSymbol()));
    }

    @Transactional
    public void remove(Long userId, String symbol) {
        Watchlist watchlist = watchlistRepository.findByUserIdAndSymbol(userId, symbol.trim())
                .orElseThrow(WatchlistException::notFound);
        watchlistRepository.delete(watchlist);
    }
}
