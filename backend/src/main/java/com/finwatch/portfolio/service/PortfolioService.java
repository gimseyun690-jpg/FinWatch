package com.finwatch.portfolio.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.finwatch.fx.dto.FxRateResponses.LatestFxRate;
import com.finwatch.fx.service.FxRateService;
import com.finwatch.portfolio.domain.PortfolioHolding;
import com.finwatch.portfolio.dto.PortfolioCreateRequest;
import com.finwatch.portfolio.dto.PortfolioResponse;
import com.finwatch.portfolio.dto.PortfolioResponse.AppliedFxRate;
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
    private final FxRateService fxRateService;

    public PortfolioService(
            PortfolioHoldingRepository holdingRepository,
            AppUserRepository userRepository,
            StockRepository stockRepository,
            LatestPriceResolver latestPriceResolver,
            FxRateService fxRateService) {
        this.holdingRepository = holdingRepository;
        this.userRepository = userRepository;
        this.stockRepository = stockRepository;
        this.latestPriceResolver = latestPriceResolver;
        this.fxRateService = fxRateService;
    }

    @Transactional(readOnly = true)
    public PortfolioResponse getPortfolio(Long userId) {
        Optional<LatestFxRate> usdKrw = fxRateService.latestForPortfolio();
        List<Holding> holdings = holdingRepository.findAllWithStockByUserId(userId).stream()
                .map(holding -> toHolding(holding, usdKrw.orElse(null)))
                .toList();
        return portfolioResponse(holdings, usdKrw.orElse(null));
    }

    @Transactional
    public Holding create(Long userId, PortfolioCreateRequest request) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "사용자를 찾을 수 없습니다."));
        String symbol = request.symbol().trim().toUpperCase(Locale.ROOT);
        Stock stock = stockRepository.findFirstBySymbolAndActiveTrue(symbol)
                .orElseThrow(PortfolioException::stockNotFound);
        if (!stock.getCurrency().equalsIgnoreCase(request.currency().trim())) throw PortfolioException.currencyMismatch();
        validatePurchaseFx(stock, request.averagePurchaseFxRate(), request.purchaseFxBaseCurrency(), request.purchaseFxQuoteCurrency());
        if (holdingRepository.existsByUserIdAndStockId(userId, stock.getId())) throw PortfolioException.duplicated();
        try {
            PortfolioHolding holding = holdingRepository.save(PortfolioHolding.create(
                    user, stock, request.quantity(), request.averagePurchasePrice(), request.averagePurchaseFxRate(),
                    upper(request.purchaseFxBaseCurrency()), upper(request.purchaseFxQuoteCurrency())));
            return toHolding(holding, fxRateService.latestForPortfolio().orElse(null));
        } catch (DataIntegrityViolationException exception) {
            throw PortfolioException.duplicated();
        }
    }

    @Transactional
    public Holding update(Long userId, Long holdingId, PortfolioUpdateRequest request) {
        PortfolioHolding holding = holdingRepository.findWithStockByIdAndUserId(holdingId, userId)
                .orElseThrow(PortfolioException::notFound);
        boolean fxPresent = request.averagePurchaseFxRate() != null
                || request.purchaseFxBaseCurrency() != null || request.purchaseFxQuoteCurrency() != null;
        if (fxPresent) validatePurchaseFx(holding.getStock(), request.averagePurchaseFxRate(), request.purchaseFxBaseCurrency(), request.purchaseFxQuoteCurrency());
        holding.update(request.quantity(), request.averagePurchasePrice(), request.averagePurchaseFxRate(),
                upper(request.purchaseFxBaseCurrency()), upper(request.purchaseFxQuoteCurrency()), fxPresent);
        return toHolding(holding, fxRateService.latestForPortfolio().orElse(null));
    }

    @Transactional
    public void delete(Long userId, Long holdingId) {
        PortfolioHolding holding = holdingRepository.findWithStockByIdAndUserId(holdingId, userId)
                .orElseThrow(PortfolioException::notFound);
        holdingRepository.delete(holding);
    }

    private Holding toHolding(PortfolioHolding holding, LatestFxRate usdKrw) {
        Stock stock = holding.getStock();
        BigDecimal purchaseAmount = money(holding.getQuantity().multiply(holding.getAveragePurchasePrice()));
        LatestPrice latest = latestPriceResolver.resolve(stock).orElse(null);
        if (latest == null) {
            return new Holding(
                    holding.getId(), stock.getSymbol(), stock.getName(), stock.getMarket(), holding.getCurrency(),
                    holding.getQuantity(), holding.getAveragePurchasePrice(), holding.getAveragePurchaseFxRate(),
                    holding.getPurchaseFxBaseCurrency(), holding.getPurchaseFxQuoteCurrency(), null, null, null,
                    purchaseAmount, null, null, null, null, convertedPurchase(holding, purchaseAmount), null, null,
                    "PRICE_UNAVAILABLE", holding.getUpdatedAt());
        }

        BigDecimal evaluationAmount = money(holding.getQuantity().multiply(latest.price()));
        BigDecimal profitLoss = money(evaluationAmount.subtract(purchaseAmount));
        BigDecimal returnRate = purchaseAmount.signum() == 0 ? null
                : profitLoss.divide(purchaseAmount, 8, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal convertedEvaluation = convertedEvaluation(holding, evaluationAmount, usdKrw);
        BigDecimal convertedPurchase = convertedPurchase(holding, purchaseAmount);
        BigDecimal convertedProfit = convertedEvaluation == null || convertedPurchase == null
                ? null : won(convertedEvaluation.subtract(convertedPurchase));
        BigDecimal fxEffect = fxEffect(holding, evaluationAmount, usdKrw);
        return new Holding(
                holding.getId(), stock.getSymbol(), stock.getName(), stock.getMarket(), holding.getCurrency(),
                holding.getQuantity(), holding.getAveragePurchasePrice(), holding.getAveragePurchaseFxRate(),
                holding.getPurchaseFxBaseCurrency(), holding.getPurchaseFxQuoteCurrency(), latest.price(),
                latest.asOf(), latest.source(), purchaseAmount, evaluationAmount, profitLoss, returnRate,
                convertedEvaluation, convertedPurchase, convertedProfit, fxEffect, "VALUED", holding.getUpdatedAt());
    }

    private PortfolioResponse portfolioResponse(List<Holding> holdings, LatestFxRate usdKrw) {
        boolean conversionComplete = holdings.stream().allMatch(item -> item.convertedEvaluationAmount() != null);
        BigDecimal evaluation = conversionComplete
                ? holdings.stream().map(Holding::convertedEvaluationAmount).reduce(BigDecimal.ZERO, BigDecimal::add)
                : null;
        boolean profitComplete = conversionComplete && holdings.stream().allMatch(item -> item.convertedPurchaseAmount() != null);
        BigDecimal purchase = profitComplete
                ? holdings.stream().map(Holding::convertedPurchaseAmount).reduce(BigDecimal.ZERO, BigDecimal::add)
                : null;
        BigDecimal profit = profitComplete ? won(evaluation.subtract(purchase)) : null;
        List<AppliedFxRate> rates = usdKrw == null || holdings.stream().noneMatch(item -> "USD".equals(item.currency()))
                ? List.of()
                : List.of(new AppliedFxRate("USD/KRW", usdKrw.rate(), usdKrw.asOf(), usdKrw.source(), usdKrw.rateType(), usdKrw.freshness()));
        return new PortfolioResponse("KRW", evaluation == null ? null : won(evaluation),
                purchase == null ? null : won(purchase), profit, conversionComplete, profitComplete,
                rates, summarizeByCurrency(holdings), holdings);
    }

    private List<CurrencySummary> summarizeByCurrency(List<Holding> holdings) {
        Map<String, List<Holding>> grouped = new LinkedHashMap<>();
        for (Holding holding : holdings) grouped.computeIfAbsent(holding.currency(), ignored -> new ArrayList<>()).add(holding);
        return grouped.entrySet().stream().map(entry -> {
            BigDecimal purchase = entry.getValue().stream().map(Holding::purchaseAmount)
                    .reduce(BigDecimal.ZERO.setScale(4), BigDecimal::add);
            boolean complete = entry.getValue().stream().allMatch(item -> item.evaluationAmount() != null);
            if (!complete) return new CurrencySummary(entry.getKey(), money(purchase), null, null, null, false);
            BigDecimal evaluation = entry.getValue().stream().map(Holding::evaluationAmount)
                    .reduce(BigDecimal.ZERO.setScale(4), BigDecimal::add);
            BigDecimal profit = money(evaluation.subtract(purchase));
            BigDecimal returnRate = purchase.signum() == 0 ? null
                    : profit.divide(purchase, 8, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
            return new CurrencySummary(entry.getKey(), money(purchase), money(evaluation), profit, returnRate, true);
        }).toList();
    }

    private BigDecimal convertedEvaluation(PortfolioHolding holding, BigDecimal evaluation, LatestFxRate usdKrw) {
        if ("KRW".equals(holding.getCurrency())) return won(evaluation);
        if ("USD".equals(holding.getCurrency()) && usdKrw != null) return won(evaluation.multiply(usdKrw.rate()));
        return null;
    }

    private BigDecimal convertedPurchase(PortfolioHolding holding, BigDecimal purchase) {
        if ("KRW".equals(holding.getCurrency())) return won(purchase);
        if ("USD".equals(holding.getCurrency()) && holding.getAveragePurchaseFxRate() != null) return won(purchase.multiply(holding.getAveragePurchaseFxRate()));
        return null;
    }

    private BigDecimal fxEffect(PortfolioHolding holding, BigDecimal evaluation, LatestFxRate usdKrw) {
        if (!"USD".equals(holding.getCurrency()) || usdKrw == null || holding.getAveragePurchaseFxRate() == null) return null;
        return won(evaluation.multiply(usdKrw.rate().subtract(holding.getAveragePurchaseFxRate())));
    }

    private void validatePurchaseFx(Stock stock, BigDecimal rate, String base, String quote) {
        if (rate == null) return;
        if (!"USD".equals(stock.getCurrency()) || !"USD".equals(upper(base)) || !"KRW".equals(upper(quote))) {
            throw PortfolioException.invalidFxPair();
        }
    }

    private BigDecimal money(BigDecimal value) { return value.setScale(4, RoundingMode.HALF_UP); }
    private BigDecimal won(BigDecimal value) { return value.setScale(0, RoundingMode.HALF_UP); }
    private String upper(String value) { return value == null ? null : value.trim().toUpperCase(Locale.ROOT); }
}
