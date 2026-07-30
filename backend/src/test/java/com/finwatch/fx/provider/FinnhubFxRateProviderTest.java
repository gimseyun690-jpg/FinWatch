package com.finwatch.fx.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.finwatch.data.provider.ProviderException;
import com.sun.net.httpserver.HttpServer;

class FinnhubFxRateProviderTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void latestRateDoesNotDependOnForexSymbolDiscovery() throws IOException {
        AtomicInteger symbolCalls = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/forex/rates", exchange -> {
            byte[] body = "{\"base\":\"USD\",\"quote\":{\"KRW\":1382.5}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/forex/symbol", exchange -> {
            symbolCalls.incrementAndGet();
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        server.start();
        RestClient client = RestClient.builder()
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                .build();
        FinnhubFxRateProvider provider = new FinnhubFxRateProvider("key", "OANDA", client);

        var quote = provider.latest("USD", "KRW");

        assertThat(quote.rate()).isEqualByComparingTo("1382.5");
        assertThat(quote.rateType()).isEqualTo("REFERENCE");
        assertThat(quote.providerSymbol()).isEqualTo("OANDA:USD_KRW");
        assertThat(symbolCalls).hasValue(0);
    }

    @Test
    void extractsUsdKrwWithoutReversingThePair() {
        BigDecimal rate = FinnhubFxRateProvider.extractRate(
                Map.of("base", "USD", "quote", Map.of("KRW", 1382.5)), "USD", "KRW");
        assertThat(rate).isEqualByComparingTo("1382.5");
        assertThatThrownBy(() -> FinnhubFxRateProvider.extractRate(
                Map.of("base", "KRW", "quote", Map.of("USD", 0.00072)), "USD", "KRW"))
                .isInstanceOf(ProviderException.class);
    }

    @Test
    void normalizesOnlyValidOhlcBars() {
        Map<String, Object> response = Map.of(
                "s", "ok",
                "o", List.of(1380, 1400),
                "h", List.of(1385, 1390),
                "l", List.of(1378, 1410),
                "c", List.of(1382.5, 1405),
                "t", List.of(1784018400L, 1784104800L));
        assertThat(FinnhubFxRateProvider.normalizeBars(response, "OANDA:USD_KRW"))
                .singleElement().satisfies(bar -> {
                    assertThat(bar.close()).isEqualByComparingTo("1382.5");
                    assertThat(bar.providerSymbol()).isEqualTo("OANDA:USD_KRW");
                });
    }
}
