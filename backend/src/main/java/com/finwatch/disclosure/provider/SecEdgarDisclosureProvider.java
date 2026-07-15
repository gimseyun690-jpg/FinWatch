package com.finwatch.disclosure.provider;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.finwatch.data.provider.ProviderException;
import com.finwatch.data.provider.ProviderRestClientFactory;
import com.finwatch.disclosure.provider.DisclosureProviderResponses.DisclosureFetchResult;
import com.finwatch.disclosure.provider.DisclosureProviderResponses.DisclosureItem;
import com.finwatch.stock.domain.Stock;

@Component
public class SecEdgarDisclosureProvider implements DisclosureProvider {

    public static final String PROVIDER_ID = "SEC_EDGAR";

    private static final Pattern SYMBOL = Pattern.compile("[A-Z0-9.:-]{1,30}");
    private static final Pattern ACCESSION = Pattern.compile("\\d{10}-\\d{2}-\\d{6}");
    private static final Pattern PRIMARY_DOCUMENT = Pattern.compile("[A-Za-z0-9._-]{1,255}");
    private static final int MAX_ITEMS = 100;

    private final RestClient dataClient;
    private final RestClient webClient;
    private final String userAgent;
    private final Duration identifierCacheTtl;
    private final Object cacheLock = new Object();
    private volatile IdentifierCache identifierCache = new IdentifierCache(Map.of(), Instant.EPOCH);

    @Autowired
    public SecEdgarDisclosureProvider(
            @Value("${app.data.sec-edgar.user-agent:}") String userAgent,
            @Value("${app.data.sec-edgar.data-base-url:https://data.sec.gov}") String dataBaseUrl,
            @Value("${app.data.sec-edgar.web-base-url:https://www.sec.gov}") String webBaseUrl,
            @Value("${app.data.connect-timeout:3s}") Duration connectTimeout,
            @Value("${app.data.read-timeout:10s}") Duration readTimeout,
            @Value("${app.data.disclosures.identifier-cache-ttl:24h}") Duration identifierCacheTtl) {
        this(
                userAgent,
                ProviderRestClientFactory.create(dataBaseUrl, connectTimeout, readTimeout),
                ProviderRestClientFactory.create(webBaseUrl, connectTimeout, readTimeout),
                identifierCacheTtl);
    }

    SecEdgarDisclosureProvider(
            String userAgent,
            RestClient dataClient,
            RestClient webClient,
            Duration identifierCacheTtl) {
        this.userAgent = userAgent == null ? "" : userAgent.trim();
        this.dataClient = dataClient;
        this.webClient = webClient;
        this.identifierCacheTtl = identifierCacheTtl;
    }

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public boolean supports(Stock stock) {
        if (stock == null) {
            return false;
        }
        boolean us = "NASDAQ".equalsIgnoreCase(stock.getMarket()) || "NYSE".equalsIgnoreCase(stock.getMarket());
        return us && SYMBOL.matcher(stock.getSymbol()).matches();
    }

