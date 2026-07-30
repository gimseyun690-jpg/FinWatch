package com.finwatch.ai.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.finwatch.news.content.ContentSource;
import com.finwatch.news.content.FetchedArticleContent;
import com.finwatch.news.content.RightsProfile;
import com.finwatch.news.domain.NewsArticle;
import com.finwatch.news.repository.NewsArticleRepository;
import com.finwatch.stock.repository.StockRepository;

@SpringBootTest(properties = "app.ai.provider=mock")
@AutoConfigureMockMvc
@ActiveProfiles("demo")
@Transactional
class AiDisclosureSummaryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private NewsArticleRepository newsArticleRepository;

    @Test
    void authenticatedUserCanSummarizeStoredOfficialDisclosureAndReuseCache() throws Exception {
        var stock = stockRepository.findFirstBySymbolAndActiveTrue("000660").orElseThrow();
        String content = "반도체 생산 설비 투자와 자금 조달 계획, 투자 위험 및 일정 변경 가능성을 설명한 공식 공시 본문입니다.";
        URI url = URI.create("https://dart.fss.or.kr/dsaf001/main.do?rcpNo=20260715000123");
        NewsArticle disclosure = NewsArticle.createDisclosure(
                stock,
                "20260715000123",
                "신규시설투자등",
                "금융감독원 전자공시시스템",
                url.toString(),
                Instant.parse("2026-07-15T01:00:00Z"),
                "OPENDART",
                "B");
        disclosure.applyFetchedContent(new FetchedArticleContent(
                url,
                url,
                disclosure.getTitle(),
                content,
                "application/zip",
                Instant.now(),
                null,
                null,
                sha256(content),
                "opendart-zip-test",
                ContentSource.OFFICIAL_DISCLOSURE,
                RightsProfile.STORE_FOR_AI));
        disclosure = newsArticleRepository.saveAndFlush(disclosure);
        String body = "{\"disclosureId\":" + disclosure.getId() + "}";

        mockMvc.perform(post("/api/v1/ai/disclosure-summaries")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.newsId").value(disclosure.getId()))
                .andExpect(jsonPath("$.data.cacheHit").value(false))
                .andExpect(jsonPath("$.data.summary").isNotEmpty())
                .andExpect(jsonPath("$.data.analysisScope").value("FULL_PROCESSED_TEXT"));

        mockMvc.perform(post("/api/v1/ai/disclosure-summaries")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cacheHit").value(true))
                .andExpect(jsonPath("$.data.inputTokens").value(0))
                .andExpect(jsonPath("$.data.estimatedCost").value(0));
    }

    @Test
    void newsArticleIsRejectedByDisclosureEndpoint() throws Exception {
        Long newsId = newsArticleRepository.findAllByStockSymbolOrderByPublishedAtDesc("000660")
                .stream()
                .filter(article -> "NEWS".equals(article.getContentKind()))
                .findFirst()
                .orElseThrow()
                .getId();

        mockMvc.perform(post("/api/v1/ai/disclosure-summaries")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"disclosureId\":" + newsId + "}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DISCLOSURE_REQUIRED"));
    }

    private String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
