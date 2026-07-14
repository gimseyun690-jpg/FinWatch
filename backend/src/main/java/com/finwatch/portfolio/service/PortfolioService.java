package com.finwatch.portfolio.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.finwatch.portfolio.domain.PortfolioHolding;
import com.finwatch.portfolio.dto.PortfolioCreateRequest;
import com.finwatch.portfolio.dto.PortfolioResponse;
import com.finwatch.portfolio.dto.PortfolioResponse.CurrencySummary;
import com.finwatch.portfolio.dto.PortfolioResponse.Holding;
import com.finwatch.portfolio.dto.PortfolioUpdateRequest;
import com.finwatch.portfolio.repository.PortfolioHoldingRepository;
import com.finwatch.stock.domain.Stock;
import com.finwatch.stock.repository.StockRepository;
import com.finwatch.stock.service.LatestPriceResolver;
import com.finwatch.stock.service.LatestPriceResolver.LatestPrice;
import com.finwatch.user.domain.AppUser;
import com.finwatch.user.repository.AppUserRepository;

@Service
public class PortfolioService {

    private final PortfolioHoldingRepository holdingRepository;
    private final AppUserRepository userRepository;
    private final StockRepository stockRepository;
    private final LatestPriceResolver latestPriceResolver;

    public PortfolioService(
            PortfolioHoldingRepository holdingRepository,
            AppUserRepository userRepository,
            StockRepository stockRepository,
            LatestPriceResolver latestPriceResolver) {
        this.holdingRepository = holdingRepository;
        this.userRepository = userRepository;
        this.stockRepository = stockRepository;
        this.latestPriceResolver = latestPriceResolver;
    }

    @Transactional(readOnly = true)
    public PortfolioResponse getPortfolio(Long userId) {
        List<Holding> holdings = holdingRepository.findAllWithStockByUserId(userId).stream()
                .map(this::toHolding)
                .toList();
        return new PortfolioResponse(summarizeByCurrency(holdings), holdings);
    }

    @Transactional
    public Holding create(Long userId, PortfolioCreateRequest request) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "사용자를 찾을 수 없습니다."));
        String symbol = request.symbol().trim().toUpperCase(Locale.ROOT);
        Stock stock = stockRepository.findFirstBySymbolAndActiveTrue(symbol)
                .orElseThrow(PortfolioException::stockNotFound);
        if (!stock.getCurrency().equalsIgnoreCase(request.currency().trim())) {
            throw PortfolioException.currencyMismatch();
        }
        if (holdingRepository.existsByUserIdAndStockId(userId, stock.getId())) {
            throw PortfolioException.duplicated();
        }
        try {
            PortfolioHolding holding = holdingRepository.save(PortfolioHolding.create(
                    user, stock, request.quantity(), request.averagePurchasePrice()));
            return toHolding(holding);
        } catch (DataIntegrityViolationException exception) {
            throw PortfolioException.duplicated();
        }
    }

    @Transactional
    public Holding update(Long userId, Long holdingId, PortfolioUpdateRequest request) {
        PortfolioHolding holding = holdingRepository.findWithStockByIdAndUserId(holdingId, userId)
                .orElseThrow(PortfolioException::notFound);
        holding.update(request.quantity(), request.averagePurchasePrice());
        return toHolding(holding);
    }

    @Transactional
    public void delete(Long userId, Long holdingId) {
        PortfolioHolding holding = holdingRepository.findWithStockByIdAndUserId(holdingId, userId)
                .orElseThrow(PortfolioException::notFound);
        holdingRepository.delete(holding);
    }

    private Holding toHolding(PortfolioHolding holding) {
        Stock stock = holding.getStock();
        BigDecimal purchaseAmount = money(holding.getQuantity().multiply(holding.getAveragePurchasePrice()));
        LatestPrice latest = latestPriceResolver.resolve(stock).orElse(null);
        if (latest == null) {
            return new Holding(
                    holding.getId(), stock.getSymbol(), stock.getName(), stock.getMarket(), holding.getCurrency(),
                    holding.getQuantity(), holding.getAveragePurchasePrice(), null, null, null, purchaseAmount,
                    null, null, null, "PRICE_UNAVAILABLE", holding.getUpdatedAt());
        }

        BigDecimal evaluationAmount = money(holding.getQuantity().multiply(latest.price()));
        BigDecimal profitLoss = money(evaluationAmount.subtract(purchaseAmount));
        BigDecimal returnRate = purchaseAmount.signum() == 0
                ? null
                : profitLoss.divide(purchaseAmount, 8, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
        return new Holding(
                holding.getId(), stock.getSymbol(), stock.getName(), stock.getMarket(), holding.getCurrency(),
                holding.getQuantity(), holding.getAveragePurchasePrice(), latest.price(),
                latest.asOf(), latest.source(), purchaseAmount, evaluationAmount, profitLoss,
                returnRate, "VALUED", holding.getUpdatedAt());
    }

    private List<CurrencySummary> summarizeByCurrency(List<Holding> holdings) {
        Map<String, List<Holding>> grouped = new LinkedHashMap<>();
        for (Holding holding : holdings) {
            grouped.computeIfAbsent(holding.currency(), ignored -> new ArrayList<>()).add(holding);
        }

        return grouped.entrySet().stream().map(entry -> {
            BigDecimal purchase = entry.getValue().stream()
                    .map(Holding::purchaseAmount)
                    .reduce(BigDecimal.ZERO.setScale(4), BigDecimal::add);
            boolean complete = entry.getValue().stream().allMatch(item -> item.evaluationAmount() != null);
            if (!complete) {
                return new CurrencySummary(entry.getKey(), money(purchase), null, null, null, false);
            }
            BigDecimal evaluation = entry.getValue().stream()
                    .map(Holding::evaluationAmount)
                    .reduce(BigDecimal.ZERO.setScale(4), BigDecimal::add);
            BigDecimal profit = money(evaluation.subtract(purchase));
            BigDecimal returnRate = purchase.signum() == 0
                    ? null
                    : profit.divide(purchase, 8, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
            return new CurrencySummary(entry.getKey(), money(purchase), money(evaluation), profit, returnRate, true);
        }).toList();
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP);
    }
}
