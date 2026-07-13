package com.finwatch.news.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.finwatch.news.repository.NewsArticleRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class AdminNewsContentApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NewsArticleRepository newsArticleRepository;

    @Test
    void metadataOnlySourceIsRejectedWithoutExternalRequest() throws Exception {
        Long newsId = newsArticleRepository.findByExternalId("demo-news-000660-metadata-only")
                .orElseThrow()
                .getId();

        mockMvc.perform(post("/api/v1/admin/news/{newsId}/content/refresh", newsId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("NEWS_CONTENT_UNAVAILABLE"));
    }

    @Test
    void userRoleCannotRefreshSourceContent() throws Exception {
        Long newsId = newsArticleRepository.findByExternalId("demo-news-000660-metadata-only")
                .orElseThrow()
                .getId();

        mockMvc.perform(post("/api/v1/admin/news/{newsId}/content/refresh", newsId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }
}
