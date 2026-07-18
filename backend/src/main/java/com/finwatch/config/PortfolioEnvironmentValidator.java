package com.finwatch.config;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Profile("portfolio")
public class PortfolioEnvironmentValidator implements InitializingBean {

    private static final String LOCAL_JWT_SECRET = "finwatch-local-jwt-secret-change-before-production-2026";

    private final Environment environment;

    public PortfolioEnvironmentValidator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        String publicBaseUrl = canonicalHttpsBase(required("app.public-base-url"));
        requireEqual("app.auth.frontend-base-url", publicBaseUrl);
        requireOnlyCorsOrigin(publicBaseUrl);
        requireFalse("app.auth.demo-users-enabled");
        requireTrue("app.auth.session.cookie-secure");
        requireEqualIgnoreCase("app.data.mode", "LIVE");
        requireNotLocalRedis();
        requireSecureDatabase();
        requireStrongSecret("app.auth.jwt-secret", 48, List.of(LOCAL_JWT_SECRET));
        requireNonMockAi();
        validateKakaoRedirect(publicBaseUrl);
    }

    private void requireOnlyCorsOrigin(String publicBaseUrl) {
        List<String> origins = Arrays.stream(required("app.cors.allowed-origins").split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
        if (!origins.equals(List.of(publicBaseUrl))) {
            throw invalid("CORS_ALLOWED_ORIGINS must contain only APP_PUBLIC_BASE_URL in portfolio.");
        }
    }

    private void requireNotLocalRedis() {
        String host = required("spring.data.redis.host").toLowerCase(Locale.ROOT);
        if (host.equals("localhost") || host.equals("127.0.0.1") || host.equals("0.0.0.0")) {
            throw invalid("REDIS_HOST must use the private Docker or managed Redis endpoint in portfolio.");
        }
        requireStrongSecret("spring.data.redis.password", 16, List.of("redis", "password"));
    }

    private void requireSecureDatabase() {
        String url = required("spring.datasource.url");
        String normalized = url.toLowerCase(Locale.ROOT);
        if (!normalized.startsWith("jdbc:postgresql://")
                || !(normalized.contains("sslmode=require")
                || normalized.contains("sslmode=verify-ca")
                || normalized.contains("sslmode=verify-full"))) {
            throw invalid("DB_URL must be PostgreSQL and require TLS in portfolio.");
        }
        String username = required("spring.datasource.username").toLowerCase(Locale.ROOT);
        if (username.equals("postgres") || username.equals("finwatch")) {
            throw invalid("DB_USERNAME must be a dedicated non-master portfolio application user.");
        }
        requireStrongSecret("spring.datasource.password", 16, List.of("finwatch-local", "password", "postgres"));
    }

    private void requireNonMockAi() {
        String provider = required("app.ai.provider");
        if (provider.equalsIgnoreCase("mock")) {
            throw invalid("AI_PROVIDER=mock is not allowed in portfolio.");
        }
        if (provider.equalsIgnoreCase("gemini")) {
            requireStrongSecret("app.ai.gemini.api-key", 16, List.of());
        }
    }

    private void validateKakaoRedirect(String publicBaseUrl) {
        if (!Boolean.parseBoolean(environment.getProperty("app.auth.kakao.enabled", "false"))) {
            return;
        }
        requireEqual("app.auth.kakao.redirect-uri", publicBaseUrl + "/api/v1/auth/kakao/callback");
        requireStrongSecret("app.auth.kakao.client-id", 8, List.of());
        requireStrongSecret("app.auth.kakao.client-secret", 16, List.of());
    }

    private String canonicalHttpsBase(String value) {
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw invalid("APP_PUBLIC_BASE_URL must be an absolute HTTPS URL.");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || uri.getHost() == null
                || uri.getUserInfo() != null
                || uri.getQuery() != null
                || uri.getFragment() != null
                || (uri.getPath() != null && !uri.getPath().isBlank() && !"/".equals(uri.getPath()))) {
            throw invalid("APP_PUBLIC_BASE_URL must be an origin-only HTTPS URL.");
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private void requireStrongSecret(String key, int minimumLength, List<String> forbidden) {
        String value = required(key);
        if (value.length() < minimumLength || forbidden.stream().anyMatch(value::equalsIgnoreCase)) {
            throw invalid(key + " is missing or unsafe for portfolio.");
        }
    }

    private void requireEqual(String key, String expected) {
        if (!required(key).equals(expected)) {
            throw invalid(key + " must exactly match " + expected + ".");
        }
    }

    private void requireEqualIgnoreCase(String key, String expected) {
        if (!required(key).equalsIgnoreCase(expected)) {
            throw invalid(key + " must be " + expected + " in portfolio.");
        }
    }

    private void requireTrue(String key) {
        if (!Boolean.parseBoolean(required(key))) {
            throw invalid(key + " must be true in portfolio.");
        }
    }

    private void requireFalse(String key) {
        if (Boolean.parseBoolean(required(key))) {
            throw invalid(key + " must be false in portfolio.");
        }
    }

    private String required(String key) {
        String value = environment.getProperty(key);
        if (value == null || value.isBlank()) {
            throw invalid(key + " is required in portfolio.");
        }
        return value.trim();
    }

    private IllegalStateException invalid(String message) {
        return new IllegalStateException("Unsafe portfolio configuration: " + message);
    }
}
