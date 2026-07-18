package com.finwatch.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class PortfolioEnvironmentValidatorTest {

    @Test
    void acceptsSecureSameOriginPortfolioConfiguration() {
        new PortfolioEnvironmentValidator(secureEnvironment()).afterPropertiesSet();
    }

    @Test
    void rejectsDemoUsersAndLocalInfrastructure() {
        MockEnvironment environment = secureEnvironment()
                .withProperty("app.auth.demo-users-enabled", "true")
                .withProperty("spring.data.redis.host", "localhost");

        assertThatThrownBy(() -> new PortfolioEnvironmentValidator(environment).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("demo-users-enabled");
    }

    @Test
    void rejectsOriginMismatchAndDatabaseWithoutTls() {
        MockEnvironment originMismatch = secureEnvironment()
                .withProperty("app.cors.allowed-origins", "https://evil.example");
        assertThatThrownBy(() -> new PortfolioEnvironmentValidator(originMismatch).afterPropertiesSet())
                .hasMessageContaining("CORS_ALLOWED_ORIGINS");

        MockEnvironment noTls = secureEnvironment()
                .withProperty("spring.datasource.url", "jdbc:postgresql://db.internal:5432/finwatch");
        assertThatThrownBy(() -> new PortfolioEnvironmentValidator(noTls).afterPropertiesSet())
                .hasMessageContaining("require TLS");
    }

    @Test
    void validatesEnabledKakaoAgainstCanonicalCallback() {
        MockEnvironment environment = secureEnvironment()
                .withProperty("app.auth.kakao.enabled", "true")
                .withProperty("app.auth.kakao.client-id", "rest-api-key")
                .withProperty("app.auth.kakao.client-secret", "a-secure-client-secret")
                .withProperty("app.auth.kakao.redirect-uri", "https://wrong.example/callback");

        assertThatThrownBy(() -> new PortfolioEnvironmentValidator(environment).afterPropertiesSet())
                .hasMessageContaining("app.auth.kakao.redirect-uri");
    }

    private MockEnvironment secureEnvironment() {
        return new MockEnvironment()
                .withProperty("app.public-base-url", "https://finwatch.example")
                .withProperty("app.auth.frontend-base-url", "https://finwatch.example")
                .withProperty("app.cors.allowed-origins", "https://finwatch.example")
                .withProperty("app.auth.demo-users-enabled", "false")
                .withProperty("app.auth.session.cookie-secure", "true")
                .withProperty("app.data.mode", "LIVE")
                .withProperty("spring.data.redis.host", "redis")
                .withProperty("spring.data.redis.password", "a-strong-redis-password")
                .withProperty("spring.datasource.url",
                        "jdbc:postgresql://db.internal:5432/finwatch?sslmode=require")
                .withProperty("spring.datasource.username", "finwatch_app")
                .withProperty("spring.datasource.password", "a-strong-database-password")
                .withProperty("app.auth.jwt-secret",
                        "a-strong-portfolio-jwt-secret-with-more-than-forty-eight-bytes")
                .withProperty("app.ai.provider", "gemini")
                .withProperty("app.ai.gemini.api-key", "a-secure-gemini-api-key")
                .withProperty("app.auth.kakao.enabled", "false");
    }
}
