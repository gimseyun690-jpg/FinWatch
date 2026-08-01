package com.finwatch.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import com.finwatch.ai.provider.AiProvider;
import com.finwatch.ai.provider.PortfolioEvaluationResponseValidator;
import com.finwatch.user.repository.AppUserRepository;

@SpringBootTest(properties = "app.ai.provider=gemini")
@ActiveProfiles("demo")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@EnabledIfEnvironmentVariable(named = "RUN_LIVE_GEMINI_SMOKE", matches = "true")
class LivePortfolioGeminiSmokeTest {

    @Autowired PortfolioEvaluationSnapshotFactory snapshots;
    @Autowired AiProvider provider;
    @Autowired PortfolioEvaluationResponseValidator validator;
    @Autowired AppUserRepository users;
    @Value("${app.ai.portfolio-prompt-version}") String promptVersion;

    @Test
    void configuredGeminiReturnsAGroundedPortfolioContract() {
        Long userId = users.findByEmailIgnoreCase("admin@finwatch.local").orElseThrow().getId();
        var snapshot = snapshots.create(userId);

        var response = provider.evaluatePortfolio(snapshot.input(), promptVersion);

        assertThat(response.headline()).isNotBlank();
        assertThat(response.modelName()).isNotBlank();
        assertThat(validator.validate(response, snapshot.input())).isSameAs(response);
    }
}
