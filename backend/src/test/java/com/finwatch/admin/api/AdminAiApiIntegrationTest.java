package com.finwatch.admin.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.finwatch.ai.repository.AiAnalysisRepository;
import com.finwatch.ai.repository.AiTechnicalExplanationRepository;
import com.finwatch.ai.repository.AiUsageLogRepository;
import com.finwatch.news.repository.NewsArticleRepository;

@SpringBootTest(properties = {
        "app.ai.provider=mock",
        "app.ai.allowed-prompt-versions=admin-metrics-v1"
})
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class AdminAiApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NewsArticleRepository newsArticleRepository;

    @Autowired
    private AiAnalysisRepository aiAnalysisRepository;

    @Autowired
    private AiUsageLogRepository aiUsageLogRepository;

    @Autowired
    private AiTechnicalExplanationRepository aiTechnicalExplanationRepository;

    @BeforeEach
    void resetUsageData() {
        aiUsageLogRepository.deleteAll();
        aiTechnicalExplanationRepository.deleteAll();
        aiAnalysisRepository.deleteAll();
    }

    @Test
    void metricsAndUsageLogsShowCacheSavings() throws Exception {
        Long newsId = newsArticleRepository.findAllByStockSymbolOrderByPublishedAtDesc("000660")
                .getFirst()
                .getId();
        String body = "{\"newsId\":" + newsId + ",\"promptVersion\":\"admin-metrics-v1\"}";

        mockMvc.perform(post("/api/v1/ai/news-summaries")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/ai/news-summaries")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/ai/metrics")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requestCount").value(2))
                .andExpect(jsonPath("$.data.modelCallCount").value(1))
                .andExpect(jsonPath("$.data.cacheHitCount").value(1))
                .andExpect(jsonPath("$.data.cacheMissCount").value(1))
                .andExpect(jsonPath("$.data.cacheHitRate").value(50.0))
                .andExpect(jsonPath("$.data.totalTokens").isNumber())
                .andExpect(jsonPath("$.data.estimatedCost").isNumber())
                .andExpect(jsonPath("$.data.savedEstimatedCost").isNumber())
                .andExpect(jsonPath("$.data.featureUsage[0].feature").value("NEWS_SUMMARY"));

        mockMvc.perform(get("/api/v1/admin/ai/usage-logs")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .param("page", "0")
                        .param("size", "10")
                        .param("sort", "estimatedCost,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].cacheHit").value(false));
    }

    @Test
    void metricsSeparateTechnicalExplanationCostAndCacheUsage() throws Exception {
        String body = """
                {"symbol":"000660","interval":"1D","promptVersion":"technical-explanation-v1"}
                """;
        var admin = jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));

        mockMvc.perform(post("/api/v1/ai/technical-explanations")
                        .with(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/ai/technical-explanations")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/ai/metrics")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.featureUsage[0].feature").value("TECHNICAL_EXPLANATION"))
                .andExpect(jsonPath("$.data.featureUsage[0].requestCount").value(2))
                .andExpect(jsonPath("$.data.featureUsage[0].modelCallCount").value(1))
                .andExpect(jsonPath("$.data.featureUsage[0].cacheHitCount").value(1))
                .andExpect(jsonPath("$.data.featureUsage[0].totalTokens").isNumber())
                .andExpect(jsonPath("$.data.featureUsage[0].estimatedCost").isNumber())
                .andExpect(jsonPath("$.data.featureUsage[0].savedEstimatedCost").isNumber());
    }
}
