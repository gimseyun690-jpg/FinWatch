package com.finwatch.data.provider;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.finwatch.data.provider.ProviderResponses.Quote;

@Component
public class YahooFinanceMarketDataClient {

    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    private static final LocalTime PRE_MARKET_OPEN = LocalTime.of(4, 0);
    private static final LocalTime REGULAR_OPEN = LocalTime.of(9, 30);
    private static final LocalTime REGULAR_CLOSE = LocalTime.of(16, 0);
    private static final LocalTime AFTER_HOURS_CLOSE = LocalTime.of(20, 0);

    private final RestClient restClient;

    public YahooFinanceMarketDataClient(
            @Value("${app.data.connect-timeout:3s}") Duration connectTimeout,
            @Value("${app.data.read-timeout:10s}") Duration readTimeout) {
        this.restClient = ProviderRestClientFactory.create(
                "https://query1.finance.yahoo.com",
                connectTimeout,
                readTimeout);
    }

    public Quote quote(String symbol) {
        String normalized = symbol == null ? "" : symbol.trim().toUpperCase(java.util.Locale.ROOT);
        if (normalized.isBlank()) {
            throw new ProviderException(HttpStatus.BAD_REQUEST, "YAHOO_SYMBOL_INVALID", "Yahoo Finance 종목 코드가 올바르지 않습니다.");
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/v8/finance/chart/" + normalized)
                            .queryParam("interval", "1m")
                            .queryParam("range", "1d")
                            .queryParam("includePrePost", "true")
                            .build())
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(Map.class);
            return parseQuote(normalized, response);
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            throw new ProviderException(
                    status == 429 ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_GATEWAY,
                    "YAHOO_HTTP_" + status,
                    status == 429 ? "Yahoo Finance 호출 한도를 초과했습니다." : "Yahoo Finance HTTP 오류: " + status,
                    exception);
        } catch (ResourceAccessException exception) {
            throw new ProviderException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "YAHOO_UNAVAILABLE",
                    "Yahoo Finance API에 연결할 수 없습니다.",
                    exception);
        }
    }

    static Quote parseQuote(String symbol, Map<String, Object> response) {
        if (response == null) {
            throw new ProviderException(HttpStatus.BAD_GATEWAY, "YAHOO_QUOTE_INVALID", "Yahoo Finance 응답이 비어 있습니다.");
        }
        Map<String, Object> chart = objectMap(response.get("chart"));
        List<?> results = list(chart.get("result"));
        if (results.isEmpty()) {
            throw new ProviderException(HttpStatus.BAD_GATEWAY, "YAHOO_QUOTE_INVALID", "Yahoo Finance 종목 데이터가 없습니다.");
        }
        Map<String, Object> result = objectMap(results.getFirst());
        Map<String, Object> meta = objectMap(result.get("meta"));

        BigDecimal regularPrice = decimal(meta.get("regularMarketPrice"));
        BigDecimal prevClose = decimal(meta.get("previousClose"));
        if (prevClose.signum() <= 0) {
            prevClose = decimal(meta.get("chartPreviousClose"));
        }
        BigDecimal preMarketPrice = decimal(meta.get("preMarketPrice"));
        BigDecimal postMarketPrice = decimal(meta.get("postMarketPrice"));

        LocalTime time = Instant.now().atZone(NEW_YORK).toLocalTime();
        BigDecimal price = regularPrice;
        BigDecimal referenceClose = prevClose;

        // In Pre-market (04:00 ~ 09:30 ET), prefer preMarketPrice if available
        if (!time.isBefore(PRE_MARKET_OPEN) && time.isBefore(REGULAR_OPEN)) {
            if (preMarketPrice.signum() > 0) {
                price = preMarketPrice;
            }
        }
        // In After-hours (16:00 ~ 20:00 ET), prefer postMarketPrice if available
        else if (!time.isBefore(REGULAR_CLOSE) && time.isBefore(AFTER_HOURS_CLOSE)) {
            if (postMarketPrice.signum() > 0) {
                price = postMarketPrice;
                if (regularPrice.signum() > 0) {
                    referenceClose = regularPrice;
                }
            }
        }

        if (price.signum() <= 0) {
            throw new ProviderException(HttpStatus.BAD_GATEWAY, "YAHOO_PRICE_INVALID", "유효한 시세를 가져오지 못했습니다.");
        }

        BigDecimal change = referenceClose.signum() > 0 ? price.subtract(referenceClose) : BigDecimal.ZERO;
        BigDecimal changeRate = referenceClose.signum() > 0
                ? change.multiply(BigDecimal.valueOf(100)).divide(referenceClose, 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal volume = decimal(meta.get("regularMarketVolume"));

        return new Quote(
                symbol,
                price,
                change,
                changeRate,
                volume,
                "USD",
                "YAHOO",
                Instant.now());
    }

    private static Map<String, Object> objectMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private static List<?> list(Object value) {
        return value instanceof List<?> items ? items : List.of();
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
}
