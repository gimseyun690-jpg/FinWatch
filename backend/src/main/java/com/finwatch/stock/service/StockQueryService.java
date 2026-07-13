package com.finwatch.stock.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.finwatch.stock.domain.MarketPrice;
import com.finwatch.stock.domain.Stock;
import com.finwatch.stock.dto.StockResponses.Macd;
import com.finwatch.stock.dto.StockResponses.MovingAverages;
import com.finwatch.stock.dto.StockResponses.PriceHistory;
import com.finwatch.stock.dto.StockResponses.PricePoint;
import com.finwatch.stock.dto.StockResponses.Rsi;
import com.finwatch.stock.dto.StockResponses.StockSummary;
import com.finwatch.stock.dto.StockResponses.TechnicalAnalysis;
import com.finwatch.stock.repository.MarketPriceRepository;
import com.finwatch.stock.repository.StockRepository;
import com.finwatch.technical.TechnicalAnalysisCalculator;
import com.finwatch.technical.TechnicalAnalysisCalculator.Result;

@Service
@Transactional(readOnly = true)
public class StockQueryService {

    private static final String DAILY_INTERVAL = "1D";
    private static final String DISCLAIMER = "기술적 신호는 투자 권유가 아닌 참고 정보입니다.";

    private final StockRepository stockRepository;
    private final MarketPriceRepository marketPriceRepository;
    private final TechnicalAnalysisCalculator calculator;

    public StockQueryService(
            StockRepository stockRepository,
            MarketPriceRepository marketPriceRepository,
            TechnicalAnalysisCalculator calculator) {
        this.stockRepository = stockRepository;
        this.marketPriceRepository = marketPriceRepository;
        this.calculator = calculator;
    }

    public List<StockSummary> getStocks() {
        return stockRepository.findAllByActiveTrueOrderByMarketAscNameAsc().stream()
                .map(this::toSummary)
                .toList();
    }

    public StockSummary getStock(String symbol) {
        return toSummary(findStock(symbol));
    }

    public PriceHistory getPriceHistory(String symbol, String period, String interval) {
        Stock stock = findStock(symbol);
        String normalizedPeriod = period.toUpperCase(Locale.ROOT);
        String normalizedInterval = interval.toUpperCase(Locale.ROOT);
        if (!DAILY_INTERVAL.equals(normalizedInterval)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 조회 간격입니다.");
        }
        List<MarketPrice> prices = loadPrices(stock.getId(), normalizedPeriod, normalizedInterval);
        if (prices.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "가격 이력이 없습니다.");
        }
        List<PricePoint> items = prices.stream()
                .map(price -> new PricePoint(
                        price.getRecordedAt(),
                        price.getOpenPrice(),
                        price.getHighPrice(),
                        price.getLowPrice(),
                        price.getClosePrice(),
                        price.getVolume()))
                .toList();
        return new PriceHistory(stock.getSymbol(), normalizedInterval, normalizedPeriod, items);
    }

    public TechnicalAnalysis getTechnicalAnalysis(String symbol) {
        Stock stock = findStock(symbol);
        List<MarketPrice> prices = marketPriceRepository
                .findAllByStockIdAndIntervalOrderByRecordedAtAsc(stock.getId(), DAILY_INTERVAL);
        if (prices.size() < 60) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "기술적 분석 데이터가 부족합니다.");
        }

        Result result = calculator.calculate(prices.stream().map(MarketPrice::getClosePrice).toList());
        return new TechnicalAnalysis(
                stock.getSymbol(),
                prices.getLast().getRecordedAt(),
                result.summarySignal().name(),
                new MovingAverages(
                        result.ma5(), result.ma20(), result.ma60(), result.movingAverageSignal().name()),
                new Rsi(14, result.rsi(), result.rsiSignal().name()),
                new Macd(
                        result.macd().value(),
                        result.macd().signalLine(),
                        result.macd().histogram(),
                        result.macdSignal().name()),
                DISCLAIMER);
    }

    private StockSummary toSummary(Stock stock) {
        MarketPrice latest = marketPriceRepository.findTopByStockIdOrderByRecordedAtDesc(stock.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "현재가가 없습니다."));
        List<MarketPrice> history = marketPriceRepository
                .findAllByStockIdAndIntervalOrderByRecordedAtAsc(stock.getId(), DAILY_INTERVAL);
        BigDecimal previousClose = history.size() > 1
                ? history.get(history.size() - 2).getClosePrice()
                : latest.getClosePrice();
        BigDecimal change = latest.getClosePrice().subtract(previousClose);
        BigDecimal changeRate = previousClose.signum() == 0
                ? BigDecimal.ZERO
                : change.multiply(BigDecimal.valueOf(100)).divide(previousClose, 4, RoundingMode.HALF_UP);

        return new StockSummary(
                stock.getSymbol(),
                stock.getName(),
                stock.getMarket(),
                stock.getCurrency(),
                latest.getClosePrice(),
                change,
                changeRate,
                latest.getVolume(),
                latest.getRecordedAt(),
                latest.getSource());
    }

    private Stock findStock(String symbol) {
        return stockRepository.findFirstBySymbolAndActiveTrue(symbol)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "종목을 찾을 수 없습니다."));
    }

    private List<MarketPrice> loadPrices(Long stockId, String period, String interval) {
        List<MarketPrice> allPrices = marketPriceRepository
                .findAllByStockIdAndIntervalOrderByRecordedAtAsc(stockId, interval);
        if (allPrices.isEmpty() || "ALL".equals(period)) {
            return allPrices;
        }
        long days = switch (period) {
            case "1M" -> 31;
            case "3M" -> 93;
            case "6M" -> 186;
            case "1Y" -> 366;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 조회 기간입니다.");
        };
        Instant latestRecordedAt = allPrices.getLast().getRecordedAt();
        Instant from = latestRecordedAt.minus(days, ChronoUnit.DAYS);
        return allPrices.stream()
                .filter(price -> !price.getRecordedAt().isBefore(from))
                .toList();
    }
}
