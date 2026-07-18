package com.finwatch.common.api;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.finwatch.auth.session.SessionCookieWriter;
import com.finwatch.auth.session.WebSessionService;

@WebMvcTest(HealthController.class)
class HealthControllerTest {

    @MockitoBean
    private WebSessionService webSessionService;

    @MockitoBean
    private SessionCookieWriter sessionCookieWriter;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsUpStatus() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-ID"))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.timestamp", not(blankOrNullString())));
    }

    @Test
    void preservesSafeRequestIdAndReplacesUnsafeValue() throws Exception {
        mockMvc.perform(get("/api/v1/health").header("X-Request-ID", "edge-12345678"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-ID", "edge-12345678"));

        mockMvc.perform(get("/api/v1/health").header("X-Request-ID", "unsafe value"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-ID", not("unsafe value")));
    }
}
