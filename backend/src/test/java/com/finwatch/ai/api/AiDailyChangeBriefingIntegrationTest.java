package com.finwatch.ai.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.finwatch.ai.repository.AiDailyChangeBriefingRepository;
import com.finwatch.ai.repository.AiUsageLogRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class AiDailyChangeBriefingIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired AiDailyChangeBriefingRepository briefings;
    @Autowired AiUsageLogRepository usageLogs;

    @BeforeEach void reset() { usageLogs.deleteAll(); briefings.deleteAll(); }

    @Test
    void createsEvidenceBasedBriefingThenUsesCacheAndLatestEndpoint() throws Exception {
        String body = """
                {"market":"KRX","symbol":"000660","promptVersion":"daily-change-briefing-v2-news-fixed"}
                """;
        mockMvc.perform(post("/api/v1/ai/daily-change-briefings").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.symbol").value("000660"))
                .andExpect(jsonPath("$.data.baselineStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.evidence[0].id").value("T1"))
                .andExpect(jsonPath("$.data.viewpoints.length()").value(7))
                .andExpect(jsonPath("$.data.viewpoints[*].viewpoint", hasItem("NEWS")))
                .andExpect(jsonPath("$.data.evidence[*].id", hasItem("N1")))
                .andExpect(jsonPath("$.data.audit.briefingInputVersion").value("daily-briefing-input-v2-news-fixed"))
                .andExpect(jsonPath("$.data.audit.cacheHit").value(false))
                .andExpect(jsonPath("$.data.audit.inputTokens").isNumber())
                .andExpect(jsonPath("$.data.dataLimitations[0]").value(org.hamcrest.Matchers.containsString("DEMO")));

        mockMvc.perform(post("/api/v1/ai/daily-change-briefings").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.audit.cacheHit").value(true))
                .andExpect(jsonPath("$.data.audit.inputTokens").value(0))
                .andExpect(jsonPath("$.data.audit.estimatedCost").value(0));

        mockMvc.perform(get("/api/v1/stocks/000660/daily-change-briefings/latest?market=KRX").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.staleBriefing").value(false));

        assertThat(briefings.count()).isEqualTo(1);
        assertThat(usageLogs.findAll()).hasSize(2).allMatch(log -> "DAILY_CHANGE_BRIEFING".equals(log.getFeatureType()));
    }

    @Test
    void rejectsUnsupportedPromptVersion() throws Exception {
        mockMvc.perform(post("/api/v1/ai/daily-change-briefings").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"000660\",\"promptVersion\":\"unknown\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AI_PROMPT_VERSION_UNSUPPORTED"));
    }
}
