package com.finwatch.fx.provider;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.finwatch.data.provider.ProviderException;
import com.finwatch.data.provider.ProviderRestClientFactory;

@Component
@Order(20)
public class FrankfurterFxRateProvider implements FxRateProvider {

    private final RestClient restClient;

    @Autowired
    public FrankfurterFxRateProvider(
            @Value("${app.data.fx.frankfurter-base-url:https://api.frankfurter.dev}") String baseUrl,
            @Value("${app.data.connect-timeout:3s}") Duration connectTimeout,
            @Value("${app.data.read-timeout:10s}") Duration readTimeout) {
        this(ProviderRestClientFactory.create(baseUrl, connectTimeout, readTimeout));
    }

    FrankfurterFxRateProvider(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public String providerId() {
        return "FRANKFURTER";
    }

    @Override
    public boolean supports(String baseCurrency, String quoteCurrency) {
        return "USD".equals(baseCurrency) && "KRW".equals(quoteCurrency);
    }

    @Override
    public FxQuote latest(String baseCurrency, String quoteCurrency) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.get()
                    .uri("/v2/rate/{base}/{quote}", baseCurrency, quoteCurrency)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(Map.class);
            return normalizeLatest(response, baseCurrency, quoteCurrency, Instant.now());
        } catch (RestClientResponseException exception) {
            throw mapHttp(exception);
        } catch (ResourceAccessException exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public List<FxBar> history(String baseCurrency, String quoteCurrency, Instant from, Instant to) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> response = restClient.get()
                    .uri(uri -> uri.path("/v2/rates")
                            .queryParam("base", baseCurrency)
                            .queryParam("quotes", quoteCurrency)
                            .queryParam("from", LocalDate.ofInstant(from, ZoneOffset.UTC))
                            .queryParam("to", LocalDate.ofInstant(to, ZoneOffset.UTC))
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(List.class);
            return normalizeHistory(response, baseCurrency, quoteCurrency);
        } catch (RestClientResponseException exception) {
            throw mapHttp(exception);
        } catch (ResourceAccessException exception) {
            throw unavailable(exception);
        }
    }

    static FxQuote normalizeLatest(
            Map<String, Object> response,
            String baseCurrency,
            String quoteCurrency,
            Instant fetchedAt) {
        validatePair(response, baseCurrency, quoteCurrency);
        BigDecimal rate = decimal(response.get("rate"));
        if (rate.signum() <= 0) {
            throw invalidResponse();
        }
        String date = string(response.get("date"));
        return new FxQuote(
                baseCurrency,
                quoteCurrency,
                rate,
                "REFERENCE",
                "FRANKFURTER:" + baseCurrency + "/" + quoteCurrency + ":" + date,
                fetchedAt,
                fetchedAt);
    }

    static List<FxBar> normalizeHistory(
            List<Map<String, Object>> response,
            String baseCurrency,
            String quoteCurrency) {
        if (response == null) {
            return List.of();
        }
        List<FxBar> bars = new ArrayList<>();
        for (Map<String, Object> item : response) {
            try {
                validatePair(item, baseCurrency, quoteCurrency);
                BigDecimal rate = decimal(item.get("rate"));
                LocalDate date = LocalDate.parse(string(item.get("date")));
                if (rate.signum() <= 0) {
                    continue;
                }
                String symbol = "FRANKFURTER:" + baseCurrency + "/" + quoteCurrency;
                bars.add(new FxBar(rate, rate, rate, rate, date.atStartOfDay(ZoneOffset.UTC).toInstant(), symbol));
            } catch (RuntimeException ignored) {
                // A malformed row must not invalidate the remaining verified reference rates.
            }
        }
        return List.copyOf(bars);
    }

    private static void validatePair(Map<String, Object> response, String baseCurrency, String quoteCurrency) {
        if (response == null
                || !baseCurrency.equalsIgnoreCase(string(response.get("base")))
                || !quoteCurrency.equalsIgnoreCase(string(response.get("quote")))) {
            throw invalidResponse();
        }
    }

    private ProviderException mapHttp(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        HttpStatus mapped = status == 429 ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.BAD_GATEWAY;
        return new ProviderException(mapped, "FRANKFURTER_HTTP_" + status,
                "Frankfurter exchange-rate HTTP error: " + status, exception);
    }

    private ProviderException unavailable(Exception exception) {
        return new ProviderException(HttpStatus.SERVICE_UNAVAILABLE, "FRANKFURTER_UNAVAILABLE",
                "Frankfurter exchange-rate API is unavailable.", exception);
    }

    private static ProviderException invalidResponse() {
        return new ProviderException(HttpStatus.BAD_GATEWAY, "FRANKFURTER_INVALID_RESPONSE",
                "Frankfurter returned an invalid exchange-rate response.");
    }

    private static String string(Object value) {
        return value == null ? "" : value.toString().trim().toUpperCase(Locale.ROOT);
    }

    private static BigDecimal decimal(Object value) {
        try {
            return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString());
        } catch (NumberFormatException ignored) {
            return BigDecimal.ZERO;
        }
    }
}
