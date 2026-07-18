package com.finwatch.content.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.finwatch.ai.domain.AiAnalysis;
import com.finwatch.ai.repository.AiAnalysisRepository;
import com.finwatch.news.content.ContentSource;
import com.finwatch.news.content.FetchedArticleContent;
import com.finwatch.news.content.RightsProfile;
import com.finwatch.news.domain.NewsArticle;
import com.finwatch.news.repository.NewsArticleRepository;
import com.finwatch.stock.domain.Stock;
import com.finwatch.stock.repository.StockRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
@Transactional
class ContentFeedApiIntegrationTest {

    private static final String CUSTOM_RANGE = "period=CUSTOM&from=2026-07-01&to=2026-07-31";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NewsArticleRepository newsArticleRepository;

    @Autowired
    private AiAnalysisRepository aiAnalysisRepository;

    @Autowired
    private StockRepository stockRepository;

    @Test
    void requiresAuthenticationAndReturnsPageMetadataForAuthenticatedUsers() throws Exception {
        mockMvc.perform(get("/api/v1/content-feed"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/content-feed").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.sort").value("publishedAt,desc"));
    }

    @Test
    void filtersNewsByMarketSymbolSourceQueryAndMetadataState() throws Exception {
        mockMvc.perform(get("/api/v1/content-feed?" + CUSTOM_RANGE)
                        .with(jwt())
                        .queryParam("kind", "NEWS")
                        .queryParam("market", "KRX")
                        .queryParam("symbol", "000660")
                        .queryParam("source", "DEMO")
                        .queryParam("q", "FinWatch")
                        .queryParam("analysis", "METADATA_ONLY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].kind").value("NEWS"))
                .andExpect(jsonPath("$.data.items[0].market").value("KRX"))
                .andExpect(jsonPath("$.data.items[0].symbol").value("000660"))
                .andExpect(jsonPath("$.data.items[0].source").value("DEMO"))
                .andExpect(jsonPath("$.data.items[0].contentSource").value("METADATA_ONLY"))
                .andExpect(jsonPath("$.data.items[0].aiAnalysisAllowed").value(true))
                .andExpect(jsonPath("$.data.items[0].aiAnalysisStatus").value("AVAILABLE"));
    }

