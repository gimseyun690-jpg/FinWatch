package com.finwatch.news.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

class ArticleContentFetcherTest {

    private static final String ALLOWED_HOST = "filings.example.test";

    @Test
    void extractsMainTextAndRemovesScriptsStylesAndAds() throws Exception {
        String html = """
                <html><head><title>Quarterly filing</title><style>.x{}</style></head>
                <body><nav>menu</nav><main><h1>Results</h1><p>Revenue increased.</p>
                <div class='advertisement'>Buy now</div><script>steal()</script></main></body></html>
                """;
        RecordingTransport transport = new RecordingTransport(uri -> response(
                200,
                Map.of("Content-Type", List.of("text/html; charset=UTF-8"), "ETag", List.of("v1")),
                html));
        ArticleContentFetcher fetcher = fetcher(allowOnly(ALLOWED_HOST), publicResolver(), transport, 4096);

        FetchedArticleContent result = fetcher.fetch(URI.create(
                "https://filings.example.test/Archives/edgar/data/1/report.htm"));

        assertThat(result.title()).isEqualTo("Quarterly filing");
        assertThat(result.extractedText()).isEqualTo("Results Revenue increased.");
        assertThat(result.extractedText()).doesNotContain("Buy now", "steal", "menu");
        assertThat(result.contentHash()).hasSize(64);
        assertThat(result.etag()).isEqualTo("v1");
        assertThat(transport.calls()).isEqualTo(1);
    }

    @Test
    void unknownDomainIsMetadataOnlyAndDoesNotIssueRequest() throws Exception {
        RecordingTransport transport = new RecordingTransport(uri -> response(200, Map.of(), "unused"));
        ArticleContentFetcher fetcher = fetcher(
                uri -> SourcePolicyDecision.metadataOnly(uri.getHost()),
                publicResolver(),
                transport,
                4096);

        assertThatThrownBy(() -> fetcher.fetch(URI.create("https://unknown.example/story")))
                .isInstanceOf(NewsContentException.class)
                .extracting(exception -> ((NewsContentException) exception).getCode())
                .isEqualTo("NEWS_CONTENT_UNAVAILABLE");
        assertThat(transport.calls()).isZero();
    }

    @Test
    void unknownPublicDomainCanBeFetchedOnDemandForAiSummary() throws Exception {
        RecordingTransport transport = new RecordingTransport(uri -> response(
                200,
                Map.of("Content-Type", List.of("text/html")),
                "<html><head><title>Market news</title></head><body><article>Revenue increased.</article></body></html>"));
        ArticleContentFetcher fetcher = fetcher(
                uri -> SourcePolicyDecision.metadataOnly(uri.getHost()),
                publicResolver(),
                transport,
                4096);

        FetchedArticleContent result = fetcher.fetchForAiSummary(
                URI.create("https://news.example.test/story"));

        assertThat(result.extractedText()).isEqualTo("Revenue increased.");
        assertThat(result.contentSource()).isEqualTo(ContentSource.ON_DEMAND_ARTICLE);
        assertThat(result.rightsProfile()).isEqualTo(RightsProfile.STORE_FOR_AI);
        assertThat(transport.calls()).isEqualTo(1);
    }

    @Test
    void onDemandFetchStillBlocksPrivateAddressBeforeRequest() throws Exception {
        RecordingTransport transport = new RecordingTransport(uri -> response(200, Map.of(), "unused"));
        HostResolver privateResolver = host -> List.of(InetAddress.getByName("127.0.0.1"));
        ArticleContentFetcher fetcher = fetcher(
                uri -> SourcePolicyDecision.metadataOnly(uri.getHost()),
                privateResolver,
                transport,
                4096);

        assertThatThrownBy(() -> fetcher.fetchForAiSummary(URI.create("https://news.example.test/story")))
                .isInstanceOf(NewsContentException.class)
                .extracting(exception -> ((NewsContentException) exception).getCode())
                .isEqualTo("ARTICLE_PRIVATE_ADDRESS_BLOCKED");
        assertThat(transport.calls()).isZero();
    }

