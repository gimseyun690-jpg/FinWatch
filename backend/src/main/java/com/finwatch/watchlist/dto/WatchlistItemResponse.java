package com.finwatch.watchlist.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.finwatch.stock.dto.StockResponses.StockSummary;
import com.finwatch.watchlist.domain.Watchlist;

public record WatchlistItemResponse(
        Long id,
        String symbol,
        String name,
        String market,
        String currency,
        BigDecimal price,
        BigDecimal change,
        BigDecimal changeRate,
        Instant asOf,
        String source,
        Instant addedAt) {

    public static WatchlistItemResponse from(Watchlist watchlist, StockSummary stock) {
        return new WatchlistItemResponse(
                watchlist.getId(),
                stock.symbol(),
                stock.name(),
                stock.market(),
                stock.currency(),
                stock.price(),
                stock.change(),
                stock.changeRate(),
                stock.asOf(),
                stock.source(),
                watchlist.getCreatedAt());
    }
}
