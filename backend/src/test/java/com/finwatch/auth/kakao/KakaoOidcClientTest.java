package com.finwatch.auth.kakao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.sun.net.httpserver.HttpServer;

class KakaoOidcClientTest {

    private HttpServer server;
    private volatile int tokenStatus;
    private volatile long tokenDelayMillis;

    @BeforeEach
    void setUp() throws Exception {
        tokenStatus = 400;
        tokenDelayMillis = 0;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/token", exchange -> {
            try {
                if (tokenDelayMillis > 0) Thread.sleep(tokenDelayMillis);
                byte[] body = "{\"error\":\"provider fixture\"}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(tokenStatus, body.length);
                exchange.getResponseBody().write(body);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void mapsRejectedAuthorizationCodeToNonRetryableFailure() {
        KakaoOidcClient client = client(Duration.ofSeconds(1));

        assertThatThrownBy(() -> client.exchange("rejected-code", "verifier", "nonce-hash"))
                .isInstanceOf(KakaoLoginException.class)
                .satisfies(error -> {
                    KakaoLoginException exception = (KakaoLoginException) error;
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(exception.getCode()).isEqualTo("KAKAO_TOKEN_REJECTED");
                });
    }

    @Test
    void mapsProviderServerErrorToRetryableUnavailableFailure() {
        tokenStatus = 503;
        KakaoOidcClient client = client(Duration.ofSeconds(1));

        assertThatThrownBy(() -> client.exchange("provider-error", "verifier", "nonce-hash"))
                .isInstanceOf(KakaoLoginException.class)
                .satisfies(error -> {
                    KakaoLoginException exception = (KakaoLoginException) error;
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(exception.getCode()).isEqualTo("KAKAO_TOKEN_UNAVAILABLE");
                });
    }

    @Test
    void mapsProviderRateLimitToRetryableUnavailableFailure() {
        tokenStatus = 429;
        KakaoOidcClient client = client(Duration.ofSeconds(1));

        assertThatThrownBy(() -> client.exchange("rate-limited", "verifier", "nonce-hash"))
                .isInstanceOf(KakaoLoginException.class)
                .satisfies(error -> {
                    KakaoLoginException exception = (KakaoLoginException) error;
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(exception.getCode()).isEqualTo("KAKAO_TOKEN_UNAVAILABLE");
                });
    }

    @Test
    void mapsProviderReadTimeoutToRetryableUnavailableFailure() {
        tokenStatus = 200;
        tokenDelayMillis = 500;
        KakaoOidcClient client = client(Duration.ofMillis(100));

        assertThatThrownBy(() -> client.exchange("slow-provider", "verifier", "nonce-hash"))
                .isInstanceOf(KakaoLoginException.class)
                .satisfies(error -> {
                    KakaoLoginException exception = (KakaoLoginException) error;
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(exception.getCode()).isEqualTo("KAKAO_TOKEN_UNAVAILABLE");
                });
    }

    private KakaoOidcClient client(Duration readTimeout) {
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        KakaoLoginProperties properties = new KakaoLoginProperties(
                true,
                "test-client",
                "test-secret",
                "https://kauth.kakao.com",
                "https://kauth.kakao.com/oauth/authorize",
                baseUrl + "/token",
                baseUrl + "/jwks",
                "http://localhost:8080/api/v1/auth/kakao/callback",
                "http://localhost:5173",
                Duration.ofMinutes(5),
                Duration.ofSeconds(1),
                readTimeout);
        return new KakaoOidcClient(properties, new KakaoIdTokenValidator(properties));
    }
}
