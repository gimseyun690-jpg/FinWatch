package com.finwatch.data.catalog;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.finwatch.data.catalog.InstrumentCatalogResponses.CatalogSnapshot;
import com.finwatch.data.catalog.InstrumentCatalogResponses.ProviderInstrument;
import com.finwatch.data.provider.ProviderException;
import com.finwatch.data.provider.ProviderRestClientFactory;

@Component
public class FinnhubInstrumentCatalogProvider implements InstrumentCatalogProvider {

    public static final String PROVIDER_ID = "FINNHUB_SYMBOLS";

    private static final Pattern SYMBOL_PATTERN = Pattern.compile("[A-Z0-9.:-]{1,30}");

    private final RestClient restClient;
    private final String apiKey;

    @Autowired
    public FinnhubInstrumentCatalogProvider(
            @Value("${app.data.finnhub.api-key:}") String apiKey,
            @Value("${app.data.finnhub.base-url}") String baseUrl,
            @Value("${app.data.connect-timeout:3s}") Duration connectTimeout,
            @Value("${app.data.read-timeout:10s}") Duration readTimeout) {
        this(apiKey, ProviderRestClientFactory.create(baseUrl, connectTimeout, readTimeout));
    }

    FinnhubInstrumentCatalogProvider(String apiKey, RestClient restClient) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.restClient = restClient;
    }

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public CatalogSnapshot fetchCatalog() {
        if (apiKey.isBlank()) {
            throw new ProviderException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "FINNHUB_NOT_CONFIGURED",
                    "FINNHUB_API_KEY가 필요합니다.");
        }
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/stock/symbol").queryParam("exchange", "US").build())
                    .header("X-Finnhub-Token", apiKey)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(List.class);
            if (response == null) {
                throw new ProviderException(
                        HttpStatus.BAD_GATEWAY,
                        "FINNHUB_CATALOG_EMPTY_RESPONSE",
                        "Finnhub 종목 목록 응답이 비어 있습니다.");
            }
            return new CatalogSnapshot(PROVIDER_ID, "US", Instant.now(), normalize(response));
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            throw new ProviderException(
                    status == 429 ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_GATEWAY,
                    "FINNHUB_CATALOG_HTTP_" + status,
                    status == 429 ? "Finnhub 호출 한도를 초과했습니다." : "Finnhub 종목 목록 HTTP 오류: " + status,
                    exception);
        } catch (ResourceAccessException exception) {
            throw new ProviderException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "FINNHUB_CATALOG_UNAVAILABLE",
                    "Finnhub 종목 목록에 연결할 수 없습니다.",
                    exception);
        }
    }

    static List<ProviderInstrument> normalize(List<Map<String, Object>> response) {
        Map<String, ProviderInstrument> unique = new LinkedHashMap<>();
        for (Map<String, Object> raw : response) {
            String symbol = upper(raw.get("symbol"));
            String market = market(raw.get("mic"));
            String type = instrumentType(raw.get("type"));
            String name = truncate(string(raw.get("description")), 150);
            if (market == null || type == null || name.isBlank() || !SYMBOL_PATTERN.matcher(symbol).matches()) {
                continue;
            }
            String figi = string(raw.get("figi"));
            String providerInstrumentId = figi.isBlank() ? market + ":" + symbol : figi;
            String currency = upper(raw.get("currency"));
            if (currency.length() != 3) {
                currency = "USD";
            }
            ProviderInstrument instrument = new ProviderInstrument(
                    providerInstrumentId,
                    market,
                    market,
                    symbol,
                    name,
                    name,
                    type,
                    currency,
                    null,
                    null,
                    true,
                    true,
                    "LISTED");
            unique.putIfAbsent(market + ":" + symbol, instrument);
        }
        return List.copyOf(unique.values());
    }

    private static String market(Object value) {
        return switch (upper(value)) {
            case "XNAS", "NASDAQ" -> "NASDAQ";
            case "XNYS", "NYSE" -> "NYSE";
            default -> null;
        };
    }

    private static String instrumentType(Object value) {
        String type = upper(value);
        if (type.contains("ADR")) {
            return "ADR";
        }
        if (type.contains("ETF") || type.contains("ETP")) {
            return "ETF";
        }
        if (type.contains("PREFERRED")) {
            return "PREFERRED";
        }
        if (type.contains("COMMON STOCK") || type.equals("STOCK") || type.contains("REIT")) {
            return "STOCK";
        }
        return null;
    }

    private static String string(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private static String upper(Object value) {
        return string(value).toUpperCase(Locale.ROOT);
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
