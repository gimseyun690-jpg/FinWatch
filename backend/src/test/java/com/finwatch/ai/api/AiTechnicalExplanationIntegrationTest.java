package com.finwatch.ai.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.finwatch.ai.repository.AiTechnicalExplanationRepository;
import com.finwatch.ai.repository.AiUsageLogRepository;
import com.finwatch.stock.domain.MarketPrice;
import com.finwatch.stock.domain.Stock;
import com.finwatch.stock.repository.MarketPriceRepository;
import com.finwatch.stock.repository.StockRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = "app.ai.provider=mock")
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class AiTechnicalExplanationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AiTechnicalExplanationRepository explanationRepository;

    @Autowired
    private AiUsageLogRepository usageLogRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private MarketPriceRepository marketPriceRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void reset() {
        usageLogRepository.deleteAll();
        explanationRepository.deleteAll();
    }

    @Test
    void identicalTechnicalSnapshotUsesCacheAndKeepsEvidence() throws Exception {
        String body = """
                {"symbol":"000660","interval":"1D","promptVersion":"technical-explanation-v1"}
                """;

        mockMvc.perform(post("/api/v1/ai/technical-explanations")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.symbol").value("000660"))
                .andExpect(jsonPath("$.data.cacheHit").value(false))
                .andExpect(jsonPath("$.data.inputHash").isNotEmpty())
                .andExpect(jsonPath("$.data.evidence[0].id").value("I1"))
                .andExpect(jsonPath("$.data.evidence[2].indicator").value("MACD"))
                .andExpect(jsonPath("$.data.evidence[2].values.fastPeriod").value("12"))
                .andExpect(jsonPath("$.data.evidence[2].values.slowPeriod").value("26"))
                .andExpect(jsonPath("$.data.evidence[2].values.signalPeriod").value("9"))
                .andExpect(jsonPath("$.data.supportingSignals[0].evidenceIds[0]").value("I1"))
                .andExpect(jsonPath("$.data.dataLimitations[0]").value(org.hamcrest.Matchers.containsString("DEMO")))
                .andExpect(jsonPath("$.data.inputTokens").isNumber())
                .andExpect(jsonPath("$.data.estimatedCost").isNumber());

        mockMvc.perform(post("/api/v1/ai/technical-explanations")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cacheHit").value(true))
                .andExpect(jsonPath("$.data.inputTokens").value(0))
                .andExpect(jsonPath("$.data.outputTokens").value(0))
                .andExpect(jsonPath("$.data.estimatedCost").value(0));

        assertThat(explanationRepository.count()).isEqualTo(1);
        assertThat(usageLogRepository.count()).isEqualTo(2);
        assertThat(usageLogRepository.findAll())
                .allMatch(log -> "TECHNICAL_EXPLANATION".equals(log.getFeatureType()));
    }

    @Test
    void rejectsUnsupportedIntervalBeforeCallingProvider() throws Exception {
        mockMvc.perform(post("/api/v1/ai/technical-explanations")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"000660\",\"interval\":\"1m\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TECHNICAL_INTERVAL_UNSUPPORTED"));

        assertThat(explanationRepository.count()).isZero();
        assertThat(usageLogRepository.count()).isZero();
    }

    @Test
    void rejectsUnsupportedPromptVersion() throws Exception {
        mockMvc.perform(post("/api/v1/ai/technical-explanations")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"000660\",\"interval\":\"1D\",\"promptVersion\":\"bypass-v99\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AI_PROMPT_VERSION_UNSUPPORTED"));

        assertThat(explanationRepository.count()).isZero();
        assertThat(usageLogRepository.count()).isZero();
    }

    @Test
    @Transactional
    void correctedHistoryAndNewDailyBarProduceNewInputHashes() throws Exception {
        String body = """
                {"symbol":"005930","interval":"1D","promptVersion":"technical-explanation-v1"}
                """;

        JsonNode first = request(body);
        String originalHash = first.path("inputHash").asText();
        assertThat(first.path("cacheHit").asBoolean()).isFalse();

        Stock stock = stockRepository.findFirstBySymbolAndActiveTrue("005930").orElseThrow();
        MarketPrice latest = marketPriceRepository.findTopByStockIdOrderByRecordedAtDesc(stock.getId()).orElseThrow();
        latest.applyProviderBar(
                latest.getOpenPrice(),
                latest.getHighPrice(),
                latest.getLowPrice(),
                latest.getClosePrice().add(BigDecimal.ONE),
                latest.getVolume(),
                "DEMO_CORRECTED");
        marketPriceRepository.saveAndFlush(latest);

        JsonNode corrected = request(body);
        String correctedHash = corrected.path("inputHash").asText();
        assertThat(corrected.path("cacheHit").asBoolean()).isFalse();
        assertThat(correctedHash).isNotEqualTo(originalHash);

        BigDecimal nextClose = latest.getClosePrice().add(BigDecimal.TEN);
        marketPriceRepository.saveAndFlush(MarketPrice.create(
                stock,
                "1D",
                nextClose.subtract(BigDecimal.ONE),
                nextClose.add(BigDecimal.TEN),
                nextClose.subtract(BigDecimal.TEN),
                nextClose,
                latest.getVolume(),
                Instant.now().minusSeconds(30),
                "DEMO_NEW_BAR"));

        JsonNode withNewBar = request(body);
        assertThat(withNewBar.path("cacheHit").asBoolean()).isFalse();
        assertThat(withNewBar.path("inputHash").asText()).isNotEqualTo(correctedHash);
        assertThat(explanationRepository.count()).isEqualTo(3);
    }

    private JsonNode request(String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/ai/technical-explanations")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        String json = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        return objectMapper.readTree(json).path("data");
    }
}
