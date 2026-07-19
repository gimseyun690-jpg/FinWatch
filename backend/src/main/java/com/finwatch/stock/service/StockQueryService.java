package com.finwatch.stock.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.finwatch.realtime.RealtimeCandleAggregator;
import com.finwatch.realtime.RealtimeQuoteHub;
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
import com.finwatch.stock.dto.StockSearchResponses.CanonicalStockDetail;
import com.finwatch.stock.repository.MarketPriceRepository;
import com.finwatch.stock.repository.StockRepository;
import com.finwatch.technical.TechnicalAnalysisCalculator;
import com.finwatch.technical.TechnicalAnalysisCalculator.Candle;
import com.finwatch.technical.TechnicalAnalysisCalculator.Result;

@Service
@Transactional(readOnly = true)
public class StockQueryService {

    private static final String DAILY_INTERVAL = "1D";
    private static final String WEEKLY_INTERVAL = "1W";
    private static final String MONTHLY_INTERVAL = "1M";
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    private static final String CALCULATION_VERSION = "technical-v2-wilder";
    private static final String DISCLAIMER = "기술적 신호는 투자 권유가 아닌 참고 정보입니다.";

    private final StockRepository stockRepository;
    private final MarketPriceRepository marketPriceRepository;
    private final TechnicalAnalysisCalculator calculator;
    private final RealtimeQuoteHub realtimeQuoteHub;
    private final RealtimeCandleAggregator candleAggregator;

    public StockQueryService(
            StockRepository stockRepository,
            MarketPriceRepository marketPriceRepository,
            TechnicalAnalysisCalculator calculator,
            RealtimeQuoteHub realtimeQuoteHub,
            RealtimeCandleAggregator candleAggregator) {
        this.stockRepository = stockRepository;
        this.marketPriceRepository = marketPriceRepository;
        this.calculator = calculator;
        this.realtimeQuoteHub = realtimeQuoteHub;
        this.candleAggregator = candleAggregator;
    }

    public List<StockSummary> getStocks() {
        return stockRepository.findAllActiveWithPrices().stream()
                .map(this::toSummary)
                .toList();
    }

    public StockSummary getStock(String symbol) {
        return toSummary(findStock(symbol));
    }

    public CanonicalStockDetail getStock(String market, String symbol) {
        return toCanonicalDetail(findStock(market, symbol));
    }

    public PriceHistory getPriceHistory(String symbol, String period, String interval) {
        return getPriceHistory(findStock(symbol), period, interval);
    }

    public PriceHistory getPriceHistory(String market, String symbol, String period, String interval) {
        return getPriceHistory(findStock(market, symbol), period, interval);
    }

