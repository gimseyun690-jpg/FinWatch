package com.finwatch.disclosure.provider;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.jsoup.Jsoup;
import org.jsoup.parser.Parser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
public class OpenDartDisclosureProvider implements DisclosureProvider {

    public static final String PROVIDER_ID = "OPENDART";

    private static final Pattern STOCK_CODE = Pattern.compile("\\d{6}");
    private static final Pattern RECEIPT_NUMBER = Pattern.compile("\\d{14}");
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final int MAX_ARCHIVE_BYTES = 8 * 1024 * 1024;
    private static final int MAX_XML_BYTES = 24 * 1024 * 1024;

    private final RestClient restClient;
    private final String apiKey;
    private final Duration identifierCacheTtl;
    private final Object cacheLock = new Object();
    private volatile IdentifierCache identifierCache = new IdentifierCache(Map.of(), Instant.EPOCH);

    @Autowired
    public OpenDartDisclosureProvider(
            @Value("${app.news-content.opendart-api-key:}") String apiKey,
            @Value("${app.data.opendart.base-url:https://opendart.fss.or.kr/api}") String baseUrl,
            @Value("${app.data.connect-timeout:3s}") Duration connectTimeout,
            @Value("${app.data.read-timeout:10s}") Duration readTimeout,
            @Value("${app.data.disclosures.identifier-cache-ttl:24h}") Duration identifierCacheTtl) {
        this(apiKey, ProviderRestClientFactory.create(baseUrl, connectTimeout, readTimeout), identifierCacheTtl);
    }