    @Test
    void privateAddressIsBlockedBeforeRequest() throws Exception {
        RecordingTransport transport = new RecordingTransport(uri -> response(200, Map.of(), "unused"));
        HostResolver privateResolver = host -> List.of(InetAddress.getByName("127.0.0.1"));
        ArticleContentFetcher fetcher = fetcher(allowOnly(ALLOWED_HOST), privateResolver, transport, 4096);

        assertThatThrownBy(() -> fetcher.fetch(URI.create("https://filings.example.test/report")))
                .isInstanceOf(NewsContentException.class)
                .extracting(exception -> ((NewsContentException) exception).getCode())
                .isEqualTo("ARTICLE_PRIVATE_ADDRESS_BLOCKED");
        assertThat(transport.calls()).isZero();
    }

    @Test
    void redirectTargetMustPassAllowlistAgain() throws Exception {
        RecordingTransport transport = new RecordingTransport(uri -> response(
                302,
                Map.of("Location", List.of("https://evil.example.test/private")),
                ""));
        ArticleContentFetcher fetcher = fetcher(allowOnly(ALLOWED_HOST), publicResolver(), transport, 4096);

        assertThatThrownBy(() -> fetcher.fetch(URI.create("https://filings.example.test/report")))
                .isInstanceOf(NewsContentException.class)
                .extracting(exception -> ((NewsContentException) exception).getCode())
                .isEqualTo("NEWS_CONTENT_UNAVAILABLE");
        assertThat(transport.calls()).isEqualTo(1);
    }

    @Test
    void oversizedResponseIsRejected() throws Exception {
        RecordingTransport transport = new RecordingTransport(uri -> response(
                200,
                Map.of("Content-Type", List.of("text/html")),
                "x".repeat(129)));
        ArticleContentFetcher fetcher = fetcher(allowOnly(ALLOWED_HOST), publicResolver(), transport, 128);

        assertThatThrownBy(() -> fetcher.fetch(URI.create("https://filings.example.test/report")))
                .isInstanceOf(NewsContentException.class)
                .extracting(exception -> ((NewsContentException) exception).getCode())
                .isEqualTo("ARTICLE_RESPONSE_TOO_LARGE");
    }

    @Test
    void upstreamRateLimitIsReturnedWithoutParsingBody() throws Exception {
        RecordingTransport transport = new RecordingTransport(uri -> response(429, Map.of(), "slow down"));
        ArticleContentFetcher fetcher = fetcher(allowOnly(ALLOWED_HOST), publicResolver(), transport, 4096);

        assertThatThrownBy(() -> fetcher.fetch(URI.create("https://filings.example.test/report")))
                .isInstanceOf(NewsContentException.class)
                .extracting(exception -> ((NewsContentException) exception).getCode())
                .isEqualTo("PROVIDER_RATE_LIMITED");
    }

    private ArticleContentFetcher fetcher(
            SourcePolicyResolver policyResolver,
            HostResolver hostResolver,
            ArticleHttpTransport transport,
            int maxResponseBytes) {
        return new ArticleContentFetcher(
                policyResolver,
                new UrlSafetyValidator(hostResolver),
                new DomainRateLimiter(),
                transport,
                new ArticleContentExtractor(),
                "FinWatch Test test@example.com",
                2,
                maxResponseBytes,
                0);
    }

    private SourcePolicyResolver allowOnly(String host) {
        return uri -> host.equals(uri.getHost())
                ? new SourcePolicyDecision(
                        host,
                        "/",
                        SourceFetchMode.ALLOWLIST_FETCH,
                        RightsProfile.STORE_FOR_AI,
                        ContentSource.OFFICIAL_DISCLOSURE,
                        0,
                        false,
                        "fixture",
                        LocalDate.of(2026, 7, 13))
                : SourcePolicyDecision.metadataOnly(uri.getHost());
    }

    private HostResolver publicResolver() throws Exception {
        InetAddress publicAddress = InetAddress.getByName("93.184.216.34");
        return host -> List.of(publicAddress);
    }

    private ArticleHttpResponse response(int status, Map<String, List<String>> headers, String body) {
        return new ArticleHttpResponse(status, headers, body.getBytes(StandardCharsets.UTF_8));
    }

    private static final class RecordingTransport implements ArticleHttpTransport {
        private final AtomicInteger calls = new AtomicInteger();
        private final Function<URI, ArticleHttpResponse> responseFunction;

        private RecordingTransport(Function<URI, ArticleHttpResponse> responseFunction) {
            this.responseFunction = responseFunction;
        }

        @Override
        public ArticleHttpResponse get(URI uri, String userAgent, Map<String, String> requestHeaders) {
            calls.incrementAndGet();
            return responseFunction.apply(uri);
        }

        private int calls() {
            return calls.get();
        }
    }
}
