package com.finwatch.stock.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class StockApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsOnlyActiveStocksThatHavePriceData() throws Exception {
        mockMvc.perform(get("/api/v1/stocks").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].price").isNumber());
    }

    @Test
    void returnsStockPriceHistoryAndTechnicalAnalysis() throws Exception {
        mockMvc.perform(get("/api/v1/stocks/000660").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("SK하이닉스"))
                .andExpect(jsonPath("$.data.price").value(2723000));

        mockMvc.perform(get("/api/v1/stocks/000660/prices")
                        .with(jwt())
                        .queryParam("period", "3M")
                        .queryParam("interval", "1D"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.period").value("3M"))
                .andExpect(jsonPath("$.data.source").value("DEMO"))
                .andExpect(jsonPath("$.data.items").isNotEmpty())
                .andExpect(jsonPath("$.data.items[-1].indicators.ma20").isNumber())
                .andExpect(jsonPath("$.data.items[-1].indicators.rsi").isNumber())
                .andExpect(jsonPath("$.data.items[-1].indicators.atr").isNumber());

        mockMvc.perform(get("/api/v1/stocks/000660/technical").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summarySignal").value("BUY"))
                .andExpect(jsonPath("$.data.rsi.period").value(14))
                .andExpect(jsonPath("$.data.bollingerBands.period").value(20))
                .andExpect(jsonPath("$.data.atr.period").value(14))
                .andExpect(jsonPath("$.data.volumeMa20").isNumber())
                .andExpect(jsonPath("$.data.events").isArray())
                .andExpect(jsonPath("$.data.calculationVersion").value("technical-v2-wilder"))
                .andExpect(jsonPath("$.data.rsi.method").value("WILDER"));
    }

    @Test
    void allWatchlistStocksHaveEnoughHistoryForTechnicalAnalysis() throws Exception {
        for (String symbol : new String[] { "005930", "000660", "NVDA", "AAPL" }) {
            mockMvc.perform(get("/api/v1/stocks/{symbol}/technical", symbol).with(jwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.symbol").value(symbol));
        }
    }

    @Test
    void supportsChartPeriodsAndRejectsUnsupportedInterval() throws Exception {
        for (String period : new String[] { "1M", "3M", "6M", "1Y", "ALL" }) {
            mockMvc.perform(get("/api/v1/stocks/000660/prices")
                            .with(jwt())
                            .queryParam("period", period)
                            .queryParam("interval", "1D"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.period").value(period))
                    .andExpect(jsonPath("$.data.items").isNotEmpty());
        }

        mockMvc.perform(get("/api/v1/stocks/000660/prices")
                        .with(jwt())
                        .queryParam("period", "ALL")
                        .queryParam("interval", "1W"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.interval").value("1W"))
                .andExpect(jsonPath("$.data.items").isNotEmpty())
                .andExpect(jsonPath("$.data.items[-1].indicators.ma5").isNumber());

        mockMvc.perform(get("/api/v1/stocks/000660/prices")
                        .with(jwt())
                        .queryParam("period", "ALL")
                        .queryParam("interval", "1M"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.interval").value("1M"))
                .andExpect(jsonPath("$.data.items").isNotEmpty());

        mockMvc.perform(get("/api/v1/stocks/000660/prices")
                        .with(jwt())
                        .queryParam("period", "3M")
                        .queryParam("interval", "1H"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchesLocalCatalogWithRankingFiltersAndPagination() throws Exception {
        mockMvc.perform(get("/api/v1/stocks/search")
                        .with(jwt())
                        .queryParam("q", "삼성")
                        .queryParam("page", "0")
                        .queryParam("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].symbol").value("005930"))
                .andExpect(jsonPath("$.data.items[0].market").value("KRX"))
                .andExpect(jsonPath("$.data.items[0].dataAvailability").value("READY"))
                .andExpect(jsonPath("$.data.items[1].symbol").value("207940"))
                .andExpect(jsonPath("$.data.catalogAsOf").isNotEmpty());

        mockMvc.perform(get("/api/v1/stocks/search")
                        .with(jwt())
                        .queryParam("q", "AAPL")
                        .queryParam("market", "NASDAQ")
                        .queryParam("type", "STOCK")
                        .queryParam("page", "0")
                        .queryParam("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$.data.items[0].englishName").value("Apple Inc."))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(1))
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(get("/api/v1/stocks/search").with(jwt()).queryParam("q", "005930"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].symbol").value("005930"))
                .andExpect(jsonPath("$.data.items[0].name").value("삼성전자"));

        mockMvc.perform(get("/api/v1/stocks/search").with(jwt()).queryParam("q", "Apple"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$.data.items[0].market").value("NASDAQ"));

        mockMvc.perform(get("/api/v1/stocks/search")
                        .with(jwt())
                        .queryParam("q", "Apple/../../"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("STOCK_SEARCH_QUERY_INVALID"));
    }

    @Test
    void canonicalEndpointsDistinguishMarketAndExposeMetadataOnlyState() throws Exception {
        mockMvc.perform(get("/api/v1/stocks/KRX/005930").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stockId").isNumber())
                .andExpect(jsonPath("$.data.market").value("KRX"))
                .andExpect(jsonPath("$.data.symbol").value("005930"))
                .andExpect(jsonPath("$.data.dataAvailability").value("READY"))
                .andExpect(jsonPath("$.data.price").isNumber());

        mockMvc.perform(get("/api/v1/stocks/KRX/005930/prices")
                        .with(jwt())
                        .queryParam("period", "3M"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.symbol").value("005930"))
                .andExpect(jsonPath("$.data.items").isNotEmpty());

        mockMvc.perform(get("/api/v1/stocks/NASDAQ/MSFT").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Microsoft"))
                .andExpect(jsonPath("$.data.dataAvailability").value("METADATA_ONLY"))
                .andExpect(jsonPath("$.data.price").doesNotExist());

        mockMvc.perform(get("/api/v1/stocks/TEST").with(jwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STOCK_SYMBOL_AMBIGUOUS"));

        mockMvc.perform(get("/api/v1/stocks/NYSE/TEST").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.market").value("NYSE"))
                .andExpect(jsonPath("$.data.name").value("Demo Holdings"));
    }

    @Test
    void dataLoadUsesExistingDemoDataAndRejectsMetadataOnlyFixtures() throws Exception {
        mockMvc.perform(post("/api/v1/stocks/KRX/005930/data-loads")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resources\":[\"QUOTE\",\"DAILY_PRICES\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.resources[0].status").value("READY"));

        mockMvc.perform(post("/api/v1/stocks/NASDAQ/MSFT/data-loads")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resources\":[\"QUOTE\",\"DAILY_PRICES\"]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DATA_NOT_SUPPORTED"));

        mockMvc.perform(get("/api/v1/stocks/KRX/005930/disclosures").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }
}
