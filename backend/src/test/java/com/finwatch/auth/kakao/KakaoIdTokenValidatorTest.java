package com.finwatch.auth.kakao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetSocketAddress;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.finwatch.auth.session.AuthHashing;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;

class KakaoIdTokenValidatorTest {

    private HttpServer server;
    private RSAKey signingKey;
    private KakaoIdTokenValidator validator;
    private volatile int jwkStatus;
    private volatile long jwkDelayMillis;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        signingKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey((RSAPrivateKey) keyPair.getPrivate())
                .keyID("test-key")
                .algorithm(JWSAlgorithm.RS256)
                .build();
        jwkStatus = 200;
        jwkDelayMillis = 0;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/jwks", exchange -> {
            if (jwkDelayMillis > 0) {
                try {
                    Thread.sleep(jwkDelayMillis);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] body = new JWKSet(signingKey.toPublicJWK()).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(jwkStatus, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        KakaoLoginProperties properties = new KakaoLoginProperties(
                true,
                "test-client",
                "test-secret",
                "https://kauth.kakao.com",
                "https://kauth.kakao.com/oauth/authorize",
                "https://kauth.kakao.com/oauth/token",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/jwks",
                "http://localhost:8080/api/v1/auth/kakao/callback",
                "http://localhost:5173",
                Duration.ofMinutes(5),
                Duration.ofSeconds(1),
                Duration.ofMillis(100));
        validator = new KakaoIdTokenValidator(properties);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void validatesRs256IssuerAudienceNonceAndSubject() throws Exception {
        String nonce = "one-time-nonce";
        var profile = validator.validate(token("test-client", nonce), AuthHashing.sha256(nonce));

        assertThat(profile.subject()).isEqualTo("kakao-sub-123");
        assertThat(profile.nickname()).isEqualTo("Ryan");
        assertThat(profile.picture()).isEqualTo("https://example.test/ryan.png");
    }

    @Test
    void rejectsWrongAudienceAndNonce() throws Exception {
        assertThatThrownBy(() -> validator.validate(
                token("another-client", "nonce"),
                AuthHashing.sha256("nonce")))
                .isInstanceOf(KakaoLoginException.class)
                .hasMessageContaining("audience");

        assertThatThrownBy(() -> validator.validate(
                token("test-client", "actual"),
                AuthHashing.sha256("expected")))
                .isInstanceOf(KakaoLoginException.class)
                .hasMessageContaining("nonce");
    }

    @Test
    void rejectsTokenWhenJwkEndpointReturnsServerError() throws Exception {
        jwkStatus = 503;

        assertThatThrownBy(() -> validator.validate(
                token("test-client", "nonce"),
                AuthHashing.sha256("nonce")))
                .isInstanceOf(KakaoLoginException.class)
                .satisfies(error -> assertThat(((KakaoLoginException) error).getCode())
                        .isEqualTo("KAKAO_TOKEN_UNAVAILABLE"));
    }

    @Test
    void rejectsTokenWhenJwkEndpointTimesOut() throws Exception {
        jwkDelayMillis = 500;

        assertThatThrownBy(() -> validator.validate(
                token("test-client", "nonce"),
                AuthHashing.sha256("nonce")))
                .isInstanceOf(KakaoLoginException.class)
                .satisfies(error -> assertThat(((KakaoLoginException) error).getCode())
                        .isEqualTo("KAKAO_TOKEN_UNAVAILABLE"));
    }

    private String token(String audience, String nonce) throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("https://kauth.kakao.com")
                .audience(audience)
                .subject("kakao-sub-123")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(600)))
                .claim("nonce", nonce)
                .claim("nickname", "Ryan")
                .claim("picture", "https://example.test/ryan.png")
                .build();
        SignedJWT token = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).keyID("test-key").build(),
                claims);
        token.sign(new RSASSASigner(signingKey));
        return token.serialize();
    }
}
