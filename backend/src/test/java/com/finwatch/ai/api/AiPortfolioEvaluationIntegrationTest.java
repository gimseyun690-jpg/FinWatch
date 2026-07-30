package com.finwatch.ai.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.finwatch.ai.repository.AiPortfolioEvaluationRepository;
import com.finwatch.ai.repository.AiUsageLogRepository;
import com.finwatch.user.repository.AppUserRepository;

@SpringBootTest(properties = "app.ai.provider=mock")
@AutoConfigureMockMvc
@ActiveProfiles("demo")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AiPortfolioEvaluationIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AiPortfolioEvaluationRepository evaluations;
    @Autowired AiUsageLogRepository usageLogs;
    @Autowired AppUserRepository users;

    @BeforeEach
    void reset() {
        usageLogs.deleteAll();
        evaluations.deleteAll();
    }

    @Test
    void createsServerScopedEvaluationThenReusesWindowCachePerUser() throws Exception {
        Long adminId = users.findByEmailIgnoreCase("admin@finwatch.local").orElseThrow().getId();
        Long userId = users.findByEmailIgnoreCase("user@finwatch.local").orElseThrow().getId();

        mockMvc.perform(post("/api/v1/ai/portfolio-evaluations")
                        .with(authenticated(adminId, "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.evaluationId").isNumber())
                .andExpect(jsonPath("$.data.baseCurrency").value("KRW"))
                .andExpect(jsonPath("$.data.balanceStatus").isString())
                .andExpect(jsonPath("$.data.diversification.evidenceIds[0]").value("C1"))
                .andExpect(jsonPath("$.data.evidence[*].id", hasItem("P1")))
                .andExpect(jsonPath("$.data.evidence[*].id", hasItem("C1")))
                .andExpect(jsonPath("$.data.evidence[*].id", hasItem("FX1")))
                .andExpect(jsonPath("$.data.audit.cacheHit").value(false))
                .andExpect(jsonPath("$.data.audit.inputTokens").isNumber())
                .andExpect(jsonPath("$.data.audit.estimatedCost").isNumber())
                .andExpect(jsonPath("$.data.disclaimer").value(
                        "AI 평가는 투자 권유가 아닌 포트폴리오 구성 점검용 참고 정보입니다."));

        mockMvc.perform(post("/api/v1/ai/portfolio-evaluations")
                        .with(authenticated(adminId, "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.audit.cacheHit").value(true))
                .andExpect(jsonPath("$.data.audit.inputTokens").value(0))
                .andExpect(jsonPath("$.data.audit.outputTokens").value(0))
                .andExpect(jsonPath("$.data.audit.estimatedCost").value(0))
                .andExpect(jsonPath("$.data.audit.savedEstimatedCost").isNumber());

        mockMvc.perform(post("/api/v1/ai/portfolio-evaluations")
                        .with(authenticated(userId, "user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.audit.cacheHit").value(false));

        assertThat(evaluations.count()).isEqualTo(2);
        assertThat(usageLogs.findAll())
                .filteredOn(log -> "PORTFOLIO_EVALUATION".equals(log.getFeatureType()))
                .hasSize(3)
                .extracting(log -> log.getTargetId())
                .contains(adminId, userId);
    }

    @Test
    void rejectsUnsupportedPromptVersionBeforeCallingProvider() throws Exception {
        Long userId = users.findByEmailIgnoreCase("user@finwatch.local").orElseThrow().getId();

        mockMvc.perform(post("/api/v1/ai/portfolio-evaluations")
                        .with(authenticated(userId, "user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"promptVersion\":\"unknown\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AI_PROMPT_VERSION_UNSUPPORTED"));

        assertThat(evaluations.count()).isZero();
    }

    private JwtRequestPostProcessor authenticated(Long userId, String subject) {
        return jwt().jwt(token -> token.subject(subject).claim("userId", userId));
    }
}
