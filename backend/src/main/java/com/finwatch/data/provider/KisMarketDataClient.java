package com.finwatch.data.provider;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.finwatch.data.provider.ProviderResponses.Bar;
import com.finwatch.data.provider.ProviderResponses.BarSeries;
import com.finwatch.data.provider.ProviderResponses.Quote;

@Component
public class KisMarketDataClient {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final Duration TOKEN_EXPIRY_MARGIN = Duration.ofMinutes(5);
    private static final int DAILY_CHUNK_DAYS = 100;
    private static final int MAX_OVERSEAS_DAILY_PAGES = 30;
    private static final long REQUEST_INTERVAL_MILLIS = 1_050L;
    private static final int TRANSIENT_RETRY_ATTEMPTS = 3;

    private final RestClient restClient;
    private final String appKey;
    private final String appSecret;
    private final KisDomesticMarket domesticMarket;
    private final Object requestThrottle = new Object();
    private long nextRequestAtNanos;
    private volatile AccessToken cachedToken;

    public KisMarketDataClient(
            @Value("${app.data.kis.app-key:}") String appKey,
            @Value("${app.data.kis.app-secret:}") String appSecret,
            @Value("${app.data.kis.environment:paper}") String environment,
            @Value("${app.data.kis.prod-base-url}") String prodBaseUrl,
            @Value("${app.data.kis.paper-base-url}") String paperBaseUrl,
            @Value("${app.data.kis.domestic-market-code:UN}") String domesticMarketCode,
            @Value("${app.data.connect-timeout:3s}") Duration connectTimeout,
            @Value("${app.data.read-timeout:10s}") Duration readTimeout) {
        this.appKey = appKey;
        this.appSecret = appSecret;
        this.domesticMarket = KisDomesticMarket.fromCode(domesticMarketCode);
        String baseUrl = "prod".equalsIgnoreCase(environment) ? prodBaseUrl : paperBaseUrl;
        this.restClient = ProviderRestClientFactory.create(baseUrl, connectTimeout, readTimeout);
    }

    public KisDomesticMarket domesticMarket() {
        return domesticMarket;
    }

    public Quote getDomesticQuote(String symbol) {
        validateSymbol(symbol);
        Map<String, Object> response = get(
                "/uapi/domestic-stock/v1/quotations/inquire-price",
                "FHKST01010100",
                Map.of("FID_COND_MRKT_DIV_CODE", domesticMarket.restCode(), "FID_INPUT_ISCD", symbol));
        Map<String, Object> output = objectMap(response.get("output"));
        return new Quote(
                symbol,
                decimal(output, "stck_prpr"),
                decimal(output, "prdy_vrss"),
                decimal(output, "prdy_ctrt"),
                decimal(output, "acml_vol"),
                "KRW",
                domesticMarket.providerId(),
                Instant.now());
    }

    public BarSeries getDomesticDailyBars(String symbol, LocalDate from, LocalDate to) {
        validateSymbol(symbol);
        if (from == null || to == null || from.isAfter(to)) {
            throw new ProviderException(HttpStatus.BAD_REQUEST, "KIS_DATE_RANGE_INVALID", "KIS 일봉 조회 기간이 올바르지 않습니다.");
        }
        Map<LocalDate, Bar> barsByDate = new TreeMap<>();
        LocalDate chunkEnd = to;
        while (!chunkEnd.isBefore(from)) {
            LocalDate chunkStart = chunkEnd.minusDays(DAILY_CHUNK_DAYS - 1L);
            if (chunkStart.isBefore(from)) {
                chunkStart = from;
            }
            try {
                LocalDate requestStart = chunkStart;
                LocalDate requestEnd = chunkEnd;
                for (Bar bar : fetchBarsWithRetry(() -> fetchDomesticDailyBars(symbol, requestStart, requestEnd))) {
                    if (!bar.sessionDate().isBefore(chunkStart) && !bar.sessionDate().isAfter(chunkEnd)) {
                        barsByDate.put(bar.sessionDate(), bar);
                    }
                }
            } catch (ProviderException exception) {
                if (barsByDate.isEmpty()) {
                    throw exception;
                }
                break;
            }
            if (chunkStart.equals(from)) {
                break;
            }
            chunkEnd = chunkStart.minusDays(1);
        }
        return new BarSeries(
                symbol,
                "1D",
                domesticMarket.providerId(),
                Instant.now(),
                List.copyOf(barsByDate.values()));
    }

