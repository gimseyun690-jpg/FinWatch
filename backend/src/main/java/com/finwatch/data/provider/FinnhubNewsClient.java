package com.finwatch.data.provider;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.finwatch.data.provider.ProviderResponses.CompanyNewsItem;
import com.finwatch.data.provider.ProviderResponses.CompanyNewsResult;

@Component
public class FinnhubNewsClient {

    private static final Pattern SYMBOL_PATTERN = Pattern.compile("[A-Z0-9.:-]{1,20}");
    private static final long MAX_DATE_RANGE_DAYS = 366;

    private final RestClient restClient;
    private final String apiKey;

    public FinnhubNewsClient(
            @Value("${app.data.finnhub.api-key:}") String apiKey,
            @Value("${app.data.finnhub.base-url}") String baseUrl,
            @Value("${app.data.connect-timeout:3s}") Duration connectTimeout,
            @Value("${app.data.read-timeout:10s}") Duration readTimeout) {
        this.apiKey = apiKey;
        this.restClient = ProviderRestClientFactory.create(baseUrl, connectTimeout, readTimeout);
    }

    public CompanyNewsResult companyNews(String symbol, LocalDate from, LocalDate to) {
        String normalizedSymbol = normalizeSymbol(symbol);
        validateDates(from, to);
        requireCredentials();

        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/company-news")
                            .queryParam("symbol", normalizedSymbol)
                            .queryParam("from", from)
                            .queryParam("to", to)
                            .build())
                    .header("X-Finnhub-Token", apiKey)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(List.class);

            if (response == null) {
                throw new ProviderException(
                        HttpStatus.BAD_GATEWAY,
                        "FINNHUB_EMPTY_RESPONSE",
                        "Finnhub가 빈 응답을 반환했습니다.");
            }
            return normalize(normalizedSymbol, from, to, response);
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            HttpStatus mapped = status == 429 ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_GATEWAY;
            throw new ProviderException(
                    mapped,
                    "FINNHUB_HTTP_" + status,
                    status == 429 ? "Finnhub 호출 한도를 초과했습니다." : "Finnhub HTTP 오류: " + status,
                    exception);
        } catch (ResourceAccessException exception) {
            throw new ProviderException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "FINNHUB_UNAVAILABLE",
                    "Finnhub에 연결할 수 없습니다.",
                    exception);
        }
    }

    private CompanyNewsResult normalize(
            String symbol,
            LocalDate from,
            LocalDate to,
            List<Map<String, Object>> response) {
        List<CompanyNewsItem> items = new ArrayList<>();
        for (Map<String, Object> raw : response) {
            String title = clean(string(raw, "headline"));
            String url = string(raw, "url").trim();
            if (title.isBlank() || url.isBlank()) {
                continue;
            }
            long epochSeconds = longValue(raw.get("datetime"));
            items.add(new CompanyNewsItem(
                    longValue(raw.get("id")),
                    symbol,
                    title,
                    clean(string(raw, "summary")),
                    url,
                    clean(string(raw, "source")),
                    clean(string(raw, "category")),
                    epochSeconds > 0 ? Instant.ofEpochSecond(epochSeconds) : null,
                    "finnhub"));
        }
        items.sort(Comparator.comparing(
                CompanyNewsItem::publishedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return new CompanyNewsResult(symbol, from, to, Instant.now(), List.copyOf(items));
    }

    private String normalizeSymbol(String symbol) {
        String normalized = symbol == null ? "" : symbol.trim().toUpperCase(java.util.Locale.ROOT);
        if (!SYMBOL_PATTERN.matcher(normalized).matches()) {
            throw new ProviderException(
                    HttpStatus.BAD_REQUEST,
                    "FINNHUB_SYMBOL_INVALID",
                    "Finnhub 종목 코드는 영문 대문자, 숫자, 점, 콜론 또는 하이픈 형식이어야 합니다.");
        }
        return normalized;
    }

    private void validateDates(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)) {
            throw new ProviderException(
                    HttpStatus.BAD_REQUEST,
                    "FINNHUB_DATE_RANGE_INVALID",
                    "Finnhub 뉴스 조회 기간이 올바르지 않습니다.");
        }
        if (Duration.between(from.atStartOfDay(), to.plusDays(1).atStartOfDay()).toDays() > MAX_DATE_RANGE_DAYS) {
            throw new ProviderException(
                    HttpStatus.BAD_REQUEST,
                    "FINNHUB_DATE_RANGE_TOO_LARGE",
                    "Finnhub 무료 등급 조회 기간은 한 번에 최대 1년입니다.");
        }
    }

    private void requireCredentials() {
        if (apiKey.isBlank()) {
            throw new ProviderException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "FINNHUB_NOT_CONFIGURED",
                    "FINNHUB_API_KEY가 필요합니다.");
        }
    }

    private String clean(String value) {
        return Jsoup.parse(value == null ? "" : value).text().trim();
    }

    private String string(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value == null ? "" : value.toString();
    }

    private long longValue(Object value) {
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }
}
