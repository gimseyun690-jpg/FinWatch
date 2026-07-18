package com.finwatch.data.provider;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.finwatch.data.provider.ProviderResponses.NewsItem;
import com.finwatch.data.provider.ProviderResponses.NewsSearchResult;

@Component
public class NaverNewsSearchClient {

    private final RestClient restClient;
    private final String clientId;
    private final String clientSecret;

    public NaverNewsSearchClient(
            @Value("${app.data.naver.client-id:}") String clientId,
            @Value("${app.data.naver.client-secret:}") String clientSecret,
            @Value("${app.data.naver.base-url}") String baseUrl,
            @Value("${app.data.connect-timeout:3s}") Duration connectTimeout,
            @Value("${app.data.read-timeout:10s}") Duration readTimeout) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.restClient = ProviderRestClientFactory.create(baseUrl, connectTimeout, readTimeout);
    }

    public NewsSearchResult search(String query, int display) {
        if (query == null || query.isBlank()) {
            throw new ProviderException(HttpStatus.BAD_REQUEST, "NAVER_QUERY_REQUIRED", "뉴스 검색어가 필요합니다.");
        }
        if (display < 1 || display > 100) {
            throw new ProviderException(HttpStatus.BAD_REQUEST, "NAVER_DISPLAY_INVALID", "display는 1~100 범위여야 합니다.");
        }
        requireCredentials();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search/v1/news")
                            .queryParam("query", query.trim())
                            .queryParam("display", display)
                            .queryParam("start", 1)
                            .queryParam("sort", "date")
                            .queryParam("format", "json")
                            .build())
                    .header("X-NCP-APIGW-API-KEY-ID", clientId)
                    .header("X-NCP-APIGW-API-KEY", clientSecret)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(Map.class);
            if (response == null) {
                throw new ProviderException(HttpStatus.BAD_GATEWAY, "NAVER_EMPTY_RESPONSE", "NAVER API HUB가 빈 응답을 반환했습니다.");
            }
            return normalize(query.trim(), response);
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            HttpStatus mapped = status == 429 ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_GATEWAY;
            throw new ProviderException(mapped, "NAVER_HTTP_" + status, "NAVER API HUB HTTP 오류: " + status, exception);
        } catch (ResourceAccessException exception) {
            throw new ProviderException(HttpStatus.SERVICE_UNAVAILABLE, "NAVER_UNAVAILABLE", "NAVER API HUB에 연결할 수 없습니다.", exception);
        }
    }

    private NewsSearchResult normalize(String query, Map<String, Object> response) {
        List<NewsItem> items = new ArrayList<>();
        Object rawItems = response.get("items");
        if (rawItems instanceof List<?> list) {
            for (Object rawItem : list) {
                Map<String, Object> item = objectMap(rawItem);
                items.add(new NewsItem(
                        clean(string(item, "title")),
                        clean(string(item, "description")),
                        string(item, "originallink"),
                        string(item, "link"),
                        parsePublishedAt(string(item, "pubDate")),
                        "naver-api-hub"));
            }
        }
        return new NewsSearchResult(
                query,
                intValue(response.get("total")),
                intValue(response.get("start")),
                intValue(response.get("display")),
                Instant.now(),
                List.copyOf(items));
    }

    private void requireCredentials() {
        if (clientId.isBlank() || clientSecret.isBlank()) {
            throw new ProviderException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "NAVER_NOT_CONFIGURED",
                    "NAVER_API_HUB_CLIENT_ID와 NAVER_API_HUB_CLIENT_SECRET이 필요합니다.");
        }
    }

    private Instant parsePublishedAt(String value) {
        if (value.isBlank()) {
            return null;
        }
        try {
            return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private String clean(String value) {
        return Jsoup.parse(value).text();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> objectMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private String string(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value == null ? "" : value.toString();
    }

    private int intValue(Object value) {
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
