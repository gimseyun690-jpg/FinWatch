package com.finwatch.ai.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.finwatch.ai.domain.AiAnalysis;
import com.finwatch.ai.dto.DailyChangeBriefingInput;
import com.finwatch.ai.dto.DailyChangeBriefingInput.BriefingEvidence;
import com.finwatch.ai.dto.DailyChangeBriefingInput.BriefingViewpoint;
import com.finwatch.ai.repository.AiAnalysisRepository;
import com.finwatch.news.domain.NewsArticle;
import com.finwatch.news.repository.NewsArticleRepository;
import com.finwatch.stock.domain.MarketPrice;
import com.finwatch.stock.domain.Stock;
import com.finwatch.stock.repository.MarketPriceRepository;
import com.finwatch.stock.repository.StockRepository;
import com.finwatch.technical.TechnicalAnalysisCalculator;
import com.finwatch.technical.TechnicalAnalysisCalculator.Candle;
import com.finwatch.technical.TechnicalAnalysisCalculator.Result;

import tools.jackson.databind.ObjectMapper;

@Component
public class DailyBriefingSnapshotFactory {
    static final String INPUT_VERSION = "daily-briefing-input-v1";
    static final String CALCULATION_VERSION = "technical-v2-wilder";

    private final StockRepository stocks;
    private final MarketPriceRepository prices;
    private final NewsArticleRepository news;
    private final AiAnalysisRepository analyses;
    private final TechnicalAnalysisCalculator calculator;
    private final ObjectMapper objectMapper;

    public DailyBriefingSnapshotFactory(StockRepository stocks, MarketPriceRepository prices,
            NewsArticleRepository news, AiAnalysisRepository analyses,
            TechnicalAnalysisCalculator calculator, ObjectMapper objectMapper) {
        this.stocks = stocks; this.prices = prices; this.news = news; this.analyses = analyses;
        this.calculator = calculator; this.objectMapper = objectMapper;
    }

