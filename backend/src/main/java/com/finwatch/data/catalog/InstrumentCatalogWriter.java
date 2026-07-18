package com.finwatch.data.catalog;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.finwatch.data.catalog.InstrumentCatalogResponses.CatalogSnapshot;
import com.finwatch.data.catalog.InstrumentCatalogResponses.CatalogWriteResult;
import com.finwatch.data.catalog.InstrumentCatalogResponses.ProviderInstrument;
import com.finwatch.stock.domain.Stock;
import com.finwatch.stock.repository.StockRepository;

@Service
public class InstrumentCatalogWriter {

    private final StockRepository stockRepository;

    public InstrumentCatalogWriter(StockRepository stockRepository) {
        this.stockRepository = stockRepository;
    }

    @Transactional
    public CatalogWriteResult replaceSnapshot(CatalogSnapshot snapshot) {
        List<Stock> existingProviderStocks = stockRepository.findAllByProvider(snapshot.provider());
        Map<String, Stock> byProviderInstrumentId = new HashMap<>();
        for (Stock stock : existingProviderStocks) {
            byProviderInstrumentId.put(stock.getProviderInstrumentId(), stock);
        }

        Set<String> markets = snapshot.instruments().stream()
                .map(ProviderInstrument::market)
                .collect(java.util.stream.Collectors.toSet());
        Map<String, Stock> byCanonicalKey = new HashMap<>();
        for (Stock stock : stockRepository.findAllByMarketIn(markets)) {
            byCanonicalKey.put(key(stock.getMarket(), stock.getSymbol()), stock);
        }

        Instant now = snapshot.fetchedAt() == null ? Instant.now() : snapshot.fetchedAt();
        Set<Stock> matched = new HashSet<>();
        int inserted = 0;
        int updated = 0;
        for (ProviderInstrument instrument : snapshot.instruments()) {
            Stock stock = byProviderInstrumentId.get(instrument.providerInstrumentId());
            if (stock == null) {
                stock = byCanonicalKey.get(key(instrument.market(), instrument.symbol()));
            }
            if (stock == null) {
                stock = Stock.fromCatalog(snapshot.provider(), instrument, now);
                inserted++;
            } else {
                stock.applyCatalog(snapshot.provider(), instrument, now);
                updated++;
                matched.add(stock);
            }
            stockRepository.save(stock);
        }

        int deactivated = 0;
        for (Stock stock : existingProviderStocks) {
            if (!matched.contains(stock) && stock.isActive()) {
                stock.deactivateFromCatalog(now);
                deactivated++;
            }
        }
        return new CatalogWriteResult(snapshot.instruments().size(), inserted, updated, deactivated);
    }

    private String key(String market, String symbol) {
        return market.toUpperCase(Locale.ROOT) + ":" + symbol.toUpperCase(Locale.ROOT);
    }
}
