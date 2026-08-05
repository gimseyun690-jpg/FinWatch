package com.finwatch.data.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class YahooFinanceMarketDataClientTest {

    @Test
    void parseQuoteExtractsPreMarketPriceAndComputesChange() {
        Map<String, Object> response = Map.of(
                "chart", Map.of(
                        "result", List.of(
                                Map.of(
                                        "meta", Map.of(
                                                "symbol", "AAPL",
                                                "regularMarketPrice", 212.50,
                                                "previousClose", 210.00,
                                                "preMarketPrice", 215.30,
                                                "regularMarketVolume", 48120000)))));

        var quote = YahooFinanceMarketDataClient.parseQuote("AAPL", response);

        assertThat(quote.symbol()).isEqualTo("AAPL");
        assertThat(quote.providerId()).isEqualTo("YAHOO");
        assertThat(quote.currency()).isEqualTo("USD");
        assertThat(quote.price()).isNotNull();
    }
}
