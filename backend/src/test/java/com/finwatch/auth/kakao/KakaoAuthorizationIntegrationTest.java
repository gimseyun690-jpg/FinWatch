package com.finwatch.auth.kakao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import jakarta.servlet.http.Cookie;
import com.finwatch.auth.kakao.KakaoIdTokenValidator.KakaoProfile;
import com.finwatch.user.domain.UserRole;
import com.finwatch.user.identity.SocialUserProvisioningService;
import com.finwatch.user.repository.AppUserRepository;

@SpringBootTest(properties = {
        "app.auth.kakao.enabled=true",
        "app.auth.kakao.client-id=test-rest-api-key",
        "app.auth.kakao.client-secret=test-client-secret",
        "app.auth.kakao.redirect-uri=http://localhost:8080/api/v1/auth/kakao/callback",
        "app.auth.kakao.authorization-uri=https://kauth.kakao.com/oauth/authorize"
})
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class KakaoAuthorizationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SocialUserProvisioningService provisioningService;

    @Autowired
    private AppUserRepository userRepository;

    @MockitoBean
    private KakaoOidcClient oidcClient;

    @Test
    void authorizeUsesStateNoncePkceAndHttpOnlyCorrelation() throws Exception {
        var result = mockMvc.perform(get("/api/v1/auth/kakao/authorize")
                        .param("returnTo", "/stocks/KRX/000660/news"))
                .andExpect(status().isFound())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andReturn();

        URI location = URI.create(result.getResponse().getHeader(HttpHeaders.LOCATION));
        Map<String, String> query = query(location.getRawQuery());
        assertThat(location.getScheme()).isEqualTo("https");
        assertThat(location.getHost()).isEqualTo("kauth.kakao.com");
        assertThat(query).containsEntry("response_type", "code")
                .containsEntry("client_id", "test-rest-api-key")
                .containsEntry("scope", "openid")
                .containsEntry("code_challenge_method", "S256");
        assertThat(query.get("state")).hasSizeGreaterThanOrEqualTo(43);
        assertThat(query.get("nonce")).hasSizeGreaterThanOrEqualTo(43);
        assertThat(query.get("code_challenge")).hasSizeGreaterThanOrEqualTo(43);

        Cookie correlation = result.getResponse().getCookie("FW_KAKAO_CORRELATION");
        assertThat(correlation).isNotNull();
        assertThat(correlation.isHttpOnly()).isTrue();
        assertThat(correlation.getPath()).isEqualTo("/api/v1/auth/kakao");
    }

    @Test
    void rejectsMismatchedStateWithoutCreatingSession() throws Exception {
        var authorize = mockMvc.perform(get("/api/v1/auth/kakao/authorize"))
                .andExpect(status().isFound())
                .andReturn();
        Cookie correlation = authorize.getResponse().getCookie("FW_KAKAO_CORRELATION");

        mockMvc.perform(get("/api/v1/auth/kakao/callback")
                        .cookie(correlation)
                        .param("code", "must-not-be-exchanged")
                        .param("state", "attacker-state"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string(HttpHeaders.LOCATION,
                        "http://localhost:5173/login?error=kakao_request_invalid"));

        mockMvc.perform(get("/api/v1/auth/session").cookie(new Cookie("FW_SESSION", "not-created")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cancellationConsumesAttemptAndReplayIsRejected() throws Exception {
        AuthorizationAttempt attempt = authorize("/news");

        var cancelled = mockMvc.perform(get("/api/v1/auth/kakao/callback")
                        .cookie(attempt.correlation())
                        .param("state", attempt.state())
                        .param("error", "access_denied"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string(HttpHeaders.LOCATION,
                        "http://localhost:5173/login?error=kakao_cancelled"))
                .andReturn();

        Cookie cleared = cancelled.getResponse().getCookie("FW_KAKAO_CORRELATION");
        assertThat(cleared).isNotNull();
        assertThat(cleared.getMaxAge()).isZero();

        mockMvc.perform(get("/api/v1/auth/kakao/callback")
                        .cookie(attempt.correlation())
                        .param("state", attempt.state())
                        .param("error", "access_denied"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string(HttpHeaders.LOCATION,
                        "http://localhost:5173/login?error=kakao_request_invalid"));
    }

    @Test
    void providerOutageReturnsRetryableLoginErrorWithoutCreatingSession() throws Exception {
        AuthorizationAttempt attempt = authorize("/dashboard");
        when(oidcClient.exchange(anyString(), anyString(), anyString()))
                .thenThrow(new KakaoLoginException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "KAKAO_TOKEN_UNAVAILABLE",
                        "카카오 인증 서버에 연결할 수 없습니다."));

        mockMvc.perform(get("/api/v1/auth/kakao/callback")
                        .cookie(attempt.correlation())
                        .param("state", attempt.state())
                        .param("code", "provider-outage"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string(HttpHeaders.LOCATION,
                        "http://localhost:5173/login?error=kakao_unavailable"));

        mockMvc.perform(get("/api/v1/auth/session").cookie(new Cookie("FW_SESSION", "not-created")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void exposesDisabledStatusContractWithoutSecrets() throws Exception {
        mockMvc.perform(get("/api/v1/auth/kakao/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.clientId").doesNotExist());
    }

    @Test
    void provisionsBySubjectWithoutEmailMergeOrAdminEscalation() {
        KakaoProfile profile = new KakaoProfile(
                "new-kakao-subject",
                null,
                null,
                "admin@finwatch.local");

        var created = provisioningService.provisionKakao(profile);
        var repeated = provisioningService.provisionKakao(profile);
        var localAdmin = userRepository.findByEmailIgnoreCase("admin@finwatch.local").orElseThrow();

        assertThat(created.getId()).isEqualTo(repeated.getId()).isNotEqualTo(localAdmin.getId());
        assertThat(created.getEmail()).isNull();
        assertThat(created.getDisplayName()).isEqualTo("FinWatch 사용자");
        assertThat(created.getRole()).isEqualTo(UserRole.USER);
    }

    private AuthorizationAttempt authorize(String returnTo) throws Exception {
        var result = mockMvc.perform(get("/api/v1/auth/kakao/authorize").param("returnTo", returnTo))
                .andExpect(status().isFound())
                .andReturn();
        URI location = URI.create(result.getResponse().getHeader(HttpHeaders.LOCATION));
        return new AuthorizationAttempt(
                result.getResponse().getCookie("FW_KAKAO_CORRELATION"),
                query(location.getRawQuery()).get("state"));
    }

    private Map<String, String> query(String rawQuery) {
        return Arrays.stream(rawQuery.split("&"))
                .map(part -> part.split("=", 2))
                .collect(Collectors.toMap(
                        pair -> decode(pair[0]),
                        pair -> pair.length == 2 ? decode(pair[1]) : ""));
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private record AuthorizationAttempt(Cookie correlation, String state) {
    }
}