    public SnapshotBundle create(String requestedMarket, String requestedSymbol) {
        String symbol = requestedSymbol.trim().toUpperCase(Locale.ROOT);
        String market = requestedMarket == null || requestedMarket.isBlank() ? null : requestedMarket.trim().toUpperCase(Locale.ROOT);
        Stock stock = findStock(market, symbol);
        List<MarketPrice> series = prices.findAllByStockIdAndIntervalOrderByRecordedAtAsc(stock.getId(), "1D");
        if (series.size() < 61) throw new DailyBriefingException(HttpStatus.UNPROCESSABLE_ENTITY,
                "BRIEFING_BASELINE_UNAVAILABLE", "전일 변화 브리핑에는 완성 일봉이 최소 61개 필요합니다.");
        validate(series);
        MarketPrice currentPrice = series.getLast();
        MarketPrice previousPrice = series.get(series.size() - 2);
        if (currentPrice.getRecordedAt().isAfter(Instant.now().plus(Duration.ofMinutes(5)))) {
            throw new DailyBriefingException(HttpStatus.UNPROCESSABLE_ENTITY, "BRIEFING_INPUT_INVALID", "미래 시각의 일봉은 브리핑에 사용할 수 없습니다.");
        }

        Result current = calculator.calculateMarket(candles(series));
        Result previous = calculator.calculateMarket(candles(series.subList(0, series.size() - 1)));
        BigDecimal priceChange = currentPrice.getClosePrice().subtract(previousPrice.getClosePrice());
        BigDecimal priceChangeRate = percent(priceChange, previousPrice.getClosePrice());
        List<BriefingEvidence> evidence = new ArrayList<>();
        evidence.add(technical("T1", "PRICE_CHANGE", currentPrice.getClosePrice(), previousPrice.getClosePrice(), priceChange,
                "종가 " + number(previousPrice.getClosePrice()) + " → " + number(currentPrice.getClosePrice()) + " (" + signed(priceChangeRate) + "%)",
                currentPrice, "PRICE"));
        evidence.add(technical("T2", "MOVING_AVERAGE_CHANGE", current.ma20(), previous.ma20(), current.ma20().subtract(previous.ma20()),
                "MA20 " + number(previous.ma20()) + " → " + number(current.ma20()) + " · 현재 " + current.movingAverageSignal(), currentPrice, "MA"));
        evidence.add(technical("T3", "RSI_CHANGE", current.rsi(), previous.rsi(), current.rsi().subtract(previous.rsi()),
                "RSI14 " + number(previous.rsi()) + " → " + number(current.rsi()), currentPrice, "RSI"));
        evidence.add(technical("T4", "MACD_CHANGE", current.macd().histogram(), previous.macd().histogram(), current.macd().histogram().subtract(previous.macd().histogram()),
                "MACD histogram " + number(previous.macd().histogram()) + " → " + number(current.macd().histogram()), currentPrice, "MACD"));
        evidence.add(technical("T5", "VOLATILITY_CHANGE", current.atrPercent(), previous.atrPercent(), current.atrPercent().subtract(previous.atrPercent()),
                "ATR 비율 " + number(previous.atrPercent()) + "% → " + number(current.atrPercent()) + "%", currentPrice, "ATR"));
        BigDecimal currentVolumeRatio = ratio(currentPrice.getVolume(), current.volumeMa20());
        BigDecimal previousVolumeRatio = ratio(previousPrice.getVolume(), previous.volumeMa20());
        evidence.add(technical("T6", "VOLUME_CHANGE", currentVolumeRatio, previousVolumeRatio, currentVolumeRatio.subtract(previousVolumeRatio),
                "거래량/20일 평균 " + number(previousVolumeRatio) + "배 → " + number(currentVolumeRatio) + "배", currentPrice, "VOLUME"));

        ContentResult contents = contentEvidence(stock, previousPrice.getRecordedAt(), Instant.now(), evidence);
        String freshness = freshness(currentPrice);
        evidence.add(new BriefingEvidence("Q1", "QUALITY", "DATA_QUALITY", null, null, null,
                "가격 출처 " + currentPrice.getSource() + " · " + freshness + " · 분석 일봉 " + series.size() + "개",
                Map.of("type", "DATA_QUALITY", "source", currentPrice.getSource(), "time", currentPrice.getRecordedAt().toString())));

        List<BriefingViewpoint> viewpoints = viewpoints(current, previous, priceChange, currentVolumeRatio, contents, evidence);
        String relation = relation(viewpoints);
        List<String> limitations = new ArrayList<>();
        if ("DEMO".equals(freshness)) limitations.add("DEMO 데이터이므로 실제 투자 판단에 사용할 수 없습니다.");
        if ("STALE".equals(freshness)) limitations.add("오래된 일봉이 포함되어 최신 시장 상태와 다를 수 있습니다.");
        if (contents.excludedCount() > 0) limitations.add("분석 권한 또는 검증된 요약이 없는 신규 콘텐츠 " + contents.excludedCount() + "건을 의미 근거에서 제외했습니다.");
        if (contents.newsCount() == 0) limitations.add("비교 구간에 의미 판단 가능한 신규 뉴스가 없습니다.");
        if (contents.disclosureCount() == 0) limitations.add("비교 구간에 의미 판단 가능한 신규 공시가 없습니다.");

        ZoneId zone = "KRX".equals(stock.getMarket()) ? ZoneId.of("Asia/Seoul") : ZoneId.of("America/New_York");
        LocalDate currentDate = currentPrice.getRecordedAt().atZone(zone).toLocalDate();
        LocalDate previousDate = previousPrice.getRecordedAt().atZone(zone).toLocalDate();
        DailyChangeBriefingInput input = new DailyChangeBriefingInput(stock.getMarket(), stock.getSymbol(), stock.getCurrency(),
                currentDate, previousDate, "AVAILABLE", currentPrice.getRecordedAt(), CALCULATION_VERSION, INPUT_VERSION,
                currentPrice.getSource(), freshness, priceChange, priceChangeRate, relation, List.copyOf(viewpoints),
                List.copyOf(evidence), contents.excludedCount(), List.copyOf(limitations));
        return new SnapshotBundle(stock, input, sha256(input));
    }