    @Override
    public DisclosureFetchResult fetch(Stock stock, LocalDate from, LocalDate to) {
        requireConfigured();
        if (!supports(stock)) {
            throw new ProviderException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "SEC_STOCK_UNSUPPORTED",
                    "SEC EDGAR가 지원하지 않는 종목입니다.");
        }
        try {
            Long cik = cikByTicker().get(stock.getSymbol().toUpperCase(Locale.ROOT));
            if (cik == null) {
                throw new ProviderException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "SEC_CIK_NOT_FOUND",
                        "SEC CIK를 찾을 수 없습니다: " + stock.getSymbol());
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> response = dataClient.get()
                    .uri("/submissions/CIK%010d.json".formatted(cik))
                    .header(HttpHeaders.USER_AGENT, userAgent)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(Map.class);
            return normalizeSubmissions(response, cik, from, to);
        } catch (RestClientResponseException exception) {
            throw httpError(exception);
        } catch (ResourceAccessException exception) {
            throw new ProviderException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "SEC_EDGAR_UNAVAILABLE",
                    "SEC EDGAR에 연결할 수 없습니다.",
                    exception);
        }
    }

    private Map<String, Long> cikByTicker() {
        IdentifierCache current = identifierCache;
        if (current.expiresAt().isAfter(Instant.now())) {
            return current.byTicker();
        }
        synchronized (cacheLock) {
            current = identifierCache;
            if (current.expiresAt().isAfter(Instant.now())) {
                return current.byTicker();
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClient.get()
                    .uri("/files/company_tickers.json")
                    .header(HttpHeaders.USER_AGENT, userAgent)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(Map.class);
            Map<String, Long> parsed = parseTickerMap(response);
            if (parsed.isEmpty()) {
                throw new ProviderException(
                        HttpStatus.BAD_GATEWAY,
                        "SEC_TICKER_MAP_EMPTY",
                        "SEC ticker-CIK 목록이 비어 있습니다.");
            }
            identifierCache = new IdentifierCache(parsed, Instant.now().plus(identifierCacheTtl));
            return parsed;
        }
    }

    static Map<String, Long> parseTickerMap(Map<String, Object> response) {
        if (response == null) {
            return Map.of();
        }
        Map<String, Long> result = new HashMap<>();
        for (Object value : response.values()) {
            if (!(value instanceof Map<?, ?> row)) {
                continue;
            }
            String ticker = string(row.get("ticker")).toUpperCase(Locale.ROOT);
            long cik = longValue(row.get("cik_str"));
            if (SYMBOL.matcher(ticker).matches() && cik > 0) {
                result.putIfAbsent(ticker, cik);
                result.putIfAbsent(ticker.replace('-', '.'), cik);
            }
        }
        return Map.copyOf(result);
    }

    static DisclosureFetchResult normalizeSubmissions(
            Map<String, Object> response,
            long cik,
            LocalDate from,
            LocalDate to) {
        if (response == null) {
            throw new ProviderException(HttpStatus.BAD_GATEWAY, "SEC_SUBMISSIONS_EMPTY", "SEC submissions 응답이 비어 있습니다.");
        }
        Object filingsValue = response.get("filings");
        if (!(filingsValue instanceof Map<?, ?> filings)
                || !(filings.get("recent") instanceof Map<?, ?> recent)) {
            throw new ProviderException(HttpStatus.BAD_GATEWAY, "SEC_SUBMISSIONS_INVALID", "SEC submissions 형식이 올바르지 않습니다.");
        }
        List<?> accessions = list(recent.get("accessionNumber"));
        List<?> filingDates = list(recent.get("filingDate"));
        List<?> forms = list(recent.get("form"));
        List<?> documents = list(recent.get("primaryDocument"));
        List<?> descriptions = list(recent.get("primaryDocDescription"));
        List<DisclosureItem> items = new ArrayList<>();
        int size = Math.min(accessions.size(), filingDates.size());
        for (int index = 0; index < size && items.size() < MAX_ITEMS; index++) {
            String accession = at(accessions, index);
            LocalDate filingDate = parseDate(at(filingDates, index));
            String form = at(forms, index);
            String primaryDocument = at(documents, index);
            if (!ACCESSION.matcher(accession).matches() || filingDate == null
                    || filingDate.isBefore(from) || filingDate.isAfter(to)) {
                continue;
            }
            String description = at(descriptions, index);
            String title = description.isBlank()
                    ? (form.isBlank() ? "SEC filing" : form + " filing")
                    : description;
            String accessionCompact = accession.replace("-", "");
            String url = PRIMARY_DOCUMENT.matcher(primaryDocument).matches()
                    ? "https://www.sec.gov/Archives/edgar/data/" + cik + "/" + accessionCompact + "/" + primaryDocument
                    : "https://www.sec.gov/Archives/edgar/data/" + cik + "/" + accessionCompact + "/" + accession + "-index.html";
            items.add(new DisclosureItem(
                    accession,
                    truncate(title, 500),
                    "SEC EDGAR",
                    url,
                    filingDate.atStartOfDay(ZoneOffset.UTC).toInstant(),
                    truncate(form, 80),
                    PROVIDER_ID));
        }
        items.sort(Comparator.comparing(DisclosureItem::publishedAt).reversed());
        return new DisclosureFetchResult(PROVIDER_ID, Instant.now(), items);
    }

    private void requireConfigured() {
        if (userAgent.isBlank()) {
            throw new ProviderException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "SEC_EDGAR_USER_AGENT_REQUIRED",
                    "SEC_EDGAR_USER_AGENT에 프로젝트명과 연락처를 설정해야 합니다.");
        }
    }

    private ProviderException httpError(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        return new ProviderException(
                status == 429 || status == 403 ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_GATEWAY,
                "SEC_EDGAR_HTTP_" + status,
                status == 429 || status == 403
                        ? "SEC EDGAR fair-access 제한으로 요청이 거절되었습니다."
                        : "SEC EDGAR HTTP 오류: " + status,
                exception);
    }

    private static List<?> list(Object value) {
        return value instanceof List<?> values ? values : List.of();
    }

    private static String at(List<?> values, int index) {
        return index < values.size() ? string(values.get(index)) : "";
    }

    private static LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String string(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private static long longValue(Object value) {
        try {
            return Long.parseLong(string(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private record IdentifierCache(Map<String, Long> byTicker, Instant expiresAt) {
    }
}
