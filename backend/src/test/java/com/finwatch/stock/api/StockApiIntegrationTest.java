package com.finwatch.stock.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
                .andExpect(jsonPath("$.data.items").isNotEmpty());

        mockMvc.perform(get("/api/v1/stocks/000660/technical").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summarySignal").value("BUY"))
                .andExpect(jsonPath("$.data.rsi.period").value(14));
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
                        .queryParam("period", "3M")
                        .queryParam("interval", "1H"))
                .andExpect(status().isBadRequest());
    }
}
