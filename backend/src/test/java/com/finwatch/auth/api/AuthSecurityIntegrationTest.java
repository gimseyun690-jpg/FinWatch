package com.finwatch.auth.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import jakarta.servlet.http.Cookie;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class AuthSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void loginIssuesHttpOnlySessionAndEnforcesRoles() throws Exception {
        mockMvc.perform(get("/api/v1/stocks/000660"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        Cookie userSession = login("user@finwatch.local", "FinWatch123!", "USER");
        Cookie adminSession = login("admin@finwatch.local", "FinWatchAdmin123!", "ADMIN");

        mockMvc.perform(get("/api/v1/stocks/000660")
                        .cookie(userSession))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/ai/metrics")
                        .cookie(userSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(get("/api/v1/providers/kis/quotes/005930")
                        .cookie(userSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(get("/api/v1/admin/ai/metrics")
                        .cookie(adminSession))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/auth/session").cookie(userSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authenticated").value(true))
                .andExpect(jsonPath("$.data.user.displayName").value("user"))
                .andExpect(jsonPath("$.data.user.authProvider").value("LOCAL"))
                .andExpect(jsonPath("$.data.accessToken").doesNotExist());
    }

    @Test
    void invalidPasswordReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@finwatch.local\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void loginAcceptsConfiguredFrontendOrigin() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@finwatch.local\",\"password\":\"FinWatchAdmin123!\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
                .andExpect(jsonPath("$.data.user.role").value("ADMIN"))
                .andReturn();
        Cookie sessionCookie = result.getResponse().getCookie("FW_SESSION");
        org.assertj.core.api.Assertions.assertThat(sessionCookie).isNotNull();
        org.assertj.core.api.Assertions.assertThat(sessionCookie.isHttpOnly()).isTrue();
    }

    @Test
    void cookieAuthenticatedLogoutRequiresCsrfAndInvalidatesSession() throws Exception {
        MvcResult loginResult = loginResult("user@finwatch.local", "FinWatch123!", "USER");
        Cookie session = loginResult.getResponse().getCookie("FW_SESSION");
        Cookie csrfCookie = loginResult.getResponse().getCookie("XSRF-TOKEN");
        org.assertj.core.api.Assertions.assertThat(session).isNotNull();
        org.assertj.core.api.Assertions.assertThat(csrfCookie).isNotNull();

        mockMvc.perform(post("/api/v1/auth/logout").cookie(session))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(session, csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/auth/session").cookie(session))
                .andExpect(status().isUnauthorized());
    }

    private Cookie login(String email, String password, String expectedRole) throws Exception {
        MvcResult result = loginResult(email, password, expectedRole);
        Cookie cookie = result.getResponse().getCookie("FW_SESSION");
        org.assertj.core.api.Assertions.assertThat(cookie).isNotNull();
        org.assertj.core.api.Assertions.assertThat(cookie.isHttpOnly()).isTrue();
        return cookie;
    }

    private MvcResult loginResult(String email, String password, String expectedRole) throws Exception {
        String body = "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authenticated").value(true))
                .andExpect(jsonPath("$.data.user.role").value(expectedRole))
                .andExpect(jsonPath("$.data.accessToken").doesNotExist())
                .andReturn();
    }
}
