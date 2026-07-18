package com.finwatch.data.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

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

    @Test
    void followsFinnhubSignedCatalogRedirect() throws Exception {
        HttpServer fileServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        fileServer.createContext("/symbols.json", exchange -> {
            byte[] body = """
                    [{"symbol":"AAPL","mic":"XNAS","description":"Apple Inc","type":"Common Stock","figi":"BBG000B9XRY4","currency":"USD"}]
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        fileServer.start();

        HttpServer apiServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        apiServer.createContext("/stock/symbol", exchange -> {
            exchange.getResponseHeaders().set(
                    "Location",
                    "http://127.0.0.1:" + fileServer.getAddress().getPort() + "/symbols.json");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        apiServer.start();

        try {
            FinnhubInstrumentCatalogProvider provider = new FinnhubInstrumentCatalogProvider(
                    "fixture-key",
                    "http://127.0.0.1:" + apiServer.getAddress().getPort(),
                    Duration.ofSeconds(1),
                    Duration.ofSeconds(2));

            var snapshot = provider.fetchCatalog();

            assertThat(snapshot.instruments()).hasSize(1);
            assertThat(snapshot.instruments().getFirst().symbol()).isEqualTo("AAPL");
        } finally {
            apiServer.stop(0);
            fileServer.stop(0);
        }
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
