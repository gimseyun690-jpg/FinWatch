package com.finwatch.data.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class FinnhubInstrumentCatalogProviderTest {

    @Test
    void normalizesSupportedUsSymbolsAndDropsUnsupportedOrDuplicateRows() {
        var instruments = FinnhubInstrumentCatalogProvider.normalize(List.of(
                row("AAPL", "XNAS", "Apple Inc", "Common Stock", "BBG000B9XRY4", "USD"),
                row("AAPL", "XNAS", "Apple duplicate", "Common Stock", "duplicate", "USD"),
                row("SPY", "XNYS", "SPDR S&P 500 ETF", "ETP", "BBG000BDTBL9", "USD"),
                row("TESTW", "XNAS", "Test warrant", "Warrant", "", "USD"),
                row("7203", "XTKS", "Toyota", "Common Stock", "", "JPY")));

        assertThat(instruments).hasSize(2);
        assertThat(instruments.get(0))
                .extracting("market", "symbol", "name", "instrumentType", "providerInstrumentId")
                .containsExactly("NASDAQ", "AAPL", "Apple Inc", "STOCK", "BBG000B9XRY4");
        assertThat(instruments.get(1))
                .extracting("market", "symbol", "instrumentType")
                .containsExactly("NYSE", "SPY", "ETF");
    }

    private Map<String, Object> row(
            String symbol,
            String mic,
            String description,
            String type,
            String figi,
            String currency) {
        return Map.of(
                "symbol", symbol,
                "mic", mic,
                "description", description,
                "type", type,
                "figi", figi,
                "currency", currency);
    }
}
