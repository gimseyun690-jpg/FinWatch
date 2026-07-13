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

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class AuthSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void loginIssuesJwtAndEnforcesRoles() throws Exception {
        mockMvc.perform(get("/api/v1/stocks/000660"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        String userToken = login("user@finwatch.local", "FinWatch123!", "USER");
        String adminToken = login("admin@finwatch.local", "FinWatchAdmin123!", "ADMIN");

        mockMvc.perform(get("/api/v1/stocks/000660")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/ai/metrics")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(get("/api/v1/providers/kis/quotes/005930")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(get("/api/v1/admin/ai/metrics")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk());
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
        mockMvc.perform(post("/api/v1/auth/login")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@finwatch.local\",\"password\":\"FinWatchAdmin123!\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
                .andExpect(jsonPath("$.data.user.role").value("ADMIN"));
    }

    private String login(String email, String password, String expectedRole) throws Exception {
        String body = objectMapper.writeValueAsString(new LoginBody(email, password));
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(3600))
                .andExpect(jsonPath("$.data.user.role").value(expectedRole))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray())
                .get("data")
                .get("accessToken")
                .asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record LoginBody(String email, String password) {
    }
}
