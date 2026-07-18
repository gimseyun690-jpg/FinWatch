package com.finwatch.auth.kakao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import jakarta.servlet.http.Cookie;

@SpringBootTest(properties = {
        "app.auth.kakao.enabled=true",
        "app.auth.kakao.client-id=test-rest-api-key",
        "app.auth.kakao.client-secret=test-client-secret",
        "app.auth.kakao.redirect-uri=http://localhost:8080/api/v1/auth/kakao/callback",
        "app.auth.kakao.authorization-uri=https://kauth.kakao.com/oauth/authorize",
        "app.auth.kakao.attempt-ttl=100ms"
})
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class KakaoExpiredAttemptIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private KakaoOidcClient oidcClient;

    @Test
    void expiredAttemptFailsClosedBeforeAuthorizationCodeExchange() throws Exception {
        var authorize = mockMvc.perform(get("/api/v1/auth/kakao/authorize"))
                .andExpect(status().isFound())
                .andReturn();
        Cookie correlation = authorize.getResponse().getCookie("FW_KAKAO_CORRELATION");
        assertThat(correlation).isNotNull();
        URI location = URI.create(authorize.getResponse().getHeader(HttpHeaders.LOCATION));
        String state = query(location.getRawQuery()).get("state");

        Thread.sleep(300);

        mockMvc.perform(get("/api/v1/auth/kakao/callback")
                        .cookie(correlation)
                        .param("state", state)
                        .param("code", "must-not-be-exchanged"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string(HttpHeaders.LOCATION,
                        "http://localhost:5173/login?error=kakao_request_invalid"));
        verifyNoInteractions(oidcClient);
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
}
