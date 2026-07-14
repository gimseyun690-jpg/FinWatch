package com.finwatch.data.provider;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
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

@Component
public class FinnhubMarketDataClient {

    private static final Pattern SYMBOL_PATTERN = Pattern.compile("[A-Z0-9.:-]{1,20}");

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

    private BigDecimal decimal(Object value) {
        if (value == null || value.toString().isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException ignored) {
            return BigDecimal.ZERO;
        }
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
