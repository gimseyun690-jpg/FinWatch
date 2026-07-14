package com.finwatch.stock.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.finwatch.realtime.LiveQuote;
import com.finwatch.realtime.RealtimeQuoteHub;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class IntradayCandleApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RealtimeQuoteHub quoteHub;

    @Test
    void returnsSessionOneMinuteCandlesBuiltFromWebsocketTicks() throws Exception {
        quoteHub.publish(quote("85000", "1000", "2099-01-01T01:00:01Z"));
        quoteHub.publish(quote("85100", "1010", "2099-01-01T01:00:20Z"));

        mockMvc.perform(get("/api/v1/stocks/005930/intraday")
                        .with(jwt())
                        .queryParam("limit", "390"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.interval").value("1m"))
                .andExpect(jsonPath("$.data.period").value("SESSION"))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].open").value(85000))
                .andExpect(jsonPath("$.data.items[0].high").value(85100))
                .andExpect(jsonPath("$.data.items[0].close").value(85100))
                .andExpect(jsonPath("$.data.items[0].volume").value(10));
    }

    @Test
    void rejectsInvalidIntradayLimit() throws Exception {
        mockMvc.perform(get("/api/v1/stocks/005930/intraday")
                        .with(jwt())
                        .queryParam("limit", "601"))
                .andExpect(status().isBadRequest());
    }

    private LiveQuote quote(String price, String volume, String asOf) {
        return new LiveQuote(
                "005930",
                new BigDecimal(price),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal(volume),
                "KRW",
                Instant.parse(asOf),
                "KIS_WS",
                "LIVE");
    }
}
