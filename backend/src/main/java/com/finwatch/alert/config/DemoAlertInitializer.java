package com.finwatch.alert.config;

import java.math.BigDecimal;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.finwatch.alert.domain.AlertCondition;
import com.finwatch.alert.domain.PriceAlert;
import com.finwatch.alert.repository.PriceAlertRepository;
import com.finwatch.stock.domain.Stock;
import com.finwatch.stock.repository.StockRepository;
import com.finwatch.user.domain.AppUser;
import com.finwatch.user.repository.AppUserRepository;

@Component
@Order(4)
@ConditionalOnProperty(name = "app.auth.demo-users-enabled", havingValue = "true", matchIfMissing = true)
public class DemoAlertInitializer implements ApplicationRunner {

    private final PriceAlertRepository alertRepository;
    private final AppUserRepository userRepository;
    private final StockRepository stockRepository;

    public DemoAlertInitializer(PriceAlertRepository alertRepository, AppUserRepository userRepository, StockRepository stockRepository) {
        this.alertRepository = alertRepository;
        this.userRepository = userRepository;
        this.stockRepository = stockRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seed("admin@finwatch.local", "000660", AlertCondition.ABOVE, "2800000");
        seed("admin@finwatch.local", "NVDA", AlertCondition.ABOVE, "200");
        seed("user@finwatch.local", "005930", AlertCondition.BELOW, "330000");
    }

    private void seed(String email, String symbol, AlertCondition condition, String target) {
        AppUser user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        Stock stock = stockRepository.findFirstBySymbolAndActiveTrue(symbol).orElse(null);
        BigDecimal targetPrice = new BigDecimal(target);
        if (user == null || stock == null || alertRepository.findDuplicate(user.getId(), stock.getId(), condition, targetPrice).isPresent()) return;
        alertRepository.save(PriceAlert.create(user, stock, condition, targetPrice));
    }
}