    OpenDartDisclosureProvider(String apiKey, RestClient restClient, Duration identifierCacheTtl) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.restClient = restClient;
        this.identifierCacheTtl = identifierCacheTtl;
    }

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public boolean supports(Stock stock) {
        return stock != null
                && "KRX".equalsIgnoreCase(stock.getMarket())
                && STOCK_CODE.matcher(stock.getSymbol()).matches();
    }

    @Override
    public DisclosureFetchResult fetch(Stock stock, LocalDate from, LocalDate to) {
        requireConfigured();
        if (!supports(stock)) {
            throw new ProviderException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "OPENDART_STOCK_UNSUPPORTED",
                    "Open DART가 지원하지 않는 종목입니다.");
        }
        try {
            String corpCode = corpCodes().get(stock.getSymbol());
            if (corpCode == null) {
                throw new ProviderException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "OPENDART_CORP_CODE_NOT_FOUND",
                        "Open DART 고유번호를 찾을 수 없습니다: " + stock.getSymbol());
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/list.json")
                            .queryParam("crtfc_key", apiKey)
                            .queryParam("corp_code", corpCode)
                            .queryParam("bgn_de", compact(from))
                            .queryParam("end_de", compact(to))
                            .queryParam("sort", "date")
                            .queryParam("sort_mth", "desc")
                            .queryParam("page_count", 100)
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(Map.class);
            return normalizeList(response, from, to);
        } catch (RestClientResponseException exception) {
            throw httpError(exception);
        } catch (ResourceAccessException exception) {
            throw new ProviderException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "OPENDART_UNAVAILABLE",
                    "Open DART에 연결할 수 없습니다.",
                    exception);
        }
    }

    private Map<String, String> corpCodes() {
        IdentifierCache current = identifierCache;
        if (current.expiresAt().isAfter(Instant.now())) {
            return current.byStockCode();
        }
        synchronized (cacheLock) {
            current = identifierCache;
            if (current.expiresAt().isAfter(Instant.now())) {
                return current.byStockCode();
            }
            byte[] archive = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/corpCode.xml")
                            .queryParam("crtfc_key", apiKey)
                            .build())
                    .accept(MediaType.APPLICATION_OCTET_STREAM)
                    .retrieve()
                    .body(byte[].class);
            if (archive == null || archive.length == 0 || archive.length > MAX_ARCHIVE_BYTES) {
                throw new ProviderException(
                        HttpStatus.BAD_GATEWAY,
                        "OPENDART_CORP_CODE_INVALID",
                        "Open DART 고유번호 파일이 비어 있거나 제한을 초과했습니다.");
            }
            try {
                Map<String, String> parsed = parseCorpCodeArchive(archive);
                if (parsed.isEmpty()) {
                    throw new ProviderException(
                            HttpStatus.BAD_GATEWAY,
                            "OPENDART_CORP_CODE_EMPTY",
                            "Open DART 고유번호 파일에 상장 종목이 없습니다.");
                }
                identifierCache = new IdentifierCache(parsed, Instant.now().plus(identifierCacheTtl));
                return parsed;
            } catch (IOException exception) {
                throw new ProviderException(
                        HttpStatus.BAD_GATEWAY,
                        "OPENDART_CORP_CODE_INVALID",
                        "Open DART 고유번호 압축 파일을 해석할 수 없습니다.",
                        exception);
            }
        }
    }

    static Map<String, String> parseCorpCodeArchive(byte[] archive) throws IOException {
        byte[] xml = null;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory() || !entry.getName().toLowerCase(Locale.ROOT).endsWith(".xml")) {
                    continue;
                }
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int total = 0;
                int read;
                while ((read = zip.read(buffer)) >= 0) {
                    total += read;
                    if (total > MAX_XML_BYTES) {
                        throw new IOException("Open DART corp code XML exceeds the size limit.");
                    }
                    output.write(buffer, 0, read);
                }
                xml = output.toByteArray();
                break;
            }
        }
        if (xml == null) {
            throw new IOException("Open DART corp code XML entry was not found.");
        }
        var document = Jsoup.parse(new String(xml, StandardCharsets.UTF_8), "", Parser.xmlParser());
        Map<String, String> result = new HashMap<>();
        document.select("list").forEach(element -> {
            String stockCode = element.selectFirst("stock_code") == null
                    ? "" : element.selectFirst("stock_code").text().trim();
            String corpCode = element.selectFirst("corp_code") == null
                    ? "" : element.selectFirst("corp_code").text().trim();
            if (STOCK_CODE.matcher(stockCode).matches() && corpCode.matches("\\d{8}")) {
                result.putIfAbsent(stockCode, corpCode);
            }
        });
        return Map.copyOf(result);
    }

    static DisclosureFetchResult normalizeList(Map<String, Object> response, LocalDate from, LocalDate to) {
        if (response == null) {
            throw new ProviderException(HttpStatus.BAD_GATEWAY, "OPENDART_EMPTY_RESPONSE", "Open DART 응답이 비어 있습니다.");
        }
        String status = string(response.get("status"));
        if ("013".equals(status)) {
            return new DisclosureFetchResult(PROVIDER_ID, Instant.now(), List.of());
        }
        if (!"000".equals(status)) {
            throw new ProviderException(
                    status.equals("020") ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_GATEWAY,
                    "OPENDART_STATUS_" + (status.isBlank() ? "UNKNOWN" : status),
                    "Open DART 오류: " + string(response.get("message")));
        }
        Object listValue = response.get("list");
        if (!(listValue instanceof List<?> rawItems)) {
            return new DisclosureFetchResult(PROVIDER_ID, Instant.now(), List.of());
        }
        List<DisclosureItem> items = new ArrayList<>();
        for (Object rawItem : rawItems) {
            if (!(rawItem instanceof Map<?, ?> raw)) {
                continue;
            }
            String receipt = string(raw.get("rcept_no"));
            String title = string(raw.get("report_nm"));
            LocalDate filedAt = parseDate(string(raw.get("rcept_dt")));
            if (!RECEIPT_NUMBER.matcher(receipt).matches() || title.isBlank() || filedAt == null
                    || filedAt.isBefore(from) || filedAt.isAfter(to)) {
                continue;
            }
            String filer = string(raw.get("flr_nm"));
            String company = string(raw.get("corp_name"));
            items.add(new DisclosureItem(
                    receipt,
                    truncate(title, 500),
                    truncate(filer.isBlank() ? company : filer, 150),
                    "https://dart.fss.or.kr/dsaf001/main.do?rcpNo=" + receipt,
                    filedAt.atStartOfDay(SEOUL).toInstant(),
                    truncate(string(raw.get("pblntf_ty")), 80),
                    PROVIDER_ID));
        }
        items.sort(Comparator.comparing(DisclosureItem::publishedAt).reversed());
        return new DisclosureFetchResult(PROVIDER_ID, Instant.now(), items);
    }

    private void requireConfigured() {
        if (apiKey.isBlank()) {
            throw new ProviderException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "OPENDART_API_KEY_REQUIRED",
                    "OPENDART_API_KEY가 필요합니다.");
        }
    }

    private ProviderException httpError(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        return new ProviderException(
                status == 429 ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_GATEWAY,
                "OPENDART_HTTP_" + status,
                status == 429 ? "Open DART 호출 한도를 초과했습니다." : "Open DART HTTP 오류: " + status,
                exception);
    }

    private static String compact(LocalDate date) {
        return date.toString().replace("-", "");
    }

    private static LocalDate parseDate(String value) {
        if (!value.matches("\\d{8}")) {
            return null;
        }
        try {
            return LocalDate.of(
                    Integer.parseInt(value.substring(0, 4)),
                    Integer.parseInt(value.substring(4, 6)),
                    Integer.parseInt(value.substring(6, 8)));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String string(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private record IdentifierCache(Map<String, String> byStockCode, Instant expiresAt) {
    }
}
