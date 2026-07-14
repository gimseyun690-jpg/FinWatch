package com.finwatch.realtime;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.springframework.stereotype.Component;

@Component
public class RealtimeCandleAggregator {

    private static final int MAX_CANDLES_PER_SYMBOL = 600;

    private final ConcurrentHashMap<String, ConcurrentSkipListMap<Instant, IntradayCandle>> candles =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, BigDecimal> lastCumulativeVolumes = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Consumer<IntradayCandle>> listeners = new CopyOnWriteArrayList<>();

    public RealtimeCandleAggregator(RealtimeQuoteHub quoteHub) {
        quoteHub.addListener(this::accept);
    }

    public List<IntradayCandle> find(String symbol, int limit) {
        var symbolCandles = candles.get(symbol);
        if (symbolCandles == null || symbolCandles.isEmpty()) {
            return List.of();
        }
        return symbolCandles.descendingMap().values().stream()
                .limit(Math.max(1, limit))
                .sorted(Comparator.comparing(IntradayCandle::time))
                .toList();
    }

    public IntradayCandleSnapshot snapshot() {
        List<IntradayCandle> items = candles.values().stream()
                .flatMap(symbolCandles -> symbolCandles.values().stream())
                .sorted(Comparator.comparing(IntradayCandle::symbol).thenComparing(IntradayCandle::time))
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
        if (!"LIVE".equalsIgnoreCase(quote.sessionStatus()) || source == null || !source.endsWith("_WS")) {
            return;
        }

        Instant minute = quote.asOf().truncatedTo(ChronoUnit.MINUTES);
        BigDecimal tickVolume = tickVolume(quote);
        var symbolCandles = candles.computeIfAbsent(quote.symbol(), ignored -> new ConcurrentSkipListMap<>());
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
        lastCumulativeVolumes.compute(quote.symbol(), (ignored, previous) -> {
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
                    quote.symbol(),
                    minute,
                    quote.price(),
                    quote.price(),
                    quote.price(),
                    quote.price(),
                    tickVolume,
                    quote.currency(),
                    quote.source());
        }
        return new IntradayCandle(
                current.symbol(),
                current.time(),
                current.open(),
                current.high().max(quote.price()),
                current.low().min(quote.price()),
                quote.price(),
                current.volume().add(tickVolume),
                current.currency(),
                quote.source());
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
}