    private PriceHistory getPriceHistory(Stock stock, String period, String interval) {
        String normalizedPeriod = period.toUpperCase(Locale.ROOT);
        String normalizedInterval = interval.toUpperCase(Locale.ROOT);
        if (!List.of(DAILY_INTERVAL, WEEKLY_INTERVAL, MONTHLY_INTERVAL).contains(normalizedInterval)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 조회 간격입니다.");
        }
        List<MarketPrice> dailyPrices = marketPriceRepository
                .findAllByStockIdAndIntervalOrderByRecordedAtAsc(stock.getId(), DAILY_INTERVAL);
        List<HistoryBar> allBars = aggregateBars(
                dailyPrices.stream().map(HistoryBar::from).toList(),
                normalizedInterval,
                zoneFor(stock));
        List<HistoryBar> prices = filterPrices(allBars, normalizedPeriod);
        if (prices.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "가격 이력이 없습니다.");
        }
        Map<Instant, TechnicalSeriesPoint> indicatorsByTime = buildIndicatorSeries(allBars).stream()
                .collect(Collectors.toMap(IndicatorAtTime::time, IndicatorAtTime::indicators));
        List<PricePoint> items = prices.stream()
                .map(price -> new PricePoint(
                        price.time(),
                        price.open(),
                        price.high(),
                        price.low(),
                        price.close(),
                        price.volume(),
                        indicatorsByTime.get(price.time())))
                .toList();
        List<String> sources = dailyPrices.stream().map(MarketPrice::getSource).distinct().toList();
        String source = sources.size() == 1 ? sources.getFirst() : sources.isEmpty() ? "UNKNOWN" : "MIXED";
        return new PriceHistory(stock.getSymbol(), normalizedInterval, normalizedPeriod, source, items);
    }

    public PriceHistory getIntradayPriceHistory(String symbol, int limit) {
        return getIntradayPriceHistory(findStock(symbol), limit);
    }

    public PriceHistory getIntradayPriceHistory(String market, String symbol, int limit) {
        return getIntradayPriceHistory(findStock(market, symbol), limit);
    }

    private PriceHistory getIntradayPriceHistory(Stock stock, int limit) {
        if (limit < 1 || limit > 600) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit은 1~600 범위여야 합니다.");
        }
        List<PricePoint> items = candleAggregator.find(stock.getMarket(), stock.getSymbol(), limit).stream()
                .map(candle -> new PricePoint(
                        candle.time(),
                        candle.open(),
                        candle.high(),
                        candle.low(),
                        candle.close(),
                        candle.volume(),
                        null))
                .toList();
        return new PriceHistory(stock.getSymbol(), "1m", "SESSION", "LIVE", items);
    }

    public TechnicalAnalysis getTechnicalAnalysis(String symbol) {
        return getTechnicalAnalysis(findStock(symbol));
    }

    public TechnicalAnalysis getTechnicalAnalysis(String market, String symbol) {
        return getTechnicalAnalysis(findStock(market, symbol));
    }

    private TechnicalAnalysis getTechnicalAnalysis(Stock stock) {
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
        var liveQuote = realtimeQuoteHub.find(stock.getMarket(), stock.getSymbol());
        if (liveQuote.isPresent()) {
            var quote = liveQuote.get();
            return new StockSummary(
                    stock.getSymbol(),
                    stock.getName(),
                    stock.getMarket(),
                    stock.getCurrency(),
                    quote.price(),
                    quote.change(),
                    quote.changeRate(),
                    quote.volume() == null || quote.volume().signum() == 0 ? latest.getVolume() : quote.volume(),
                    quote.asOf(),
                    quote.source());
        }
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

    private CanonicalStockDetail toCanonicalDetail(Stock stock) {
        List<MarketPrice> dailyHistory = marketPriceRepository
                .findAllByStockIdAndIntervalOrderByRecordedAtAsc(stock.getId(), DAILY_INTERVAL);
        Optional<MarketPrice> latest = dailyHistory.isEmpty()
                ? Optional.empty()
                : Optional.of(dailyHistory.getLast());
        var live = realtimeQuoteHub.find(stock.getMarket(), stock.getSymbol());
        StockSummary quote = latest.isPresent()
                ? toSummary(stock)
                : live.map(value -> new StockSummary(
                        stock.getSymbol(),
                        stock.getName(),
                        stock.getMarket(),
                        stock.getCurrency(),
                        value.price(),
                        value.change(),
                        value.changeRate(),
                        value.volume() == null ? BigDecimal.ZERO : value.volume(),
                        value.asOf(),
                        value.source()))
                    .orElse(null);
        String availability = latest.isPresent() ? "READY" : live.isPresent() ? "PARTIAL" : "METADATA_ONLY";
        return new CanonicalStockDetail(
                stock.getId(),
                stock.getMarket(),
                stock.getExchange(),
                stock.getSymbol(),
                stock.getName(),
                stock.getEnglishName(),
                stock.getInstrumentType(),
                stock.getCurrency(),
                stock.isActive(),
                stock.isTradable(),
                stock.getStatus(),
                availability,
                stock.getProvider(),
                stock.getCatalogUpdatedAt(),
                !dailyHistory.isEmpty(),
                dailyHistory.size(),
                latest.map(MarketPrice::getRecordedAt).orElse(null),
                latest.map(MarketPrice::getSource).orElse(null),
                quote == null ? null : quote.price(),
                quote == null ? null : quote.change(),
                quote == null ? null : quote.changeRate(),
                quote == null ? null : quote.volume(),
                quote == null ? null : quote.asOf(),
                quote == null ? null : quote.source());
    }

    private Stock findStock(String symbol) {
        String normalizedSymbol = normalizeSymbol(symbol);
        List<Stock> candidates = stockRepository
                .findAllBySymbolIgnoreCaseAndActiveTrueOrderByMarketAsc(normalizedSymbol);
        if (candidates.isEmpty()) {
            throw new StockQueryException(HttpStatus.NOT_FOUND, "STOCK_NOT_FOUND", "종목을 찾을 수 없습니다.");
        }
        if (candidates.size() > 1) {
            throw new StockQueryException(
                    HttpStatus.CONFLICT,
                    "STOCK_SYMBOL_AMBIGUOUS",
                    "같은 symbol이 여러 시장에 존재합니다. market을 포함한 canonical 경로를 사용해 주세요.");
        }
        return candidates.getFirst();
    }

    private Stock findStock(String market, String symbol) {
        String normalizedMarket = normalizeMarket(market);
        String normalizedSymbol = normalizeSymbol(symbol);
        return stockRepository.findByMarketAndSymbolAndActiveTrue(normalizedMarket, normalizedSymbol)
                .orElseThrow(() -> new StockQueryException(
                        HttpStatus.NOT_FOUND,
                        "STOCK_NOT_FOUND",
                        "해당 시장의 종목을 찾을 수 없습니다."));
    }

    private String normalizeMarket(String market) {
        if (market == null || !market.trim().matches("[A-Za-z0-9._-]{2,30}")) {
            throw new StockQueryException(HttpStatus.BAD_REQUEST, "STOCK_MARKET_INVALID", "market 형식이 올바르지 않습니다.");
        }
        return market.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeSymbol(String symbol) {
        if (symbol == null || !symbol.trim().matches("[A-Za-z0-9._-]{1,30}")) {
            throw new StockQueryException(HttpStatus.BAD_REQUEST, "STOCK_SYMBOL_INVALID", "symbol 형식이 올바르지 않습니다.");
        }
        return symbol.trim().toUpperCase(Locale.ROOT);
    }

    private List<HistoryBar> filterPrices(List<HistoryBar> allPrices, String period) {
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
        Instant latestRecordedAt = allPrices.getLast().time();
        Instant from = latestRecordedAt.minus(days, ChronoUnit.DAYS);
        return allPrices.stream()
                .filter(price -> !price.time().isBefore(from))
                .toList();
    }

    private List<HistoryBar> aggregateBars(List<HistoryBar> dailyBars, String interval, ZoneId zone) {
        if (DAILY_INTERVAL.equals(interval)) {
            return dailyBars;
        }
        Map<String, List<HistoryBar>> groups = new LinkedHashMap<>();
        for (HistoryBar bar : dailyBars) {
            var date = bar.time().atZone(zone).toLocalDate();
            String key = WEEKLY_INTERVAL.equals(interval)
                    ? date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()
                    : YearMonth.from(date).toString();
            groups.computeIfAbsent(key, ignored -> new java.util.ArrayList<>()).add(bar);
        }
        return groups.values().stream().map(group -> {
            HistoryBar first = group.getFirst();
            HistoryBar last = group.getLast();
            BigDecimal high = group.stream().map(HistoryBar::high).max(BigDecimal::compareTo).orElse(first.high());
            BigDecimal low = group.stream().map(HistoryBar::low).min(BigDecimal::compareTo).orElse(first.low());
            BigDecimal volume = group.stream().map(HistoryBar::volume).reduce(BigDecimal.ZERO, BigDecimal::add);
            return new HistoryBar(last.time(), first.open(), high, low, last.close(), volume);
        }).toList();
    }

    private ZoneId zoneFor(Stock stock) {
        return "KRX".equalsIgnoreCase(stock.getMarket()) ? SEOUL : NEW_YORK;
    }

    private List<IndicatorAtTime> buildIndicatorSeries(List<HistoryBar> prices) {
        List<IndicatorAtTime> result = new java.util.ArrayList<>(prices.size());
        BigDecimal closeSum5 = BigDecimal.ZERO;
        BigDecimal closeSum20 = BigDecimal.ZERO;
        BigDecimal closeSum60 = BigDecimal.ZERO;
        BigDecimal volumeSum20 = BigDecimal.ZERO;
        BigDecimal fastEma = null;
        BigDecimal slowEma = null;
        BigDecimal signalEma = null;
        BigDecimal averageGain = BigDecimal.ZERO;
        BigDecimal averageLoss = BigDecimal.ZERO;
        BigDecimal atr = BigDecimal.ZERO;
        BigDecimal fastMultiplier = emaMultiplier(12);
        BigDecimal slowMultiplier = emaMultiplier(26);
        BigDecimal signalMultiplier = emaMultiplier(9);

        for (int index = 0; index < prices.size(); index++) {
            HistoryBar price = prices.get(index);
            BigDecimal close = price.close();
            int size = index + 1;

            closeSum5 = addRollingValue(closeSum5, prices, index, 5, close);
            closeSum20 = addRollingValue(closeSum20, prices, index, 20, close);
            closeSum60 = addRollingValue(closeSum60, prices, index, 60, close);
            volumeSum20 = volumeSum20.add(price.volume());
            if (index >= 20) {
                volumeSum20 = volumeSum20.subtract(prices.get(index - 20).volume());
            }

            fastEma = nextEma(fastEma, close, fastMultiplier);
            slowEma = nextEma(slowEma, close, slowMultiplier);
            BigDecimal macdValue = fastEma.subtract(slowEma);
            signalEma = nextEma(signalEma, macdValue, signalMultiplier);
            BigDecimal macdHistogram = macdValue.subtract(signalEma);

            BigDecimal rsi = null;
            BigDecimal currentAtr = null;
            if (index > 0) {
                BigDecimal previousClose = prices.get(index - 1).close();
                BigDecimal delta = close.subtract(previousClose);
                BigDecimal gain = delta.max(BigDecimal.ZERO);
                BigDecimal loss = delta.min(BigDecimal.ZERO).abs();
                BigDecimal trueRange = price.high().subtract(price.low()).abs()
                        .max(price.high().subtract(previousClose).abs())
                        .max(price.low().subtract(previousClose).abs());

                if (index <= 14) {
                    averageGain = averageGain.add(gain);
                    averageLoss = averageLoss.add(loss);
                    atr = atr.add(trueRange);
                    if (index == 14) {
                        averageGain = divide(averageGain, 14, 12);
                        averageLoss = divide(averageLoss, 14, 12);
                        atr = divide(atr, 14, 4);
                    }
                } else {
                    averageGain = divide(averageGain.multiply(BigDecimal.valueOf(13)).add(gain), 14, 12);
                    averageLoss = divide(averageLoss.multiply(BigDecimal.valueOf(13)).add(loss), 14, 12);
                    atr = divide(atr.multiply(BigDecimal.valueOf(13)).add(trueRange), 14, 12);
                }
                if (index >= 14) {
                    rsi = rsiFromAverages(averageGain, averageLoss);
                    currentAtr = atr.setScale(4, RoundingMode.HALF_UP);
                }
            }

            TechnicalAnalysisCalculator.BollingerBands bollinger = size >= 20
                    ? calculator.bollingerBands(
                            prices.subList(index - 19, index + 1).stream().map(HistoryBar::close).toList(),
                            20,
                            2)
                    : null;
            boolean macdReady = size >= 26;
            result.add(new IndicatorAtTime(
                    price.time(),
                    new TechnicalSeriesPoint(
                            size >= 5 ? movingAverage(closeSum5, 5) : null,
                            size >= 20 ? movingAverage(closeSum20, 20) : null,
                            size >= 60 ? movingAverage(closeSum60, 60) : null,
                            size >= 20 ? movingAverage(volumeSum20, 20) : null,
                            bollinger == null ? null : bollinger.upper(),
                            bollinger == null ? null : bollinger.middle(),
                            bollinger == null ? null : bollinger.lower(),
                            rsi,
                            macdReady ? macdValue.setScale(4, RoundingMode.HALF_UP) : null,
                            macdReady ? signalEma.setScale(4, RoundingMode.HALF_UP) : null,
                            macdReady ? macdHistogram.setScale(4, RoundingMode.HALF_UP) : null,
                            currentAtr)));
        }
        return List.copyOf(result);
    }

    private BigDecimal addRollingValue(
            BigDecimal sum,
            List<HistoryBar> prices,
            int index,
            int period,
            BigDecimal value) {
        BigDecimal next = sum.add(value);
        return index >= period ? next.subtract(prices.get(index - period).close()) : next;
    }

    private BigDecimal movingAverage(BigDecimal sum, int period) {
        return divide(sum, period, 4);
    }

    private BigDecimal emaMultiplier(int period) {
        return BigDecimal.valueOf(2)
                .divide(BigDecimal.valueOf(period + 1L), 12, RoundingMode.HALF_UP);
    }

    private BigDecimal nextEma(BigDecimal previous, BigDecimal value, BigDecimal multiplier) {
        if (previous == null) {
            return value.setScale(12, RoundingMode.HALF_UP);
        }
        return value.subtract(previous)
                .multiply(multiplier)
                .add(previous)
                .setScale(12, RoundingMode.HALF_UP);
    }

    private BigDecimal divide(BigDecimal value, int divisor, int scale) {
        return value.divide(BigDecimal.valueOf(divisor), scale, RoundingMode.HALF_UP);
    }

    private BigDecimal rsiFromAverages(BigDecimal averageGain, BigDecimal averageLoss) {
        if (averageGain.signum() == 0 && averageLoss.signum() == 0) {
            return BigDecimal.valueOf(50).setScale(4);
        }
        if (averageLoss.signum() == 0) {
            return BigDecimal.valueOf(100).setScale(4);
        }
        if (averageGain.signum() == 0) {
            return BigDecimal.ZERO.setScale(4);
        }
        BigDecimal relativeStrength = averageGain.divide(averageLoss, 12, RoundingMode.HALF_UP);
        return BigDecimal.valueOf(100)
                .subtract(BigDecimal.valueOf(100)
                        .divide(BigDecimal.ONE.add(relativeStrength), 12, RoundingMode.HALF_UP))
                .setScale(4, RoundingMode.HALF_UP);
    }

    private record IndicatorAtTime(Instant time, TechnicalSeriesPoint indicators) {
    }

    private record HistoryBar(
            Instant time,
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close,
            BigDecimal volume) {

        private static HistoryBar from(MarketPrice price) {
            return new HistoryBar(
                    price.getRecordedAt(),
                    price.getOpenPrice(),
                    price.getHighPrice(),
                    price.getLowPrice(),
                    price.getClosePrice(),
                    price.getVolume());
        }
    }
}
