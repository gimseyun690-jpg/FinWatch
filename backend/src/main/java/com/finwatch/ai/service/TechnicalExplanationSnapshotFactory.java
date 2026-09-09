package com.finwatch.ai.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.finwatch.ai.dto.TechnicalExplanationInput;
import com.finwatch.ai.dto.TechnicalExplanationInput.TechnicalEvidence;
import com.finwatch.stock.domain.MarketPrice;
import com.finwatch.stock.domain.Stock;
import com.finwatch.stock.dto.StockResponses.TechnicalAnalysis;
import com.finwatch.stock.repository.MarketPriceRepository;
import com.finwatch.stock.repository.StockRepository;
import com.finwatch.stock.service.StockQueryService;
import com.finwatch.technical.TechnicalAnalysisCalculator;

import tools.jackson.databind.ObjectMapper;

import com.finwatch.realtime.RealtimeQuoteHub;

@Component
public class TechnicalExplanationSnapshotFactory {

    private static final String INTERVAL = "1D";
    private final StockRepository stockRepository;
    private final MarketPriceRepository marketPriceRepository;
    private final StockQueryService stockQueryService;
    private final RealtimeQuoteHub realtimeQuoteHub;
    private final ObjectMapper objectMapper;

    public TechnicalExplanationSnapshotFactory(
            StockRepository stockRepository,
            MarketPriceRepository marketPriceRepository,
            StockQueryService stockQueryService,
            RealtimeQuoteHub realtimeQuoteHub,
            ObjectMapper objectMapper) {
        this.stockRepository = stockRepository;
        this.marketPriceRepository = marketPriceRepository;
        this.stockQueryService = stockQueryService;
        this.realtimeQuoteHub = realtimeQuoteHub;
        this.objectMapper = objectMapper;
    }

    public SnapshotBundle create(String requestedMarket, String requestedSymbol, String requestedInterval) {
        String symbol = requestedSymbol.trim().toUpperCase(Locale.ROOT);
        String market = requestedMarket == null || requestedMarket.isBlank()
                ? null
                : requestedMarket.trim().toUpperCase(Locale.ROOT);
        String interval = requestedInterval == null || requestedInterval.isBlank()
                ? INTERVAL
                : requestedInterval.trim().toUpperCase(Locale.ROOT);
        if (!INTERVAL.equals(interval)) {
            throw new TechnicalExplanationException(
                    HttpStatus.BAD_REQUEST,
                    "TECHNICAL_INTERVAL_UNSUPPORTED",
                    "MVP에서는 1D 기술지표 해설만 지원합니다.");
        }
        Stock stock = findStock(market, symbol);
        List<MarketPrice> prices = marketPriceRepository
                .findAllByStockIdAndIntervalOrderByRecordedAtAsc(stock.getId(), INTERVAL);
        var liveQuote = realtimeQuoteHub.find(stock.getMarket(), stock.getSymbol());
        if (liveQuote.isPresent() && liveQuote.get().price() != null) {
            var quote = liveQuote.get();
            if (prices.isEmpty() || quote.asOf().isAfter(prices.getLast().getRecordedAt())) {
                MarketPrice virtualBar = MarketPrice.create(
                        stock, INTERVAL,
                        quote.price(), quote.price(), quote.price(), quote.price(),
                        quote.volume() != null ? quote.volume() : BigDecimal.ZERO,
                        quote.asOf(), quote.source() != null ? quote.source() : "live");
                prices = new ArrayList<>(prices);
                prices.add(virtualBar);
            }
        }
        if (prices.isEmpty()) {
            throw new TechnicalExplanationException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "TECHNICAL_DATA_INSUFFICIENT",
                    "기술지표 해설을 생성하기 위한 일봉 데이터가 없습니다.");
        }
        validatePrices(prices);

