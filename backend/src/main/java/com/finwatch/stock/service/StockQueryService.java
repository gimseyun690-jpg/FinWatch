package com.finwatch.stock.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.finwatch.stock.domain.MarketPrice;
import com.finwatch.stock.domain.Stock;
import com.finwatch.stock.dto.StockResponses.Macd;
import com.finwatch.stock.dto.StockResponses.Atr;
import com.finwatch.stock.dto.StockResponses.BollingerBands;
import com.finwatch.stock.dto.StockResponses.MovingAverages;
import com.finwatch.stock.dto.StockResponses.PriceHistory;
import com.finwatch.stock.dto.StockResponses.PricePoint;
import com.finwatch.stock.dto.StockResponses.Rsi;
import com.finwatch.stock.dto.StockResponses.StockSummary;
import com.finwatch.stock.dto.StockResponses.TechnicalAnalysis;
import com.finwatch.stock.dto.StockResponses.TechnicalEvent;
import com.finwatch.stock.dto.StockResponses.TechnicalSeriesPoint;
import com.finwatch.stock.repository.MarketPriceRepository;
import com.finwatch.stock.repository.StockRepository;
import com.finwatch.technical.TechnicalAnalysisCalculator;
import com.finwatch.technical.TechnicalAnalysisCalculator.Candle;
import com.finwatch.technical.TechnicalAnalysisCalculator.Result;

@Service
@Transactional(readOnly = true)
public class StockQueryService {

    private static final String DAILY_INTERVAL = "1D";
    private static final String CALCULATION_VERSION = "technical-v2-wilder";
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
        List<MarketPrice> allPrices = marketPriceRepository
                .findAllByStockIdAndIntervalOrderByRecordedAtAsc(stock.getId(), normalizedInterval);
        List<MarketPrice> prices = filterPrices(allPrices, normalizedPeriod);
        if (prices.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "가격 이력이 없습니다.");
        }
        Map<Instant, TechnicalSeriesPoint> indicatorsByTime = buildIndicatorSeries(allPrices).stream()
                .collect(Collectors.toMap(IndicatorAtTime::time, IndicatorAtTime::indicators));
        List<PricePoint> items = prices.stream()
                .map(price -> new PricePoint(
                        price.getRecordedAt(),
                        price.getOpenPrice(),
                        price.getHighPrice(),
                        price.getLowPrice(),
                        price.getClosePrice(),
                        price.getVolume(),
                        indicatorsByTime.get(price.getRecordedAt())))
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

        Result result = calculator.calculateMarket(prices.stream()
                .map(price -> new Candle(
                        price.getOpenPrice(),
                        price.getHighPrice(),
                        price.getLowPrice(),
                        price.getClosePrice(),
                        price.getVolume()))
                .toList());
        return new TechnicalAnalysis(
                stock.getSymbol(),
                prices.getLast().getRecordedAt(),
                CALCULATION_VERSION,
                result.summarySignal().name(),
                new MovingAverages(
                        result.ma5(), result.ma20(), result.ma60(), result.movingAverageSignal().name()),
                new Rsi(14, "WILDER", result.rsi(), result.rsiSignal().name()),
                new Macd(
                        result.macd().value(),
                        result.macd().signalLine(),
                        result.macd().histogram(),
                        result.macdSignal().name()),
                new BollingerBands(
                        result.bollingerBands().period(),
                        result.bollingerBands().deviationMultiplier(),
                        result.bollingerBands().upper(),
                        result.bollingerBands().middle(),
                        result.bollingerBands().lower(),
                        result.bollingerBandwidthPercent()),
                new Atr(14, result.atr(), result.atrPercent()),
                result.volumeMa20(),
                result.crossoverEvents().stream()
                        .map(event -> new TechnicalEvent(
                                prices.get(event.index()).getRecordedAt(),
                                event.type(),
                                event.signal().name()))
                        .toList(),
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

    private List<MarketPrice> filterPrices(List<MarketPrice> allPrices, String period) {
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

    private List<IndicatorAtTime> buildIndicatorSeries(List<MarketPrice> prices) {
        List<BigDecimal> closes = prices.stream().map(MarketPrice::getClosePrice).toList();
        List<BigDecimal> volumes = prices.stream().map(MarketPrice::getVolume).toList();
        List<Candle> candles = prices.stream()
                .map(price -> new Candle(
                        price.getOpenPrice(),
                        price.getHighPrice(),
                        price.getLowPrice(),
                        price.getClosePrice(),
                        price.getVolume()))
                .toList();
        return java.util.stream.IntStream.range(0, prices.size())
                .mapToObj(index -> {
                    int size = index + 1;
                    List<BigDecimal> closePrefix = closes.subList(0, size);
                    TechnicalAnalysisCalculator.MacdValue macd = size >= 26 ? calculator.macd(closePrefix) : null;
                    TechnicalAnalysisCalculator.BollingerBands bollinger = size >= 20
                            ? calculator.bollingerBands(closePrefix, 20, 2)
                            : null;
                    return new IndicatorAtTime(
                            prices.get(index).getRecordedAt(),
                            new TechnicalSeriesPoint(
                                    size >= 5 ? calculator.simpleMovingAverage(closePrefix, 5) : null,
                                    size >= 20 ? calculator.simpleMovingAverage(closePrefix, 20) : null,
                                    size >= 60 ? calculator.simpleMovingAverage(closePrefix, 60) : null,
                                    size >= 20 ? calculator.simpleMovingAverage(volumes.subList(0, size), 20) : null,
                                    bollinger == null ? null : bollinger.upper(),
                                    bollinger == null ? null : bollinger.middle(),
                                    bollinger == null ? null : bollinger.lower(),
                                    size >= 15 ? calculator.relativeStrengthIndex(closePrefix, 14) : null,
                                    macd == null ? null : macd.value(),
                                    macd == null ? null : macd.signalLine(),
                                    macd == null ? null : macd.histogram(),
                                    size >= 15 ? calculator.averageTrueRange(candles.subList(0, size), 14) : null));
                })
                .toList();
    }

    private record IndicatorAtTime(Instant time, TechnicalSeriesPoint indicators) {
    }
}
