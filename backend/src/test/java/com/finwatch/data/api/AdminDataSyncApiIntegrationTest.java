package com.finwatch.data.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class AdminDataSyncApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void adminCanRunDemoSyncWithoutCallingExternalProviders() throws Exception {
        mockMvc.perform(post("/api/v1/admin/data/stocks/005930/sync")
                        .with(jwt().authorities(() -> "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("DEMO"))
                .andExpect(jsonPath("$.data.pricesImported").value(0))
                .andExpect(jsonPath("$.data.newsImported").value(0))
                .andExpect(jsonPath("$.data.stocks[0].marketPrices.status").value("SKIPPED"));
    }

    @Test
    void regularUserCannotRunSync() throws Exception {
        mockMvc.perform(post("/api/v1/admin/data/sync").with(jwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void returnsStructuredNotFoundErrorForUnknownStock() throws Exception {
        mockMvc.perform(post("/api/v1/admin/data/stocks/UNKNOWN/sync")
                        .with(jwt().authorities(() -> "ROLE_ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STOCK_NOT_FOUND"));
    }
}
