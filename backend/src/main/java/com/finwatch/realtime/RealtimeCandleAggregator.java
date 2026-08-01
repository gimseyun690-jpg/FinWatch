package com.finwatch.realtime;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.springframework.stereotype.Component;

@Component
public class RealtimeCandleAggregator {

    // A full US extended session spans 04:00-20:00 ET (960 one-minute bars).
    private static final int MAX_CANDLES_PER_SYMBOL = 1_000;

    private final ConcurrentHashMap<String, ConcurrentSkipListMap<Instant, IntradayCandle>> candles =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, BigDecimal> lastCumulativeVolumes = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Consumer<IntradayCandle>> listeners = new CopyOnWriteArrayList<>();

    public RealtimeCandleAggregator(RealtimeQuoteHub quoteHub) {
        quoteHub.addListener(this::accept);
    }

    public List<IntradayCandle> find(String market, String symbol, int limit) {
        var symbolCandles = candles.get(canonical(market, symbol));
        if (symbolCandles == null || symbolCandles.isEmpty()) {
            return List.of();
        }
        return symbolCandles.descendingMap().values().stream()
                .limit(Math.max(1, limit))
                .sorted(Comparator.comparing(IntradayCandle::time))
                .toList();
    }

    /**
     * Legacy symbol-only lookup. Ambiguous symbols intentionally produce no candles.
     */
    public List<IntradayCandle> find(String symbol, int limit) {
        String normalizedSymbol = normalize(symbol);
        List<String> matches = candles.keySet().stream()
                .filter(key -> key.endsWith(":" + normalizedSymbol))
                .limit(2)
                .toList();
        return matches.size() == 1
                ? find(matches.getFirst().substring(0, matches.getFirst().indexOf(':')), normalizedSymbol, limit)
                : List.of();
    }

    public IntradayCandleSnapshot snapshot() {
        List<IntradayCandle> items = candles.values().stream()
                .flatMap(symbolCandles -> symbolCandles.values().stream())
                .sorted(Comparator.comparing(IntradayCandle::market)
                        .thenComparing(IntradayCandle::symbol)
                        .thenComparing(IntradayCandle::time))
                .toList();
        return new IntradayCandleSnapshot(items);
    }

    public void addListener(Consumer<IntradayCandle> listener) {
        listeners.add(listener);
    }

    void accept(RealtimeEvent event) {
        if (!"quote".equals(event.type()) || !(event.data() instanceof LiveQuote quote)) {
            return;
        }
        String source = quote.source();
        if (!MarketSessionStatus.isStreaming(quote.sessionStatus()) || source == null || !source.endsWith("_WS")) {
            return;
        }

        Instant minute = quote.asOf().truncatedTo(ChronoUnit.MINUTES);
        BigDecimal tickVolume = tickVolume(quote);
        String instrumentKey = quote.canonicalKey();
        var symbolCandles = candles.computeIfAbsent(instrumentKey, ignored -> new ConcurrentSkipListMap<>());
        IntradayCandle updated = symbolCandles.compute(minute, (ignored, current) -> merge(current, quote, minute, tickVolume));
        trim(symbolCandles);
        notifyListeners(updated);
    }

    private BigDecimal tickVolume(LiveQuote quote) {
        BigDecimal volume = positiveOrZero(quote.volume());
        if (!quote.source().startsWith("KIS_")) {
            return volume;
        }

        BigDecimal[] delta = {BigDecimal.ZERO};
        lastCumulativeVolumes.compute(quote.canonicalKey(), (ignored, previous) -> {
            if (previous != null && volume.compareTo(previous) >= 0) {
                delta[0] = volume.subtract(previous);
            }
            return volume;
        });
        return delta[0];
    }

    private IntradayCandle merge(
            IntradayCandle current,
            LiveQuote quote,
            Instant minute,
            BigDecimal tickVolume) {
        if (current == null) {
            return new IntradayCandle(
                    quote.market(),
                    quote.symbol(),
                    minute,
                    quote.price(),
                    quote.price(),
                    quote.price(),
                    quote.price(),
                    tickVolume,
                    quote.currency(),
                    quote.source(),
                    quote.sessionStatus());
        }
        return new IntradayCandle(
                current.market(),
                current.symbol(),
                current.time(),
                current.open(),
                current.high().max(quote.price()),
                current.low().min(quote.price()),
                quote.price(),
                current.volume().add(tickVolume),
                current.currency(),
                quote.source(),
                quote.sessionStatus());
    }

    private void trim(ConcurrentSkipListMap<Instant, IntradayCandle> symbolCandles) {
        while (symbolCandles.size() > MAX_CANDLES_PER_SYMBOL) {
            symbolCandles.pollFirstEntry();
        }
    }

    private BigDecimal positiveOrZero(BigDecimal value) {
        return value == null || value.signum() < 0 ? BigDecimal.ZERO : value;
    }

    private void notifyListeners(IntradayCandle candle) {
        for (Consumer<IntradayCandle> listener : listeners) {
            try {
                listener.accept(candle);
            } catch (RuntimeException ignored) {
                // Chart delivery must not interrupt market data ingestion.
            }
        }
    }

    private String canonical(String market, String symbol) {
        String normalizedMarket = normalize(market);
        return (normalizedMarket.isBlank() ? "UNKNOWN" : normalizedMarket) + ":" + normalize(symbol);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
