package com.finwatch.news.content;

import java.net.URI;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.springframework.stereotype.Component;

@Component
public class OfficialFeedParser {

    private static final int MAX_ENTRIES = 50;

    public List<FeedEntry> parse(byte[] body, URI feedUri) {
        Document document = Jsoup.parse(new String(body, java.nio.charset.StandardCharsets.UTF_8), "", Parser.xmlParser());
        List<FeedEntry> entries = new ArrayList<>();
        List<Element> elements = document.select("item, entry");
        for (Element element : elements.stream().limit(MAX_ENTRIES).toList()) {
            String title = cleanText(element.selectFirst("title"));
            URI url = resolveUrl(feedUri, linkValue(element));
            if (title.isBlank() || url == null) {
                continue;
            }
            String externalId = firstNonBlank(
                    cleanText(element.selectFirst("guid")),
                    cleanText(element.selectFirst("id")),
                    url.toString());
            String description = firstNonBlank(
                    cleanHtml(element.getElementsByTag("content:encoded").first()),
                    cleanHtml(element.selectFirst("content")),
                    cleanHtml(element.selectFirst("description")),
                    cleanHtml(element.selectFirst("summary")));
            Instant publishedAt = parseInstant(firstNonBlank(
                    cleanText(element.selectFirst("pubDate")),
                    cleanText(element.selectFirst("published")),
                    cleanText(element.selectFirst("updated"))));
            entries.add(new FeedEntry(externalId, title, url, description, publishedAt));
        }
        return List.copyOf(entries);
    }

    private String linkValue(Element element) {
        Element link = element.selectFirst("link[href]");
        if (link != null) {
            return link.attr("href");
        }
        return cleanText(element.selectFirst("link"));
    }

    private URI resolveUrl(URI feedUri, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            URI resolved = feedUri.resolve(value.trim());
            if (!"https".equalsIgnoreCase(resolved.getScheme()) || resolved.getHost() == null) {
                return null;
            }
            return resolved;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String cleanText(Element element) {
        return element == null ? "" : element.text().replaceAll("\\s+", " ").trim();
    }

    private String cleanHtml(Element element) {
        if (element == null) {
            return "";
        }
        Document fragment = Jsoup.parseBodyFragment(element.text());
        fragment.select("script, style, iframe, object, embed").remove();
        return fragment.text().replaceAll("\\s+", " ").trim();
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            try {
                return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            } catch (DateTimeParseException secondIgnored) {
                return null;
            }
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    public record FeedEntry(
            String externalId,
            String title,
            URI url,
            String description,
            Instant publishedAt) {
    }
}
