package com.finwatch.news.content;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ArticleContentExtractor {

    public static final String EXTRACTOR_VERSION = "jsoup-main-text-v1";
    private static final String REMOVED_ELEMENTS = String.join(",",
            "script", "style", "noscript", "iframe", "svg", "canvas", "nav", "header", "footer",
            "form", "aside", "[hidden]", "[aria-hidden=true]", ".advertisement", ".advert", ".ad",
            ".ads", ".tracking", ".cookie-banner", ".newsletter-signup", "[data-ad]");

    public ExtractedContent extract(byte[] body, String contentType, String baseUri) {
        Document document = parse(body, contentType, baseUri);
        document.select(REMOVED_ELEMENTS).remove();

        Element root = firstNonNull(
                document.selectFirst("article"),
                document.selectFirst("main"),
                document.selectFirst("[role=main]"),
                document.body(),
                document);
        String text = normalizeWhitespace(root == null ? "" : root.text());
        if (text.isBlank()) {
            throw new NewsContentException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "ARTICLE_CONTENT_EMPTY",
                    "기사 본문에서 분석 가능한 텍스트를 찾지 못했습니다.");
        }
        String title = normalizeWhitespace(document.title());
        return new ExtractedContent(title, text, sha256(text));
    }

    private Document parse(byte[] body, String contentType, String baseUri) {
        String normalizedType = contentType.toLowerCase(Locale.ROOT);
        Parser parser = normalizedType.contains("xml") ? Parser.xmlParser() : Parser.htmlParser();
        try {
            return Jsoup.parse(new ByteArrayInputStream(body), null, baseUri, parser);
        } catch (Exception exception) {
            throw new NewsContentException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "ARTICLE_PARSE_FAILED",
                    "기사 본문 형식을 해석하지 못했습니다.",
                    exception);
        }
    }

    private Element firstNonNull(Element... elements) {
        for (Element element : elements) {
            if (element != null) {
                return element;
            }
        }
        return null;
    }

    private String normalizeWhitespace(String value) {
        return value.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is unavailable.", exception);
        }
    }

    public record ExtractedContent(String title, String text, String contentHash) {
    }
}