    @Test
    void filtersDisclosuresAndAiAllowedContent() throws Exception {
        Stock stock = stock();
        NewsArticle disclosure = newsArticleRepository.saveAndFlush(NewsArticle.createDisclosure(
                stock,
                "content-feed-disclosure-test",
                "Quarterly disclosure content test",
                "OpenDART",
                "https://dart.fss.or.kr/test",
                Instant.parse("2026-07-14T09:00:00Z"),
                "OPENDART",
                "PERIODIC_REPORT"));

        mockMvc.perform(get("/api/v1/content-feed?" + CUSTOM_RANGE)
                        .with(jwt())
                        .queryParam("kind", "DISCLOSURE")
                        .queryParam("market", "KRX")
                        .queryParam("symbol", "000660")
                        .queryParam("q", "Quarterly"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(disclosure.getId()))
                .andExpect(jsonPath("$.data.items[0].kind").value("DISCLOSURE"))
                .andExpect(jsonPath("$.data.items[0].disclosureType").value("PERIODIC_REPORT"));

        mockMvc.perform(get("/api/v1/content-feed?" + CUSTOM_RANGE)
                        .with(jwt())
                        .queryParam("kind", "NEWS")
                        .queryParam("market", "KRX")
                        .queryParam("symbol", "000660")
                        .queryParam("analysis", "AI_ALLOWED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(4))
                .andExpect(jsonPath("$.data.items[0].aiAnalysisAllowed").value(true))
                .andExpect(jsonPath("$.data.items[1].aiAnalysisAllowed").value(true))
                .andExpect(jsonPath("$.data.items[2].aiAnalysisAllowed").value(true))
                .andExpect(jsonPath("$.data.items[3].aiAnalysisAllowed").value(true));
    }

    @Test
    void returnsLatestCompletedAnalysisWithBoundedSummaryPreview() throws Exception {
        NewsArticle article = NewsArticle.createMetadata(
                stock(),
                "content-feed-preview-article",
                "Content feed preview isolation",
                "Content Feed Test",
                "https://example.com/content-feed-preview",
                Instant.parse("2026-07-15T09:00:00Z"),
                "TEST");
        article.applyFetchedContent(new FetchedArticleContent(
                URI.create("https://example.com/content-feed-preview"),
                URI.create("https://example.com/content-feed-preview"),
                article.getTitle(),
                "This source body is eligible for AI analysis.",
                "text/plain",
                Instant.parse("2026-07-15T09:01:00Z"),
                null,
                null,
                "content-feed-preview-content-hash",
                "content-feed-test-v1",
                ContentSource.PROVIDER_SUMMARY,
                RightsProfile.STORE_FOR_AI));
        article = newsArticleRepository.saveAndFlush(article);
        String longSummary = "Evidence summary ".repeat(20);
        aiAnalysisRepository.saveAndFlush(AiAnalysis.create(
                article,
                "content-feed-preview-v1",
                "test-model",
                longSummary,
                List.of("point"),
                List.of("positive"),
                List.of("risk"),
                List.of("SK hynix"),
                List.of("S1"),
                List.of("memory"),
                "POSITIVE",
                100,
                20,
                new BigDecimal("0.0001"),
                "content-feed-completed-cache",
                article.getContentHash(),
                "FULL_PROCESSED_TEXT",
                1000,
                800,
                1,
                Instant.parse("2026-07-15T10:00:00Z"),
                Instant.parse("2026-07-16T10:00:00Z")));

        mockMvc.perform(get("/api/v1/content-feed?" + CUSTOM_RANGE)
                        .with(jwt())
                        .queryParam("analysis", "AI_COMPLETED")
                        .queryParam("market", "KRX")
                        .queryParam("symbol", "000660")
                        .queryParam("q", "preview isolation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(article.getId()))
                .andExpect(jsonPath("$.data.items[0].aiAnalysisStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.items[0].summaryPreview").value(
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.startsWith("Evidence summary"),
                                org.hamcrest.Matchers.hasLength(180))));
    }

    @Test
    void usesIdAsStableTieBreakerForBothSortDirections() throws Exception {
        Stock stock = stock();
        Instant publishedAt = Instant.parse("2026-07-14T12:00:00Z");
        NewsArticle first = newsArticleRepository.saveAndFlush(NewsArticle.createMetadata(
                stock, "content-feed-tie-1", "Tie order alpha", "Test Publisher",
                "https://example.com/tie-1", publishedAt, "TEST"));
        NewsArticle second = newsArticleRepository.saveAndFlush(NewsArticle.createMetadata(
                stock, "content-feed-tie-2", "Tie order beta", "Test Publisher",
                "https://example.com/tie-2", publishedAt, "TEST"));

        mockMvc.perform(get("/api/v1/content-feed?" + CUSTOM_RANGE)
                        .with(jwt())
                        .queryParam("q", "Tie order")
                        .queryParam("size", "1")
                        .queryParam("sort", "publishedAt,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.items[0].id").value(second.getId()))
                .andExpect(jsonPath("$.data.hasNext").value(true));

        mockMvc.perform(get("/api/v1/content-feed?" + CUSTOM_RANGE)
                        .with(jwt())
                        .queryParam("q", "Tie order")
                        .queryParam("size", "1")
                        .queryParam("sort", "publishedAt,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(first.getId()));
    }

    @Test
    void rejectsInvalidFiltersPaginationAndSortAndReturnsNotFoundForUnknownStock() throws Exception {
        assertError("kind=VIDEO", "CONTENT_FILTER_INVALID", 400);
        assertError("symbol=000660", "CONTENT_FILTER_INVALID", 400);
        assertError("page=-1", "PAGE_INVALID", 400);
        assertError("size=51", "PAGE_INVALID", 400);
        assertError("page=NaN", "PAGE_INVALID", 400);
        assertError("sort=title,desc", "SORT_NOT_ALLOWED", 400);
        assertError("period=CUSTOM&from=2026-07-20&to=2026-07-01", "CONTENT_FILTER_INVALID", 400);
        assertError("market=KRX&symbol=999999", "STOCK_NOT_FOUND", 404);
    }

    @Test
    void preservesLegacyStockNewsArrayContract() throws Exception {
        mockMvc.perform(get("/api/v1/stocks/KRX/000660/news").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").isNumber());
    }

    private void assertError(String query, String code, int statusCode) throws Exception {
        mockMvc.perform(get("/api/v1/content-feed?" + query).with(jwt()))
                .andExpect(status().is(statusCode))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(code));
    }

    private Stock stock() {
        return stockRepository.findByMarketAndSymbolAndActiveTrue("KRX", "000660").orElseThrow();
    }
}
