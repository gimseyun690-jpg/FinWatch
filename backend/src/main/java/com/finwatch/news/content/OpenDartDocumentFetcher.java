package com.finwatch.news.content;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class OpenDartDocumentFetcher {

    public static final URI DOCUMENT_ENDPOINT = URI.create("https://opendart.fss.or.kr/api/document.xml");

    private final ArticleHttpTransport articleHttpTransport;
    private final UrlSafetyValidator urlSafetyValidator;
    private final ArticleContentExtractor contentExtractor;
    private final String apiKey;
    private final int maxArchiveEntries;
    private final int maxUncompressedBytes;

    public OpenDartDocumentFetcher(
            ArticleHttpTransport articleHttpTransport,
            UrlSafetyValidator urlSafetyValidator,
            ArticleContentExtractor contentExtractor,
            @Value("${app.news-content.opendart-api-key:}") String apiKey,
            @Value("${app.news-content.opendart-max-archive-entries:50}") int maxArchiveEntries,
            @Value("${app.news-content.opendart-max-uncompressed-bytes:4194304}") int maxUncompressedBytes) {
        this.articleHttpTransport = articleHttpTransport;
        this.urlSafetyValidator = urlSafetyValidator;
        this.contentExtractor = contentExtractor;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.maxArchiveEntries = maxArchiveEntries;
        this.maxUncompressedBytes = maxUncompressedBytes;
    }

    public FetchedArticleContent fetch(
            String receiptNumber,
            URI canonicalUrl,
            SourcePolicyDecision policy) {
        if (apiKey.isBlank()) {
            throw new NewsContentException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "OPENDART_API_KEY_REQUIRED",
                    "Open DART 공시 원문을 가져오려면 OPENDART_API_KEY가 필요합니다.");
        }
        if (receiptNumber == null || !receiptNumber.matches("\\d{14}")) {
            throw new NewsContentException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "OPENDART_RECEIPT_NUMBER_INVALID",
                    "Open DART 접수번호 형식이 올바르지 않습니다.");
        }
        if (policy.fetchMode() != SourceFetchMode.API_CONTENT
                || policy.contentSource() != ContentSource.OFFICIAL_DISCLOSURE) {
            throw NewsContentException.unavailable();
        }

        URI requestUri = buildRequestUri(receiptNumber);
        urlSafetyValidator.validate(requestUri);
        ArticleHttpResponse response = articleHttpTransport.get(
                requestUri,
                "FinWatch/0.1",
                Map.of("Accept", "application/zip,application/octet-stream"));
        if (response.statusCode() == 429) {
            throw new NewsContentException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "PROVIDER_RATE_LIMITED",
                    "Open DART 호출 한도를 초과했습니다.");
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new NewsContentException(
                    HttpStatus.BAD_GATEWAY,
                    "OPENDART_DOCUMENT_FETCH_FAILED",
                    "Open DART 공시 원문을 가져오지 못했습니다.");
        }

        ExtractedArchive extracted = extractArchive(response.body(), canonicalUrl);
        return new FetchedArticleContent(
                canonicalUrl,
                canonicalUrl,
                extracted.title(),
                extracted.text(),
                "application/zip",
                Instant.now(),
                response.firstHeader("ETag").orElse(null),
                response.firstHeader("Last-Modified").orElse(null),
                sha256(extracted.text()),
                "opendart-zip-" + ArticleContentExtractor.EXTRACTOR_VERSION,
                policy.contentSource(),
                policy.rightsProfile());
    }

    private URI buildRequestUri(String receiptNumber) {
        String query = "crtfc_key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8)
                + "&rcept_no=" + URLEncoder.encode(receiptNumber, StandardCharsets.UTF_8);
        return URI.create(DOCUMENT_ENDPOINT + "?" + query);
    }

    private ExtractedArchive extractArchive(byte[] archive, URI canonicalUrl) {
        StringBuilder combined = new StringBuilder();
        String title = "";
        AtomicInteger totalBytes = new AtomicInteger();
        int entries = 0;

        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory() || !isTextEntry(entry.getName())) {
                    continue;
                }
                entries++;
                if (entries > maxArchiveEntries) {
                    throw invalidArchive("Open DART 압축 파일의 문서 수가 허용 범위를 초과했습니다.");
                }
                byte[] entryBody = readEntry(zip, totalBytes);
                try {
                    ArticleContentExtractor.ExtractedContent extracted = extractEntry(
                            entry.getName(), entryBody, canonicalUrl);
                    if (title.isBlank() && !extracted.title().isBlank()) {
                        title = extracted.title();
                    }
                    if (!combined.isEmpty()) {
                        combined.append('\n');
                    }
                    combined.append(extracted.text());
                } catch (NewsContentException exception) {
                    if (!"ARTICLE_CONTENT_EMPTY".equals(exception.getCode())) {
                        throw exception;
                    }
                }
            }
        } catch (IOException exception) {
            throw new NewsContentException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "OPENDART_DOCUMENT_INVALID",
                    "Open DART 공시 원문 압축 파일을 해석하지 못했습니다.",
                    exception);
        }
        String text = combined.toString().replaceAll("\\s+", " ").trim();
        if (entries == 0 || text.isBlank()) {
            throw invalidArchive("Open DART 공시 원문에서 분석 가능한 텍스트를 찾지 못했습니다.");
        }
        return new ExtractedArchive(title, text);
    }

    private ArticleContentExtractor.ExtractedContent extractEntry(
            String entryName,
            byte[] entryBody,
            URI canonicalUrl) {
        if (!entryName.toLowerCase(Locale.ROOT).endsWith(".xml")) {
            return contentExtractor.extract(entryBody, "text/html", canonicalUrl.toString());
        }
        Document document = Jsoup.parse(
                new String(entryBody, StandardCharsets.UTF_8),
                canonicalUrl.toString(),
                Parser.xmlParser());
        document.select("script,style,noscript").remove();
        String text = document.text().replaceAll("\\s+", " ").trim();
        if (text.isBlank()) {
            throw new NewsContentException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "ARTICLE_CONTENT_EMPTY",
                    "공시 문서에서 분석 가능한 텍스트를 찾지 못했습니다.");
        }
        String title = document.select("TITLE, title").stream()
                .findFirst()
                .map(element -> element.text().trim())
                .orElse("");
        return new ArticleContentExtractor.ExtractedContent(title, text, sha256(text));
    }

    private byte[] readEntry(ZipInputStream zip, AtomicInteger totalBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8_192];
        int read;
        while ((read = zip.read(buffer)) != -1) {
            if (totalBytes.addAndGet(read) > maxUncompressedBytes) {
                throw invalidArchive("Open DART 압축 해제 결과가 허용된 크기를 초과했습니다.");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private boolean isTextEntry(String name) {
        String lowerName = name.toLowerCase(Locale.ROOT);
        return lowerName.endsWith(".xml") || lowerName.endsWith(".html")
                || lowerName.endsWith(".htm") || lowerName.endsWith(".txt");
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is unavailable.", exception);
        }
    }

    private NewsContentException invalidArchive(String message) {
        return new NewsContentException(HttpStatus.UNPROCESSABLE_ENTITY, "OPENDART_DOCUMENT_INVALID", message);
    }

    private record ExtractedArchive(String title, String text) {
    }
}