    private ContentResult contentEvidence(Stock stock, Instant fromExclusive, Instant toInclusive, List<BriefingEvidence> target) {
        List<NewsArticle> articles = news.findAllByStockMarketAndStockSymbolOrderByPublishedAtDesc(stock.getMarket(), stock.getSymbol()).stream()
                .filter(item -> item.getPublishedAt().isAfter(fromExclusive) && !item.getPublishedAt().isAfter(toInclusive))
                .sorted(Comparator.comparing(NewsArticle::getPublishedAt))
                .toList();
        int excluded = 0, newsCount = 0, disclosureCount = 0;
        Set<String> seen = new LinkedHashSet<>();
        for (NewsArticle article : articles) {
            String dedupe = article.getContentHash() == null ? article.getCanonicalUrl() : article.getContentHash();
            if (!seen.add(dedupe)) continue;
            AiAnalysis analysis = article.getContentHash() == null ? null
                    : analyses.findAllByNewsIdAndContentHash(article.getId(), article.getContentHash()).stream()
                            .max(Comparator.comparing(AiAnalysis::getGeneratedAt)).orElse(null);
            if (!article.isAiAnalysisAllowed() || analysis == null) { excluded++; continue; }
            boolean disclosure = "DISCLOSURE".equals(article.getContentKind());
            String id = (disclosure ? "D" : "N") + (disclosure ? ++disclosureCount : ++newsCount);
            target.add(new BriefingEvidence(id, disclosure ? "DISCLOSURE" : "NEWS", "CONTENT_ANALYSIS",
                    analysis.getSentiment(), null, null, article.getTitle() + " · " + analysis.getSummary(),
                    Map.of("type", disclosure ? "DISCLOSURE" : "NEWS", "targetId", article.getId().toString(),
                            "url", article.getUrl(), "source", article.getSource(), "publishedAt", article.getPublishedAt().toString())));
        }
        return new ContentResult(newsCount, disclosureCount, excluded);
    }

    private List<BriefingViewpoint> viewpoints(Result current, Result previous, BigDecimal priceChange,
            BigDecimal volumeRatio, ContentResult contents, List<BriefingEvidence> evidence) {
        List<BriefingViewpoint> rows = new ArrayList<>();
        rows.add(view("TREND", signalStatus(current.movingAverageSignal().name()), changed(current.movingAverageSignal().name(), previous.movingAverageSignal().name()), "가격과 이동평균 관계", "T1", "T2"));
        String momentum = "SELL".equals(current.macdSignal().name()) ? "CAUTION" : "BUY".equals(current.macdSignal().name()) ? "POSITIVE" : "NEUTRAL";
        rows.add(view("MOMENTUM", momentum, changed(current.macdSignal().name(), previous.macdSignal().name()), "RSI와 MACD 변화", "T3", "T4"));
        rows.add(view("OVERHEAT", current.rsi().compareTo(BigDecimal.valueOf(70)) >= 0 ? "CAUTION" : "NEUTRAL",
                boundaryChange(current.rsi(), previous.rsi(), BigDecimal.valueOf(70)), "RSI 과열 경계", "T3"));
        rows.add(view("VOLATILITY", current.atrPercent().compareTo(previous.atrPercent()) > 0 ? "CAUTION" : "NEUTRAL",
                deltaChange(current.atrPercent().subtract(previous.atrPercent())), "ATR 변동성 변화", "T5"));
        String volume = volumeRatio.compareTo(BigDecimal.ONE) >= 0 ? "CONFIRMING" : priceChange.signum() == 0 ? "NEUTRAL" : "DIVERGING";
        rows.add(view("VOLUME", volume, deltaChange(volumeRatio.subtract(BigDecimal.ONE)), "거래량 확인 여부", "T1", "T6"));
        rows.add(contentView("NEWS", "N", contents.newsCount(), evidence));
        rows.add(contentView("DISCLOSURE", "D", contents.disclosureCount(), evidence));
        return List.copyOf(rows);
    }

    private BriefingViewpoint contentView(String viewpoint, String prefix, int count, List<BriefingEvidence> evidence) {
        List<BriefingEvidence> items = evidence.stream().filter(item -> item.id().startsWith(prefix)).toList();
        if (count == 0) return new BriefingViewpoint(viewpoint, "INSUFFICIENT", "INSUFFICIENT", "검증된 신규 근거 없음", List.of());
        boolean positive = items.stream().anyMatch(item -> "POSITIVE".equals(item.currentValue()));
        boolean negative = items.stream().anyMatch(item -> "NEGATIVE".equals(item.currentValue()));
        String status = positive && negative ? "MIXED" : positive ? "POSITIVE" : negative ? "CAUTION" : "NEUTRAL";
        return new BriefingViewpoint(viewpoint, status, "NEW", "신규 " + ("NEWS".equals(viewpoint) ? "뉴스" : "공시") + " 분석 " + count + "건",
                items.stream().map(BriefingEvidence::id).toList());
    }

    private String relation(List<BriefingViewpoint> viewpoints) {
        boolean positive = viewpoints.stream().anyMatch(item -> Set.of("POSITIVE", "CONFIRMING").contains(item.status()));
        boolean caution = viewpoints.stream().anyMatch(item -> Set.of("CAUTION", "DIVERGING").contains(item.status()));
        long usable = viewpoints.stream().filter(item -> !"INSUFFICIENT".equals(item.status())).count();
        if (usable < 2) return "INSUFFICIENT";
        if (positive && caution) return "CONFLICTING";
        if (positive || caution) return usable >= 3 ? "ALIGNED" : "PARTIAL";
        return "PARTIAL";
    }

