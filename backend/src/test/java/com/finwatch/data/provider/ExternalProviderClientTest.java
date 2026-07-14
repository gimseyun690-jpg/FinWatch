package com.finwatch.data.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.finwatch.data.provider.ProviderResponses.BarSeries;
import com.finwatch.data.provider.ProviderResponses.CompanyNewsResult;
import com.finwatch.data.provider.ProviderResponses.NewsSearchResult;
import com.finwatch.data.provider.ProviderResponses.Quote;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

class ExternalProviderClientTest {

    private HttpServer server;
    private String baseUrl;
    private final AtomicInteger tokenCalls = new AtomicInteger();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/oauth2/tokenP", exchange -> {
            tokenCalls.incrementAndGet();
            respond(exchange, 200, "{\"access_token\":\"fixture-token\",\"expires_in\":86400}");
        });
        server.createContext("/uapi/domestic-stock/v1/quotations/inquire-price", exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("authorization")).isEqualTo("Bearer fixture-token");
            assertThat(exchange.getRequestHeaders().getFirst("tr_id")).isEqualTo("FHKST01010100");
            respond(exchange, 200, """
                    {"rt_cd":"0","msg_cd":"MCA00000","output":{
                      "stck_prpr":"85000","prdy_vrss":"1200","prdy_ctrt":"1.43","acml_vol":"12345678"
                    }}
                    """);
        });
        server.createContext("/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice", exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("tr_id")).isEqualTo("FHKST03010100");
            respond(exchange, 200, """
                    {"rt_cd":"0","output2":[
                      {"stck_bsop_date":"20260712","stck_oprc":"84000","stck_hgpr":"86000","stck_lwpr":"83500","stck_clpr":"85000","acml_vol":"1000"},
                      {"stck_bsop_date":"20260711","stck_oprc":"83000","stck_hgpr":"84500","stck_lwpr":"82500","stck_clpr":"83800","acml_vol":"900"}
                    ]}
                    """);
        });
        server.createContext("/search/v1/news", exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("X-NCP-APIGW-API-KEY-ID")).isEqualTo("naver-id");
            assertThat(exchange.getRequestHeaders().getFirst("X-NCP-APIGW-API-KEY")).isEqualTo("naver-secret");
            respond(exchange, 200, """
                    {"total":1,"start":1,"display":1,"items":[{
                      "title":"<b>삼성전자</b> 실적 개선",
                      "originallink":"https://news.example.com/1",
                      "link":"https://n.news.naver.com/1",
                      "description":"반도체 <b>수요</b>가 증가했다.",
                      "pubDate":"Thu, 11 Jun 2026 18:34:00 +0900"
                    }]}
                    """);
        });
        server.createContext("/company-news", exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("X-Finnhub-Token")).isEqualTo("finnhub-key");
            String query = exchange.getRequestURI().getRawQuery();
            assertThat(query).contains("symbol=AAPL", "from=2026-07-01", "to=2026-07-13");
            respond(exchange, 200, """
                    [{
                      "category":"company news",
                      "datetime":1783900800,
                      "headline":"<b>Apple</b> expands AI infrastructure",
                      "id":987654,
                      "related":"AAPL",
                      "source":"Reuters",
                      "summary":"Apple announced a new <b>AI</b> investment.",
                      "url":"https://news.example.com/apple-ai"
                    }]
                    """);
        });
        server.createContext("/quote", exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("X-Finnhub-Token")).isEqualTo("finnhub-key");
            assertThat(exchange.getRequestURI().getRawQuery()).contains("symbol=AAPL");
            respond(exchange, 200, """
                    {"c":317.31,"d":2.4,"dp":0.7621,"h":318.0,"l":313.2,"o":314.5,"pc":314.91,"t":1784000000}
                    """);
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void kisNormalizesQuoteAndBarsAndReusesAccessToken() {
        KisMarketDataClient client = new KisMarketDataClient(
                "app-key", "app-secret", "paper", baseUrl, baseUrl, java.time.Duration.ofSeconds(1), java.time.Duration.ofSeconds(2));

        Quote quote = client.getDomesticQuote("005930");
        BarSeries bars = client.getDomesticDailyBars(
                "005930", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 13));

        assertThat(quote.price()).isEqualByComparingTo("85000");
        assertThat(quote.changeRate()).isEqualByComparingTo("1.43");
        assertThat(quote.providerId()).isEqualTo("kis");
        assertThat(bars.items()).hasSize(2);
        assertThat(bars.items().getFirst().sessionDate()).isEqualTo(LocalDate.of(2026, 7, 11));
        assertThat(tokenCalls).hasValue(1);
    }

    @Test
    void naverNormalizesMetadataAndRemovesHighlightTags() {
        NaverNewsSearchClient client = new NaverNewsSearchClient(
                "naver-id", "naver-secret", baseUrl, java.time.Duration.ofSeconds(1), java.time.Duration.ofSeconds(2));

        NewsSearchResult result = client.search("삼성전자", 1);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().title()).isEqualTo("삼성전자 실적 개선");
        assertThat(result.items().getFirst().description()).isEqualTo("반도체 수요가 증가했다.");
        assertThat(result.items().getFirst().providerId()).isEqualTo("naver-api-hub");
        assertThat(result.items().getFirst().publishedAt()).isNotNull();
    }

    @Test
    void finnhubUsesHeaderAuthenticationAndNormalizesCompanyNews() {
        FinnhubNewsClient client = new FinnhubNewsClient(
                "finnhub-key", baseUrl, java.time.Duration.ofSeconds(1), java.time.Duration.ofSeconds(2));

        CompanyNewsResult result = client.companyNews(
                "aapl", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 13));

        assertThat(result.symbol()).isEqualTo("AAPL");
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().externalId()).isEqualTo(987654L);
        assertThat(result.items().getFirst().title()).isEqualTo("Apple expands AI infrastructure");
        assertThat(result.items().getFirst().description()).isEqualTo("Apple announced a new AI investment.");
        assertThat(result.items().getFirst().providerId()).isEqualTo("finnhub");
        assertThat(result.items().getFirst().publishedAt()).isNotNull();
    }

    @Test
    void finnhubNormalizesCurrentQuote() {
        FinnhubMarketDataClient client = new FinnhubMarketDataClient(
                "finnhub-key", baseUrl, java.time.Duration.ofSeconds(1), java.time.Duration.ofSeconds(2));

        Quote quote = client.quote("aapl");

        assertThat(quote.symbol()).isEqualTo("AAPL");
        assertThat(quote.price()).isEqualByComparingTo("317.31");
        assertThat(quote.change()).isEqualByComparingTo("2.4");
        assertThat(quote.changeRate()).isEqualByComparingTo("0.7621");
        assertThat(quote.currency()).isEqualTo("USD");
        assertThat(quote.providerId()).isEqualTo("finnhub");
    }

    @Test
    void finnhubRejectsInvalidSymbolAndDateRangeBeforeCallingProvider() {
        FinnhubNewsClient client = new FinnhubNewsClient(
                "finnhub-key", baseUrl, java.time.Duration.ofSeconds(1), java.time.Duration.ofSeconds(2));

        assertThatThrownBy(() -> client.companyNews(
                "AAPL<script>", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 13)))
                .isInstanceOf(ProviderException.class)
                .extracting("code")
                .isEqualTo("FINNHUB_SYMBOL_INVALID");

        assertThatThrownBy(() -> client.companyNews(
                "AAPL", LocalDate.of(2025, 1, 1), LocalDate.of(2026, 7, 13)))
                .isInstanceOf(ProviderException.class)
                .extracting("code")
                .isEqualTo("FINNHUB_DATE_RANGE_TOO_LARGE");
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
