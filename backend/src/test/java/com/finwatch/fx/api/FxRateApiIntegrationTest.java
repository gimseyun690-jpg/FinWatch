package com.finwatch.fx.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class FxRateApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsDemoLatestHistoryAndPairMetadata() throws Exception {
        mockMvc.perform(get("/api/v1/market/fx-rates/USD/KRW").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rate").value(1382.5))
                .andExpect(jsonPath("$.data.previousClose").value(1380.6))
                .andExpect(jsonPath("$.data.rateType").value("DEMO"))
                .andExpect(jsonPath("$.data.source").value("DEMO"))
                .andExpect(jsonPath("$.data.freshness").value("FRESH"));

        mockMvc.perform(get("/api/v1/market/fx-rates/USD/KRW/history?period=1M&interval=1D").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(3));

        mockMvc.perform(get("/api/v1/market/fx-rates/pairs").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].baseCurrency").value("USD"));
    }

    @Test
    void rejectsInvalidAndUnsupportedPairs() throws Exception {
        mockMvc.perform(get("/api/v1/market/fx-rates/USD/USD").with(jwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FX_PAIR_INVALID"));
        mockMvc.perform(get("/api/v1/market/fx-rates/EUR/KRW").with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FX_PAIR_NOT_SUPPORTED"));
    }
}