        MarketPrice latest = prices.getLast();
        if (latest.getRecordedAt().isAfter(Instant.now().plus(Duration.ofDays(2)))) {
            throw new TechnicalExplanationException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "TECHNICAL_SNAPSHOT_INVALID",
                    "미래 시각의 일봉은 기술지표 해설에 사용할 수 없습니다.");
        }
        TechnicalAnalysis analysis;
        try {
            analysis = stockQueryService.getTechnicalAnalysis(stock.getMarket(), symbol);
        } catch (RuntimeException exception) {
            throw new TechnicalExplanationException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "TECHNICAL_SNAPSHOT_INVALID",
                    "기술지표 입력 스냅샷을 만들 수 없습니다.",
                    exception);
        }
        String freshness = freshness(latest);
        List<TechnicalEvidence> evidence = evidence(latest, analysis, prices.size(), freshness);
        TechnicalExplanationInput input = new TechnicalExplanationInput(
                stock.getMarket(),
                stock.getSymbol(),
                stock.getCurrency(),
                INTERVAL,
                latest.getRecordedAt(),
                latest.getSource(),
                freshness,
                false,
                analysis.calculationVersion(),
                prices.size(),
                analysis.summarySignal(),
                evidence);
        return new SnapshotBundle(stock, input, sha256(input));
    }

    private Stock findStock(String market, String symbol) {
        if (market != null) {
            return stockRepository.findByMarketAndSymbolAndActiveTrue(market, symbol)
                    .orElseThrow(() -> new TechnicalExplanationException(
                            HttpStatus.NOT_FOUND,
                            "STOCK_NOT_FOUND",
                            "해당 시장의 종목을 찾을 수 없습니다."));
        }
        List<Stock> candidates = stockRepository
                .findAllBySymbolIgnoreCaseAndActiveTrueOrderByMarketAsc(symbol);
        if (candidates.isEmpty()) {
            throw new TechnicalExplanationException(
                    HttpStatus.NOT_FOUND,
                    "STOCK_NOT_FOUND",
                    "종목을 찾을 수 없습니다.");
        }
        if (candidates.size() > 1) {
            throw new TechnicalExplanationException(
                    HttpStatus.CONFLICT,
                    "STOCK_SYMBOL_AMBIGUOUS",
                    "같은 symbol이 여러 시장에 존재합니다. market을 함께 보내 주세요.");
        }
        return candidates.getFirst();
    }

    private List<TechnicalEvidence> evidence(
            MarketPrice latest,
            TechnicalAnalysis analysis,
            int sampleCount,
            String freshness) {
        List<TechnicalEvidence> result = new ArrayList<>();
        result.add(new TechnicalEvidence("I1", "MOVING_AVERAGE", values(
                "shortPeriod", BigDecimal.valueOf(TechnicalAnalysisCalculator.MOVING_AVERAGE_SHORT_PERIOD),
                "mediumPeriod", BigDecimal.valueOf(TechnicalAnalysisCalculator.MOVING_AVERAGE_MEDIUM_PERIOD),
                "longPeriod", BigDecimal.valueOf(TechnicalAnalysisCalculator.MOVING_AVERAGE_LONG_PERIOD),
                "price", latest.getClosePrice(),
                "ma5", analysis.movingAverages().ma5(),
                "ma20", analysis.movingAverages().ma20(),
                "ma60", analysis.movingAverages().ma60()),
                "현재가 " + number(latest.getClosePrice()) + " · MA5 " + number(analysis.movingAverages().ma5())
                        + " · MA20 " + number(analysis.movingAverages().ma20())
                        + " · MA60 " + number(analysis.movingAverages().ma60())
                        + " · 신호 " + analysis.movingAverages().signal()));
        result.add(new TechnicalEvidence("I2", "RSI", values(
                "period", BigDecimal.valueOf(analysis.rsi().period()),
                "value", analysis.rsi().value(),
                "lowerBoundary", BigDecimal.valueOf(30),
                "upperBoundary", BigDecimal.valueOf(70)),
                "Wilder RSI14 " + number(analysis.rsi().value()) + " · 신호 " + analysis.rsi().signal()));
        result.add(new TechnicalEvidence("I3", "MACD", values(
                "fastPeriod", BigDecimal.valueOf(TechnicalAnalysisCalculator.MACD_FAST_PERIOD),
                "slowPeriod", BigDecimal.valueOf(TechnicalAnalysisCalculator.MACD_SLOW_PERIOD),
                "signalPeriod", BigDecimal.valueOf(TechnicalAnalysisCalculator.MACD_SIGNAL_PERIOD),
                "value", analysis.macd().value(),
                "signal", analysis.macd().signalLine(),
                "histogram", analysis.macd().histogram()),
                "MACD " + TechnicalAnalysisCalculator.MACD_FAST_PERIOD + "·"
                        + TechnicalAnalysisCalculator.MACD_SLOW_PERIOD + "·"
                        + TechnicalAnalysisCalculator.MACD_SIGNAL_PERIOD + " · "
                        + number(analysis.macd().value()) + " · Signal "
                        + number(analysis.macd().signalLine()) + " · Histogram "
                        + number(analysis.macd().histogram()) + " · 신호 " + analysis.macd().signal()));
        result.add(new TechnicalEvidence("I4", "BOLLINGER_BANDS", values(
                "period", BigDecimal.valueOf(TechnicalAnalysisCalculator.BOLLINGER_PERIOD),
                "deviationMultiplier", BigDecimal.valueOf(TechnicalAnalysisCalculator.BOLLINGER_DEVIATION_MULTIPLIER),
                "upper", analysis.bollingerBands().upper(),
                "middle", analysis.bollingerBands().middle(),
                "lower", analysis.bollingerBands().lower(),
                "bandwidthPercent", analysis.bollingerBands().bandwidthPercent()),
                "볼린저 20·2 상단 " + number(analysis.bollingerBands().upper()) + " · 중단 "
                        + number(analysis.bollingerBands().middle()) + " · 하단 "
                        + number(analysis.bollingerBands().lower()) + " · 폭 "
                        + number(analysis.bollingerBands().bandwidthPercent()) + "%"));
        result.add(new TechnicalEvidence("I5", "ATR", values(
                "period", BigDecimal.valueOf(analysis.atr().period()),
                "value", analysis.atr().value(),
                "percent", analysis.atr().percent()),
                "ATR14 " + number(analysis.atr().value()) + " · 현재가 대비 "
                        + number(analysis.atr().percent()) + "%"));
        BigDecimal volumeRatio = analysis.volumeMa20().signum() == 0
                ? BigDecimal.ZERO
                : latest.getVolume().divide(analysis.volumeMa20(), 4, RoundingMode.HALF_UP);
        result.add(new TechnicalEvidence("I6", "VOLUME", values(
                "movingAveragePeriod", BigDecimal.valueOf(TechnicalAnalysisCalculator.VOLUME_MOVING_AVERAGE_PERIOD),
                "current", latest.getVolume(),
                "ma20", analysis.volumeMa20(),
                "ratio", volumeRatio),
                "거래량 " + number(latest.getVolume()) + " · Volume MA20 "
                        + number(analysis.volumeMa20()) + " · 평균 대비 " + number(volumeRatio) + "배"));

        List<com.finwatch.stock.dto.StockResponses.TechnicalEvent> events = analysis.events();
        int start = Math.max(0, events.size() - 3);
        for (int index = start; index < events.size(); index++) {
            var event = events.get(index);
            String id = "I" + (result.size() + 1);
            result.add(new TechnicalEvidence(id, "TECHNICAL_EVENT", stringValues(
                    "type", event.type(),
                    "signal", event.signal(),
                    "occurredAt", event.time().toString()),
                    event.time() + " · " + event.type() + " · " + event.signal()));
        }
        String qualityId = "I" + (result.size() + 1);
        result.add(new TechnicalEvidence(qualityId, "DATA_QUALITY", stringValues(
                "source", latest.getSource(),
                "freshness", freshness,
                "sampleCount", Integer.toString(sampleCount),
                "latestRecordedAt", latest.getRecordedAt().toString(),
                "adjusted", "false"),
                "출처 " + latest.getSource() + " · " + freshness + " · 일봉 " + sampleCount + "개 · 조정 여부 미확인"));
        return List.copyOf(result);
    }

    private void validatePrices(List<MarketPrice> prices) {
        boolean invalid = prices.stream().anyMatch(price ->
                price.getOpenPrice().signum() <= 0
                        || price.getHighPrice().signum() <= 0
                        || price.getLowPrice().signum() <= 0
                        || price.getClosePrice().signum() <= 0
                        || price.getVolume().signum() < 0
                        || price.getHighPrice().compareTo(price.getOpenPrice().max(price.getClosePrice())) < 0
                        || price.getLowPrice().compareTo(price.getOpenPrice().min(price.getClosePrice())) > 0
                        || price.getHighPrice().compareTo(price.getLowPrice()) < 0);
        if (invalid) {
            throw new TechnicalExplanationException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "TECHNICAL_SNAPSHOT_INVALID",
                    "유효하지 않은 OHLCV가 포함되어 기술지표 해설을 생성할 수 없습니다.");
        }
    }

    private Map<String, String> values(Object... pairs) {
        Map<String, String> values = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            values.put((String) pairs[index], number((BigDecimal) pairs[index + 1]));
        }
        return values;
    }

    private Map<String, String> stringValues(String... pairs) {
        Map<String, String> values = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            values.put(pairs[index], pairs[index + 1]);
        }
        return values;
    }

    private String freshness(MarketPrice latest) {
        if ("DEMO".equalsIgnoreCase(latest.getSource())) return "DEMO";
        return Duration.between(latest.getRecordedAt(), Instant.now()).abs().compareTo(Duration.ofDays(7)) > 0
                ? "STALE"
                : "FRESH";
    }

    private String sha256(TechnicalExplanationInput input) {
        try {
            byte[] canonical = objectMapper.writeValueAsString(input).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (Exception exception) {
            throw new IllegalStateException("기술지표 입력 hash를 계산할 수 없습니다.", exception);
        }
    }

    private String number(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    public record SnapshotBundle(Stock stock, TechnicalExplanationInput input, String inputHash) {
    }
}
