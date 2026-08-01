package com.finwatch.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import com.finwatch.ai.dto.PortfolioEvaluationInput;
import com.finwatch.ai.dto.PortfolioEvaluationRequest;
import com.finwatch.ai.provider.AiProvider;
import com.finwatch.ai.provider.AiProvider.PortfolioEvaluationResult;
import com.finwatch.ai.provider.AiProvider.PortfolioEvaluationStatement;
import com.finwatch.ai.provider.MockAiProvider;
import com.finwatch.ai.repository.AiPortfolioEvaluationRepository;
import com.finwatch.user.repository.AppUserRepository;

@SpringBootTest(properties = "app.ai.provider=fallback-test")
@ActiveProfiles("demo")
@Import(AiPortfolioEvaluationFallbackIntegrationTest.ProviderConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AiPortfolioEvaluationFallbackIntegrationTest {

    @Autowired AiPortfolioEvaluationService service;
    @Autowired AiPortfolioEvaluationRepository evaluations;
    @Autowired AppUserRepository users;

    @BeforeEach
    void resetProviderCalls() {
        ProviderConfiguration.providerCalls.set(0);
    }

    @Test
    void returnsButDoesNotReuseAProviderResponseThatFailedGroundingValidation() {
        Long userId = users.findByEmailIgnoreCase("admin@finwatch.local").orElseThrow().getId();

        var first = service.evaluate(userId, new PortfolioEvaluationRequest(null));
        var second = service.evaluate(userId, new PortfolioEvaluationRequest(null));

        assertThat(first.audit().modelName()).contains("server-grounded-fallback");
        assertThat(first.dataLimitations())
                .contains(PortfolioEvaluationResultResolver.FALLBACK_NOTICE);
        assertThat(first.audit().cacheHit()).isFalse();
        assertThat(second.audit().cacheHit()).isFalse();
        assertThat(second.evaluationId()).isEqualTo(first.evaluationId());
        assertThat(ProviderConfiguration.providerCalls).hasValue(2);
        assertThat(evaluations.count()).isEqualTo(1);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ProviderConfiguration {

        private static final AtomicInteger providerCalls = new AtomicInteger();

        @Bean
        AiProvider invalidPortfolioProvider() {
            MockAiProvider validProvider = new MockAiProvider();
            return new MockAiProvider() {
                @Override
                public PortfolioEvaluationResult evaluatePortfolio(
                        PortfolioEvaluationInput input,
                        String promptVersion) {
                    providerCalls.incrementAndGet();
                    PortfolioEvaluationResult valid =
                            validProvider.evaluatePortfolio(input, promptVersion);
                    return new PortfolioEvaluationResult(
                            "gemini-contract-fixture",
                            valid.headline(),
                            valid.summary(),
                            valid.diversification(),
                            new PortfolioEvaluationStatement(
                                    "상위 1개 종목이 전체 평가액에 큰 영향을 줍니다.",
                                    List.of("C1")),
                            valid.currencyExposure(),
                            valid.performanceContext(),
                            valid.strengths(),
                            valid.riskFactors(),
                            valid.reviewPoints(),
                            valid.dataLimitations(),
                            160,
                            70);
                }
            };
        }
    }
}
