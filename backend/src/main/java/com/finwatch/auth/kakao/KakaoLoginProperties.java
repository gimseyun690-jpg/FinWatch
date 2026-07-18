package com.finwatch.auth.kakao;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class KakaoLoginProperties {

    private final boolean enabled;
    private final String clientId;
    private final String clientSecret;
    private final String issuer;
    private final String authorizationUri;
    private final String tokenUri;
    private final String jwkSetUri;
    private final String redirectUri;
    private final String frontendBaseUrl;
    private final Duration attemptTtl;
    private final Duration connectTimeout;
    private final Duration readTimeout;

    public KakaoLoginProperties(
            @Value("${app.auth.kakao.enabled}") boolean enabled,
            @Value("${app.auth.kakao.client-id:}") String clientId,
            @Value("${app.auth.kakao.client-secret:}") String clientSecret,
            @Value("${app.auth.kakao.issuer}") String issuer,
            @Value("${app.auth.kakao.authorization-uri}") String authorizationUri,
            @Value("${app.auth.kakao.token-uri}") String tokenUri,
            @Value("${app.auth.kakao.jwk-set-uri}") String jwkSetUri,
            @Value("${app.auth.kakao.redirect-uri}") String redirectUri,
            @Value("${app.auth.frontend-base-url}") String frontendBaseUrl,
            @Value("${app.auth.kakao.attempt-ttl}") Duration attemptTtl,
            @Value("${app.auth.kakao.connect-timeout}") Duration connectTimeout,
            @Value("${app.auth.kakao.read-timeout}") Duration readTimeout) {
        this.enabled = enabled;
        this.clientId = clientId.trim();
        this.clientSecret = clientSecret.trim();
        this.issuer = issuer.trim();
        this.authorizationUri = authorizationUri.trim();
        this.tokenUri = tokenUri.trim();
        this.jwkSetUri = jwkSetUri.trim();
        this.redirectUri = redirectUri.trim();
        this.frontendBaseUrl = frontendBaseUrl.replaceAll("/+$", "");
        this.attemptTtl = attemptTtl;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
    }

    @PostConstruct
    void validate() {
        if (enabled && (clientId.isBlank() || clientSecret.isBlank() || redirectUri.isBlank())) {
            throw new IllegalStateException(
                    "KAKAO_LOGIN_ENABLED=true requires KAKAO_REST_API_KEY, KAKAO_CLIENT_SECRET and KAKAO_REDIRECT_URI.");
        }
        if (attemptTtl.isNegative() || attemptTtl.isZero()) {
            throw new IllegalStateException("KAKAO_OAUTH_ATTEMPT_TTL must be positive.");
        }
    }

    public boolean enabled() { return enabled; }
    public String clientId() { return clientId; }
    public String clientSecret() { return clientSecret; }
    public String issuer() { return issuer; }
    public String authorizationUri() { return authorizationUri; }
    public String tokenUri() { return tokenUri; }
    public String jwkSetUri() { return jwkSetUri; }
    public String redirectUri() { return redirectUri; }
    public String frontendBaseUrl() { return frontendBaseUrl; }
    public Duration attemptTtl() { return attemptTtl; }
    public Duration connectTimeout() { return connectTimeout; }
    public Duration readTimeout() { return readTimeout; }
}
