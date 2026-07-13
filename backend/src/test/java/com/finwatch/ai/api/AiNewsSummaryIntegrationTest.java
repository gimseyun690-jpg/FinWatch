package com.finwatch.ai.api;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.finwatch.ai.repository.AiAnalysisRepository;
import com.finwatch.ai.repository.AiUsageLogRepository;
import com.finwatch.news.repository.NewsArticleRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class AiNewsSummaryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NewsArticleRepository newsArticleRepository;

    @Autowired
    private AiAnalysisRepository aiAnalysisRepository;

    @Autowired
    private AiUsageLogRepository aiUsageLogRepository;

    @BeforeEach
    void resetUsageData() {
        aiUsageLogRepository.deleteAll();
        aiAnalysisRepository.deleteAll();
    }

    @Test
    void secondIdenticalRequestUsesCacheAndSavesCost() throws Exception {
        Long newsId = newsArticleRepository.findAllByStockSymbolOrderByPublishedAtDesc("000660")
                .getFirst()
                .getId();
        String body = "{\"newsId\":" + newsId + ",\"promptVersion\":\"news-analysis-v2\"}";

        mockMvc.perform(post("/api/v1/ai/news-summaries")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cacheHit").value(false))
                .andExpect(jsonPath("$.data.positiveFactors").isArray())
                .andExpect(jsonPath("$.data.riskFactors").isArray())
                .andExpect(jsonPath("$.data.evidenceSegments[0]").value("S1"))
                .andExpect(jsonPath("$.data.analysisScope").value("FULL_PROCESSED_TEXT"))
                .andExpect(jsonPath("$.data.providerCallCount").value(1))
                .andExpect(jsonPath("$.data.inputTokens").isNumber())
                .andExpect(jsonPath("$.data.estimatedCost").isNumber());

        mockMvc.perform(post("/api/v1/ai/news-summaries")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cacheHit").value(true))
                .andExpect(jsonPath("$.data.inputTokens").value(0))
                .andExpect(jsonPath("$.data.estimatedCost").value(0));

        org.assertj.core.api.Assertions.assertThat(aiAnalysisRepository.count()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(aiUsageLogRepository.count()).isEqualTo(2);
    }

    @Test
    void metadataOnlyNewsCannotBeSentToAiProvider() throws Exception {
        Long newsId = newsArticleRepository.findByExternalId("demo-news-000660-metadata-only")
                .orElseThrow()
                .getId();
        String body = "{\"newsId\":" + newsId + ",\"promptVersion\":\"news-analysis-v2\"}";

        mockMvc.perform(post("/api/v1/ai/news-summaries")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("NEWS_CONTENT_UNAVAILABLE"));

        org.assertj.core.api.Assertions.assertThat(aiAnalysisRepository.count()).isZero();
        org.assertj.core.api.Assertions.assertThat(aiUsageLogRepository.count()).isZero();
    }

    @Test
    void rejectsUnregisteredPromptVersionBeforeCreatingAnalysis() throws Exception {
        Long newsId = newsArticleRepository.findAllByStockSymbolOrderByPublishedAtDesc("000660")
                .getFirst()
                .getId();

        mockMvc.perform(post("/api/v1/ai/news-summaries")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newsId\":" + newsId + ",\"promptVersion\":\"attacker-cache-bypass\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AI_PROMPT_VERSION_UNSUPPORTED"));

        org.assertj.core.api.Assertions.assertThat(aiAnalysisRepository.count()).isZero();
        org.assertj.core.api.Assertions.assertThat(aiUsageLogRepository.count()).isZero();
    }
}
