package com.finwatch.portfolio.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import com.finwatch.realtime.LiveQuote;
import com.finwatch.realtime.RealtimeQuoteHub;
import com.finwatch.user.repository.AppUserRepository;

import java.math.BigDecimal;
import java.time.Instant;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PortfolioApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private RealtimeQuoteHub realtimeQuoteHub;

    @Test
    void adminPortfolioSeparatesKrwAndUsdValuations() throws Exception {
        Long userId = userRepository.findByEmailIgnoreCase("admin@finwatch.local").orElseThrow().getId();
        realtimeQuoteHub.publish(new LiveQuote(
                "000660",
                new BigDecimal("2100000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ONE,
                "KRW",
                Instant.parse("2099-07-14T01:00:00Z"),
                "KIS_WS",
                "LIVE"));

        mockMvc.perform(get("/api/v1/portfolios").with(jwt().jwt(token -> token.claim("userId", userId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currencySummaries.length()").value(2))
                .andExpect(jsonPath("$.data.holdings.length()").value(2))
                .andExpect(jsonPath("$.data.baseCurrency").value("KRW"))
                .andExpect(jsonPath("$.data.conversionComplete").value(true))
                .andExpect(jsonPath("$.data.profitLossComplete").value(true))
                .andExpect(jsonPath("$.data.fxRates[0].pair").value("USD/KRW"))
                .andExpect(jsonPath("$.data.holdings[0].valuationStatus").value("VALUED"))
                .andExpect(jsonPath("$.data.holdings[?(@.symbol == '000660')].latestPrice").value(2100000))
                .andExpect(jsonPath("$.data.holdings[?(@.symbol == '000660')].priceSource").value("KIS_WS"));
    }

    @Test
    void createAndDeleteHoldingUsesCurrentUserScope() throws Exception {
        Long userId = userRepository.findByEmailIgnoreCase("admin@finwatch.local").orElseThrow().getId();
        String request = "{\"symbol\":\"AAPL\",\"quantity\":2,\"averagePurchasePrice\":280,\"currency\":\"USD\"}";

        String response = mockMvc.perform(post("/api/v1/portfolios/holdings")
                        .with(jwt().jwt(token -> token.claim("userId", userId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.returnRate").isNumber())
                .andReturn().getResponse().getContentAsString();

        long holdingId = new tools.jackson.databind.ObjectMapper().readTree(response)
                .get("data").get("id").asLong();
        mockMvc.perform(delete("/api/v1/portfolios/holdings/{id}", holdingId)
                        .with(jwt().jwt(token -> token.claim("userId", userId))))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsDuplicateAndCurrencyMismatch() throws Exception {
        Long userId = userRepository.findByEmailIgnoreCase("admin@finwatch.local").orElseThrow().getId();

        mockMvc.perform(post("/api/v1/portfolios/holdings")
                        .with(jwt().jwt(token -> token.claim("userId", userId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"000660\",\"quantity\":1,\"averagePurchasePrice\":1,\"currency\":\"KRW\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HOLDING_DUPLICATED"));

        mockMvc.perform(post("/api/v1/portfolios/holdings")
                        .with(jwt().jwt(token -> token.claim("userId", userId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"AAPL\",\"quantity\":1,\"averagePurchasePrice\":1,\"currency\":\"KRW\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("HOLDING_CURRENCY_MISMATCH"));
    }
}
