package com.finwatch.news.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import com.finwatch.news.repository.NewsArticleRepository;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("demo")
class NewsDetailApiIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired NewsArticleRepository repository;

    @Test void protectsContentAccordingToRightsProfile() throws Exception {
        var article = repository.findAll().stream().filter(item -> item.getRightsProfile() != null).findFirst().orElseThrow();
        boolean displayAllowed = "STORE_AND_DISPLAY".equals(article.getRightsProfile().name());
        var action = mockMvc.perform(get("/api/v1/news/{id}", article.getId()).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(article.getId()))
                .andExpect(jsonPath("$.data.contentDisplayAllowed").value(displayAllowed));
        if (!displayAllowed) action.andExpect(jsonPath("$.data.content").doesNotExist());
    }

    @Test void returnsCanonicalNotFoundError() throws Exception {
        mockMvc.perform(get("/api/v1/news/99999999").with(jwt()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NEWS_NOT_FOUND"));
    }
}
