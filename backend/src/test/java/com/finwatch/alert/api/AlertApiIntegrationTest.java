package com.finwatch.alert.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.finwatch.user.repository.AppUserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class AlertApiIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AppUserRepository userRepository;

    @Test
    void listEvaluatesActiveAlertsAgainstLatestPrice() throws Exception {
        Long userId = adminId();

        mockMvc.perform(get("/api/v1/alerts").with(jwt().jwt(token -> token.claim("userId", userId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.symbol == 'NVDA')].status").value("TRIGGERED"))
                .andExpect(jsonPath("$.data[?(@.symbol == '000660')].status").value("ACTIVE"));
    }

    @Test
    void createDisableAndDeleteAlert() throws Exception {
        Long userId = adminId();
        String response = mockMvc.perform(post("/api/v1/alerts")
                        .with(jwt().jwt(token -> token.claim("userId", userId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"AAPL\",\"condition\":\"BELOW\",\"targetPrice\":250,\"currency\":\"USD\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();
        long alertId = new tools.jackson.databind.ObjectMapper().readTree(response).get("data").get("id").asLong();

        mockMvc.perform(patch("/api/v1/alerts/{id}", alertId)
                        .with(jwt().jwt(token -> token.claim("userId", userId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"));

        mockMvc.perform(delete("/api/v1/alerts/{id}", alertId)
                        .with(jwt().jwt(token -> token.claim("userId", userId))))
                .andExpect(status().isOk());
    }

    @Test
    void duplicateActiveAlertIsRejected() throws Exception {
        Long userId = adminId();
        mockMvc.perform(post("/api/v1/alerts")
                        .with(jwt().jwt(token -> token.claim("userId", userId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"000660\",\"condition\":\"ABOVE\",\"targetPrice\":2800000,\"currency\":\"KRW\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALERT_DUPLICATED"));
    }

    private Long adminId() {
        return userRepository.findByEmailIgnoreCase("admin@finwatch.local").orElseThrow().getId();
    }
}
