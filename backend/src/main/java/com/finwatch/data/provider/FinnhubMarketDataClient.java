package com.finwatch.data.provider;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.finwatch.data.provider.ProviderResponses.Quote;
import com.finwatch.data.provider.ProviderResponses.Bar;
import com.finwatch.data.provider.ProviderResponses.BarSeries;

@Component
public class FinnhubMarketDataClient {

    private static final Pattern SYMBOL_PATTERN = Pattern.compile("[A-Z0-9.:-]{1,20}");
    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

    private final RestClient restClient;
    private final String apiKey;

    public FinnhubMarketDataClient(
            @Value("${app.data.finnhub.api-key:}") String apiKey,
            @Value("${app.data.finnhub.base-url}") String baseUrl,
            @Value("${app.data.connect-timeout:3s}") Duration connectTimeout,
            @Value("${app.data.read-timeout:10s}") Duration readTimeout) {
        this.apiKey = apiKey;
        this.restClient = ProviderRestClientFactory.create(baseUrl, connectTimeout, readTimeout);
    }

    public Quote quote(String symbol) {
        String normalized = normalizeSymbol(symbol);
        requireCredentials();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/quote").queryParam("symbol", normalized).build())
                    .header("X-Finnhub-Token", apiKey)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(Map.class);
            if (response == null || decimal(response.get("c")).signum() <= 0) {
                throw new ProviderException(
                        HttpStatus.BAD_GATEWAY,
                        "FINNHUB_QUOTE_INVALID",
                        "Finnhub가 유효한 현재가를 반환하지 않았습니다.");
            }
            long epochSeconds = longValue(response.get("t"));
            return new Quote(
                    normalized,
                    decimal(response.get("c")),
                    decimal(response.get("d")),
                    decimal(response.get("dp")),
                    BigDecimal.ZERO,
                    "USD",
                    "finnhub",
                    epochSeconds > 0 ? Instant.ofEpochSecond(epochSeconds) : Instant.now());
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            throw new ProviderException(
                    status == 429 ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_GATEWAY,
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

    public BarSeries dailyBars(String symbol, LocalDate from, LocalDate to) {
        String normalized = normalizeSymbol(symbol);
        requireCredentials();
        if (from == null || to == null || from.isAfter(to)) {
            throw new ProviderException(HttpStatus.BAD_REQUEST, "FINNHUB_DATE_RANGE_INVALID", "Finnhub 일봉 조회 기간이 올바르지 않습니다.");
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/stock/candle")
                            .queryParam("symbol", normalized)
                            .queryParam("resolution", "D")
                            .queryParam("from", from.atStartOfDay(NEW_YORK).toEpochSecond())
                            .queryParam("to", to.plusDays(1).atStartOfDay(NEW_YORK).minusSeconds(1).toEpochSecond())
                            .build())
                    .header("X-Finnhub-Token", apiKey)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(Map.class);
            return normalizeBars(normalized, response);
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            throw new ProviderException(
                    status == 429 ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_GATEWAY,
                    "FINNHUB_CANDLE_HTTP_" + status,
                    status == 429 ? "Finnhub 호출 한도를 초과했습니다." : "Finnhub 일봉 HTTP 오류: " + status,
                    exception);
        } catch (ResourceAccessException exception) {
            throw new ProviderException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "FINNHUB_CANDLE_UNAVAILABLE",
                    "Finnhub 일봉 API에 연결할 수 없습니다.",
                    exception);
        }
    }

    static BarSeries normalizeBars(String symbol, Map<String, Object> response) {
        if (response == null) {
            throw new ProviderException(HttpStatus.BAD_GATEWAY, "FINNHUB_CANDLE_INVALID", "Finnhub 일봉 응답이 비어 있습니다.");
        }
        String status = response.get("s") == null ? "" : response.get("s").toString();
        if ("no_data".equalsIgnoreCase(status)) {
            return new BarSeries(symbol, "1D", "finnhub", Instant.now(), List.of());
        }
        if (!"ok".equalsIgnoreCase(status)) {
            throw new ProviderException(HttpStatus.BAD_GATEWAY, "FINNHUB_CANDLE_INVALID", "Finnhub 일봉 권한 또는 응답 상태를 확인할 수 없습니다.");
        }
        List<?> times = list(response.get("t"));
        List<?> opens = list(response.get("o"));
        List<?> highs = list(response.get("h"));
        List<?> lows = list(response.get("l"));
        List<?> closes = list(response.get("c"));
        List<?> volumes = list(response.get("v"));
        int size = java.util.stream.IntStream.of(
                        times.size(), opens.size(), highs.size(), lows.size(), closes.size(), volumes.size())
                .min().orElse(0);
        List<Bar> bars = new ArrayList<>();
        for (int index = 0; index < size; index++) {
            long epochSeconds = longValue(times.get(index));
            BigDecimal close = decimal(closes.get(index));
            if (epochSeconds <= 0 || close.signum() <= 0) {
                continue;
            }
            bars.add(new Bar(
                    Instant.ofEpochSecond(epochSeconds).atZone(NEW_YORK).toLocalDate(),
                    decimal(opens.get(index)),
                    decimal(highs.get(index)),
                    decimal(lows.get(index)),
                    close,
                    decimal(volumes.get(index))));
        }
        bars.sort(java.util.Comparator.comparing(Bar::sessionDate));
        return new BarSeries(symbol, "1D", "finnhub", Instant.now(), List.copyOf(bars));
    }

    private String normalizeSymbol(String symbol) {
        String normalized = symbol == null ? "" : symbol.trim().toUpperCase(java.util.Locale.ROOT);
        if (!SYMBOL_PATTERN.matcher(normalized).matches()) {
            throw new ProviderException(
                    HttpStatus.BAD_REQUEST,
                    "FINNHUB_SYMBOL_INVALID",
                    "Finnhub 종목 코드 형식이 올바르지 않습니다.");
        }
        return normalized;
    }

    private void requireCredentials() {
        if (apiKey.isBlank()) {
            throw new ProviderException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "FINNHUB_NOT_CONFIGURED",
                    "FINNHUB_API_KEY가 필요합니다.");
        }
    }

    private static BigDecimal decimal(Object value) {
        if (value == null || value.toString().isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException ignored) {
            return BigDecimal.ZERO;
        }
    }

    private static long longValue(Object value) {
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private static List<?> list(Object value) {
        return value instanceof List<?> items ? items : List.of();
    }
}