    public BarSeries getOverseasDailyBars(
            String market,
            String symbol,
            LocalDate from,
            LocalDate to) {
        String exchangeCode = overseasExchangeCode(market);
        validateOverseasSymbol(symbol);
        if (from == null || to == null || from.isAfter(to)) {
            throw new ProviderException(HttpStatus.BAD_REQUEST, "KIS_OVERSEAS_DATE_RANGE_INVALID", "KIS 해외 일봉 조회 기간이 올바르지 않습니다.");
        }

        Map<LocalDate, Bar> barsByDate = new TreeMap<>();
        LocalDate pageEnd = to;
        LocalDate previousOldest = null;
        for (int page = 0; page < MAX_OVERSEAS_DAILY_PAGES && !pageEnd.isBefore(from); page++) {
            List<Bar> pageItems;
            try {
                LocalDate requestEnd = pageEnd;
                pageItems = fetchBarsWithRetry(() -> fetchOverseasDailyBars(exchangeCode, symbol, requestEnd));
            } catch (ProviderException exception) {
                if (barsByDate.isEmpty()) {
                    throw exception;
                }
                break;
            }
            if (pageItems.isEmpty()) {
                break;
            }
            LocalDate oldest = pageItems.stream().map(Bar::sessionDate).min(LocalDate::compareTo).orElse(pageEnd);
            for (Bar bar : pageItems) {
                if (!bar.sessionDate().isBefore(from) && !bar.sessionDate().isAfter(to)) {
                    barsByDate.put(bar.sessionDate(), bar);
                }
            }
            if (!oldest.isAfter(from) || oldest.equals(previousOldest)) {
                break;
            }
            previousOldest = oldest;
            pageEnd = oldest.minusDays(1);
        }
        return new BarSeries(symbol, "1D", "kis-overseas", Instant.now(), List.copyOf(barsByDate.values()));
    }

    private List<Bar> fetchDomesticDailyBars(String symbol, LocalDate from, LocalDate to) {
        Map<String, Object> response = get(
                "/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice",
                "FHKST03010100",
                Map.of(
                        "FID_COND_MRKT_DIV_CODE", domesticMarket.restCode(),
                        "FID_INPUT_ISCD", symbol,
                        "FID_INPUT_DATE_1", from.format(BASIC_DATE),
                        "FID_INPUT_DATE_2", to.format(BASIC_DATE),
                        "FID_PERIOD_DIV_CODE", "D",
                        "FID_ORG_ADJ_PRC", "0"));

        List<Bar> bars = new ArrayList<>();
        Object rawItems = response.get("output2");
        if (rawItems instanceof List<?> items) {
            for (Object rawItem : items) {
                Map<String, Object> item = objectMap(rawItem);
                String date = string(item, "stck_bsop_date");
                if (date.isBlank()) {
                    continue;
                }
                bars.add(new Bar(
                        LocalDate.parse(date, BASIC_DATE),
                        decimal(item, "stck_oprc"),
                        decimal(item, "stck_hgpr"),
                        decimal(item, "stck_lwpr"),
                        decimal(item, "stck_clpr"),
                        decimal(item, "acml_vol")));
            }
        }
        bars.sort(java.util.Comparator.comparing(Bar::sessionDate));
        return List.copyOf(bars);
    }

