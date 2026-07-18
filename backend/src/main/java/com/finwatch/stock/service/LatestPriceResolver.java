package com.finwatch.stock.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.finwatch.realtime.RealtimeQuoteHub;
import com.finwatch.stock.domain.MarketPrice;
import com.finwatch.stock.domain.Stock;
import com.finwatch.stock.repository.MarketPriceRepository;

@Component
public class LatestPriceResolver {

    private final MarketPriceRepository marketPriceRepository;
    private final RealtimeQuoteHub realtimeQuoteHub;

    public LatestPriceResolver(
            MarketPriceRepository marketPriceRepository,
            RealtimeQuoteHub realtimeQuoteHub) {
        this.marketPriceRepository = marketPriceRepository;
        this.realtimeQuoteHub = realtimeQuoteHub;
    }

    public Optional<LatestPrice> resolve(Stock stock) {
        Optional<LatestPrice> stored = marketPriceRepository.findTopByStockIdOrderByRecordedAtDesc(stock.getId())
                .map(this::fromStoredPrice);
        Optional<LatestPrice> realtime = realtimeQuoteHub.find(stock.getMarket(), stock.getSymbol())
                .map(quote -> new LatestPrice(
                        quote.price(),
                        quote.asOf(),
                        quote.source(),
                        "LIVE".equals(quote.sessionStatus())));

        if (realtime.isEmpty()) {
            return stored;
        }
        if (stored.isEmpty()) {
            return realtime;
        }
        return realtime.get().asOf().isBefore(stored.get().asOf()) ? stored : realtime;
    }

    private LatestPrice fromStoredPrice(MarketPrice price) {
        return new LatestPrice(
                price.getClosePrice(),
                price.getRecordedAt(),
                price.getSource(),
                false);
    }

    public record LatestPrice(
            BigDecimal price,
            Instant asOf,
            String source,
            boolean realtime) {
    }
}
