package com.finwatch.watchlist.config;

import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.finwatch.stock.domain.Stock;
import com.finwatch.stock.repository.StockRepository;
import com.finwatch.user.domain.AppUser;
import com.finwatch.user.repository.AppUserRepository;
import com.finwatch.watchlist.domain.Watchlist;
import com.finwatch.watchlist.repository.WatchlistRepository;

@Component
@Order(2)
@ConditionalOnProperty(name = "app.auth.demo-users-enabled", havingValue = "true", matchIfMissing = true)
public class DemoWatchlistInitializer implements ApplicationRunner {

    private final AppUserRepository appUserRepository;
    private final StockRepository stockRepository;
    private final WatchlistRepository watchlistRepository;

    public DemoWatchlistInitializer(
            AppUserRepository appUserRepository,
            StockRepository stockRepository,
            WatchlistRepository watchlistRepository) {
        this.appUserRepository = appUserRepository;
        this.stockRepository = stockRepository;
        this.watchlistRepository = watchlistRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seed("user@finwatch.local", List.of("005930", "000660"));
        seed("admin@finwatch.local", List.of("005930", "000660", "NVDA", "AAPL"));
    }

    private void seed(String email, List<String> symbols) {
        AppUser user = appUserRepository.findByEmailIgnoreCase(email).orElseThrow();
        for (String symbol : symbols) {
            Stock stock = stockRepository.findFirstBySymbolAndActiveTrue(symbol).orElseThrow();
            if (!watchlistRepository.existsByUserIdAndStockId(user.getId(), stock.getId())) {
                watchlistRepository.save(Watchlist.create(user, stock));
            }
        }
    }
}