    private List<Bar> fetchOverseasDailyBars(String exchangeCode, String symbol, LocalDate to) {
        Map<String, Object> response = get(
                "/uapi/overseas-price/v1/quotations/dailyprice",
                "HHDFS76240000",
                Map.of(
                        "AUTH", "",
                        "EXCD", exchangeCode,
                        "SYMB", symbol,
                        "GUBN", "0",
                        "BYMD", to.format(BASIC_DATE),
                        "MODP", "1"));

        List<Bar> bars = new ArrayList<>();
        Object rawItems = response.get("output2");
        if (rawItems instanceof List<?> items) {
            for (Object rawItem : items) {
                Map<String, Object> item = objectMap(rawItem);
                String date = string(item, "xymd");
                if (date.isBlank()) {
                    continue;
                }
                bars.add(new Bar(
                        LocalDate.parse(date, BASIC_DATE),
                        decimal(item, "open"),
                        decimal(item, "high"),
                        decimal(item, "low"),
                        decimal(item, "clos"),
                        decimal(item, "tvol")));
            }
        }
        bars.sort(java.util.Comparator.comparing(Bar::sessionDate));
        return List.copyOf(bars);
    }

    public Quote getOverseasQuote(String market, String symbol) {
        validateOverseasSymbol(symbol);
        String exchangeCode = overseasExchangeCode(market);
        Map<String, Object> response = get(
                "/uapi/overseas-price/v1/quotations/price_detail",
                "HHDFS76200200",
                Map.of(
                        "AUTH", "",
                        "EXCD", exchangeCode,
                        "SYMB", symbol));
        Map<String, Object> output = objectMap(response.get("output"));
        BigDecimal price = decimal(output, "last");
        if (price.signum() <= 0) {
            price = decimal(output, "base");
        }
        BigDecimal change = decimal(output, "diff");
        BigDecimal changeRate = decimal(output, "rate");
        BigDecimal volume = decimal(output, "tvol");
        return new Quote(
                symbol,
                price,
                change,
                changeRate,
                volume,
                "USD",
                "KIS_OVERSEAS",
                Instant.now());
    }

    private String overseasExchangeCode(String market) {
        return switch (market == null ? "" : market.trim().toUpperCase(Locale.ROOT)) {
            case "NASDAQ" -> "NAS";
            case "NYSE" -> "NYS";
            case "AMEX", "ARCA" -> "AMS";
            default -> "NAS";
        };
    }

    private void validateOverseasSymbol(String symbol) {
        if (symbol == null || !symbol.matches("[A-Za-z0-9._-]{1,30}")) {
            throw new ProviderException(HttpStatus.BAD_REQUEST, "KIS_OVERSEAS_SYMBOL_INVALID", "KIS 해외 종목 코드 형식이 올바르지 않습니다.");
        }
    }