    private BriefingViewpoint view(String name, String status, String change, String headline, String... ids) {
        return new BriefingViewpoint(name, status, change, headline, List.of(ids));
    }
    private String signalStatus(String signal) { return "BUY".equals(signal) ? "POSITIVE" : "SELL".equals(signal) ? "CAUTION" : "NEUTRAL"; }
    private String changed(String current, String previous) { return current.equals(previous) ? "UNCHANGED" : "REVERSED"; }
    private String boundaryChange(BigDecimal current, BigDecimal previous, BigDecimal boundary) { return current.compareTo(boundary) >= 0 && previous.compareTo(boundary) < 0 ? "NEW" : current.compareTo(previous) > 0 ? "STRENGTHENED" : current.compareTo(previous) < 0 ? "WEAKENED" : "UNCHANGED"; }
    private String deltaChange(BigDecimal delta) { return delta.signum() > 0 ? "STRENGTHENED" : delta.signum() < 0 ? "WEAKENED" : "UNCHANGED"; }

    private BriefingEvidence technical(String id, String kind, BigDecimal current, BigDecimal previous, BigDecimal delta,
            String display, MarketPrice price, String target) {
        return new BriefingEvidence(id, "TECHNICAL", kind, number(current), number(previous), number(delta), display,
                Map.of("type", "CHART_INDICATOR", "target", target, "time", price.getRecordedAt().toString()));
    }
    private List<Candle> candles(List<MarketPrice> values) { return values.stream().map(value -> new Candle(value.getOpenPrice(), value.getHighPrice(), value.getLowPrice(), value.getClosePrice(), value.getVolume())).toList(); }
    private BigDecimal ratio(BigDecimal value, BigDecimal base) { return base.signum() == 0 ? BigDecimal.ZERO : value.divide(base, 4, RoundingMode.HALF_UP); }
    private BigDecimal percent(BigDecimal value, BigDecimal base) { return base.signum() == 0 ? BigDecimal.ZERO : value.multiply(BigDecimal.valueOf(100)).divide(base, 4, RoundingMode.HALF_UP); }
    private String signed(BigDecimal value) { return (value.signum() > 0 ? "+" : "") + number(value); }
    private String number(BigDecimal value) { return value.setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString(); }
    private String freshness(MarketPrice latest) { if ("DEMO".equalsIgnoreCase(latest.getSource())) return "DEMO"; return Duration.between(latest.getRecordedAt(), Instant.now()).abs().compareTo(Duration.ofDays(7)) > 0 ? "STALE" : "FRESH"; }
    private void validate(List<MarketPrice> values) { if (values.stream().anyMatch(value -> value.getOpenPrice().signum() <= 0 || value.getClosePrice().signum() <= 0 || value.getHighPrice().compareTo(value.getOpenPrice().max(value.getClosePrice())) < 0 || value.getLowPrice().compareTo(value.getOpenPrice().min(value.getClosePrice())) > 0)) throw new DailyBriefingException(HttpStatus.UNPROCESSABLE_ENTITY, "BRIEFING_INPUT_INVALID", "유효하지 않은 OHLCV가 포함되어 있습니다."); }
    private Stock findStock(String market, String symbol) { if (market != null) return stocks.findByMarketAndSymbolAndActiveTrue(market, symbol).orElseThrow(() -> notFound()); List<Stock> found = stocks.findAllBySymbolIgnoreCaseAndActiveTrueOrderByMarketAsc(symbol); if (found.isEmpty()) throw notFound(); if (found.size() > 1) throw new DailyBriefingException(HttpStatus.CONFLICT, "STOCK_SYMBOL_AMBIGUOUS", "같은 symbol이 여러 시장에 있습니다. market을 지정해 주세요."); return found.getFirst(); }
    private DailyBriefingException notFound() { return new DailyBriefingException(HttpStatus.NOT_FOUND, "STOCK_NOT_FOUND", "종목을 찾을 수 없습니다."); }
    private String sha256(Object input) { try { byte[] value = objectMapper.writeValueAsString(input).getBytes(StandardCharsets.UTF_8); return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); } catch (Exception exception) { throw new IllegalStateException("브리핑 input hash를 계산할 수 없습니다.", exception); } }
    private record ContentResult(int newsCount, int disclosureCount, int excludedCount) { }
    public record SnapshotBundle(Stock stock, DailyChangeBriefingInput input, String inputHash) { }
}
