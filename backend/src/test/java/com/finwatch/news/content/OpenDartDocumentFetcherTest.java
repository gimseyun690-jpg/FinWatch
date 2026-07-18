package com.finwatch.news.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;

class OpenDartDocumentFetcherTest {

    @Test
    void extractsOfficialDisclosureFromZipFixture() throws Exception {
        byte[] archive = zipFixture("document.xml", """
                <DOCUMENT><TITLE>분기보고서</TITLE><BODY><P>매출액이 증가했습니다.</P>
                <script>ignore()</script><P>원가 변동은 위험 요인입니다.</P></BODY></DOCUMENT>
                """);
        RecordingTransport transport = new RecordingTransport(new ArticleHttpResponse(
                200,
                Map.of("Content-Type", List.of("application/zip")),
                archive));
        HostResolver resolver = host -> List.of(InetAddress.getByName("93.184.216.34"));
        OpenDartDocumentFetcher fetcher = new OpenDartDocumentFetcher(
                transport,
                new UrlSafetyValidator(resolver),
                new ArticleContentExtractor(),
                "test-key",
                10,
                100_000);

        FetchedArticleContent result = fetcher.fetch(
                "20260713000123",
                URI.create("https://dart.fss.or.kr/dsaf001/main.do?rcpNo=20260713000123"),
                openDartPolicy());

        assertThat(result.extractedText()).contains("매출액이 증가했습니다", "원가 변동은 위험 요인입니다");
        assertThat(result.extractedText()).doesNotContain("ignore()");
        assertThat(result.contentHash()).hasSize(64);
        assertThat(result.contentSource()).isEqualTo(ContentSource.OFFICIAL_DISCLOSURE);
        assertThat(transport.calls()).isEqualTo(1);
    }

    @Test
    void missingApiKeyStopsBeforeHttpRequest() throws Exception {
        RecordingTransport transport = new RecordingTransport(new ArticleHttpResponse(200, Map.of(), new byte[0]));
        HostResolver resolver = host -> List.of(InetAddress.getByName("93.184.216.34"));
        OpenDartDocumentFetcher fetcher = new OpenDartDocumentFetcher(
                transport,
                new UrlSafetyValidator(resolver),
                new ArticleContentExtractor(),
                "",
                10,
                100_000);

        assertThatThrownBy(() -> fetcher.fetch(
                "20260713000123",
                URI.create("https://dart.fss.or.kr/dsaf001/main.do?rcpNo=20260713000123"),
                openDartPolicy()))
                .isInstanceOf(NewsContentException.class)
                .extracting(exception -> ((NewsContentException) exception).getCode())
                .isEqualTo("OPENDART_API_KEY_REQUIRED");
        assertThat(transport.calls()).isZero();
    }

    private SourcePolicyDecision openDartPolicy() {
        return new SourcePolicyDecision(
                "opendart.fss.or.kr",
                "/api/document.xml",
                SourceFetchMode.API_CONTENT,
                RightsProfile.STORE_FOR_AI,
                ContentSource.OFFICIAL_DISCLOSURE,
                1000,
                false,
                "fixture",
                LocalDate.of(2026, 7, 13));
    }

    private byte[] zipFixture(String name, String content) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry(name));
            zip.write(content.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return output.toByteArray();
    }

    private static final class RecordingTransport implements ArticleHttpTransport {
        private final AtomicInteger calls = new AtomicInteger();
        private final ArticleHttpResponse response;

        private RecordingTransport(ArticleHttpResponse response) {
            this.response = response;
        }

        @Override
        public ArticleHttpResponse get(URI uri, String userAgent, Map<String, String> requestHeaders) {
            calls.incrementAndGet();
            return response;
        }

        private int calls() {
            return calls.get();
        }
    }
}