    private List<Bar> fetchBarsWithRetry(Supplier<List<Bar>> request) {
        ProviderException lastFailure = null;
        for (int attempt = 1; attempt <= TRANSIENT_RETRY_ATTEMPTS; attempt++) {
            try {
                return request.get();
            } catch (ProviderException exception) {
                lastFailure = exception;
                if (!isTransient(exception) || attempt == TRANSIENT_RETRY_ATTEMPTS) {
                    throw exception;
                }
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(REQUEST_INTERVAL_MILLIS * attempt));
            }
        }
        throw lastFailure;
    }

    private boolean isTransient(ProviderException exception) {
        String code = exception.getCode();
        return exception.getStatus().is5xxServerError()
                && (code.contains("HTTP_429")
                        || code.contains("HTTP_500")
                        || code.contains("HTTP_502")
                        || code.contains("HTTP_503")
                        || code.contains("HTTP_504")
                        || code.contains("EGW00201")
                        || code.contains("UNAVAILABLE"));
    }

    private void awaitRequestSlot() {
        synchronized (requestThrottle) {
            long now = System.nanoTime();
            long waitNanos = nextRequestAtNanos - now;
            if (waitNanos > 0) {
                LockSupport.parkNanos(waitNanos);
            }
            nextRequestAtNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(REQUEST_INTERVAL_MILLIS);
        }
    }

    private Map<String, Object> get(String path, String trId, Map<String, String> query) {
        requireCredentials();
        awaitRequestSlot();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path(path);
                        query.forEach(uriBuilder::queryParam);
                        return uriBuilder.build();
                    })
                    .header("authorization", "Bearer " + accessToken())
                    .header("appkey", appKey)
                    .header("appsecret", appSecret)
                    .header("tr_id", trId)
                    .header("custtype", "P")
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(Map.class);
            if (response == null) {
                throw new ProviderException(HttpStatus.BAD_GATEWAY, "KIS_EMPTY_RESPONSE", "KIS가 빈 응답을 반환했습니다.");
            }
            ensureSuccess(response);
            return response;
        } catch (RestClientResponseException exception) {
            throw httpError("KIS", exception);
        } catch (ResourceAccessException exception) {
            throw new ProviderException(HttpStatus.SERVICE_UNAVAILABLE, "KIS_UNAVAILABLE", "KIS API에 연결할 수 없습니다.", exception);
        }
    }

    private String accessToken() {
        AccessToken current = cachedToken;
        if (current != null && current.expiresAt().isAfter(Instant.now().plus(TOKEN_EXPIRY_MARGIN))) {
            return current.value();
        }
        synchronized (this) {
            current = cachedToken;
            if (current != null && current.expiresAt().isAfter(Instant.now().plus(TOKEN_EXPIRY_MARGIN))) {
                return current.value();
            }
            cachedToken = issueToken();
            return cachedToken.value();
        }
    }

    private AccessToken issueToken() {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri("/oauth2/tokenP")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "grant_type", "client_credentials",
                            "appkey", appKey,
                            "appsecret", appSecret))
                    .retrieve()
                    .body(Map.class);
            if (response == null || string(response, "access_token").isBlank()) {
                throw new ProviderException(HttpStatus.BAD_GATEWAY, "KIS_TOKEN_INVALID", "KIS 접근 토큰 응답이 올바르지 않습니다.");
            }
            long expiresIn = longValue(response.get("expires_in"), 86_400L);
            return new AccessToken(string(response, "access_token"), Instant.now().plusSeconds(expiresIn));
        } catch (RestClientResponseException exception) {
            throw httpError("KIS_TOKEN", exception);
        }
    }

    private void ensureSuccess(Map<String, Object> response) {
        String resultCode = string(response, "rt_cd");
        if (!resultCode.isBlank() && !"0".equals(resultCode)) {
            String messageCode = safe(string(response, "msg_cd"));
            String message = safe(string(response, "msg1"));
            throw new ProviderException(
                    HttpStatus.BAD_GATEWAY,
                    "KIS_" + (messageCode.isBlank() ? "UPSTREAM_ERROR" : messageCode),
                    "KIS API 오류: " + (message.isBlank() ? "요청이 거부되었습니다." : message));
        }
    }

    private ProviderException httpError(String prefix, RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        HttpStatus mapped = status == 429 ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_GATEWAY;
        return new ProviderException(mapped, prefix + "_HTTP_" + status, prefix + " API HTTP 오류: " + status, exception);
    }

    private void requireCredentials() {
        if (appKey.isBlank() || appSecret.isBlank()) {
            throw new ProviderException(HttpStatus.SERVICE_UNAVAILABLE, "KIS_NOT_CONFIGURED", "KIS_APP_KEY와 KIS_APP_SECRET이 필요합니다.");
        }
    }

    private void validateSymbol(String symbol) {
        if (symbol == null || !symbol.matches("\\d{6}")) {
            throw new ProviderException(HttpStatus.BAD_REQUEST, "KIS_SYMBOL_INVALID", "KIS 국내 종목 코드는 숫자 6자리여야 합니다.");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> objectMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private BigDecimal decimal(Map<String, Object> values, String key) {
        String value = string(values, key).replace(",", "");
        return value.isBlank() ? BigDecimal.ZERO : new BigDecimal(value);
    }

    private String string(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value == null ? "" : value.toString();
    }

    private long longValue(Object value, long fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String safe(String value) {
        String sanitized = value == null ? "" : value.replaceAll("[\\r\\n\\t]+", " ").trim();
        return sanitized.length() <= 200 ? sanitized : sanitized.substring(0, 200);
    }

    private record AccessToken(String value, Instant expiresAt) {
    }
}
