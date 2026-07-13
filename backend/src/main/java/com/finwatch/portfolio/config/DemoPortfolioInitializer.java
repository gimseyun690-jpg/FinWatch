package com.finwatch.portfolio.config;

import java.math.BigDecimal;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.finwatch.portfolio.domain.PortfolioHolding;
import com.finwatch.portfolio.repository.PortfolioHoldingRepository;
import com.finwatch.stock.domain.Stock;
import com.finwatch.stock.repository.StockRepository;
import com.finwatch.user.domain.AppUser;
import com.finwatch.user.repository.AppUserRepository;

@Component
@Order(3)
@ConditionalOnProperty(name = "app.auth.demo-users-enabled", havingValue = "true", matchIfMissing = true)
public class DemoPortfolioInitializer implements ApplicationRunner {

    private final PortfolioHoldingRepository holdingRepository;
    private final AppUserRepository userRepository;
    private final StockRepository stockRepository;

    public DemoPortfolioInitializer(
            PortfolioHoldingRepository holdingRepository,
            AppUserRepository userRepository,
            StockRepository stockRepository) {
        this.holdingRepository = holdingRepository;
        this.userRepository = userRepository;
        this.stockRepository = stockRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seed("admin@finwatch.local", "000660", "3", "2500000");
        seed("admin@finwatch.local", "NVDA", "10", "190");
        seed("user@finwatch.local", "005930", "12", "310000");
    }

    private void seed(String email, String symbol, String quantity, String averagePrice) {
        AppUser user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        Stock stock = stockRepository.findFirstBySymbolAndActiveTrue(symbol).orElse(null);
        if (user == null || stock == null || holdingRepository.existsByUserIdAndStockId(user.getId(), stock.getId())) {
            return;
        }
        holdingRepository.save(PortfolioHolding.create(
                user, stock, new BigDecimal(quantity), new BigDecimal(averagePrice)));
    }
}
