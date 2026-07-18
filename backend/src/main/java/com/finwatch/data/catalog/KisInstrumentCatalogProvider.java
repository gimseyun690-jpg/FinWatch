package com.finwatch.data.catalog;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.DateTimeException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.finwatch.data.catalog.InstrumentCatalogResponses.CatalogSnapshot;
import com.finwatch.data.catalog.InstrumentCatalogResponses.ProviderInstrument;
import com.finwatch.data.provider.ProviderException;

@Component
public class KisInstrumentCatalogProvider implements InstrumentCatalogProvider {

    public static final String PROVIDER_ID = "KIS_MASTER";

    private static final Charset CP949 = Charset.forName("MS949");
    private static final Pattern DOMESTIC_SYMBOL = Pattern.compile("\\d{6}");
    private static final int MAX_ARCHIVE_BYTES = 8 * 1024 * 1024;
    private static final int MAX_ENTRY_BYTES = 24 * 1024 * 1024;

    private static final MasterFormat KOSPI = new MasterFormat(
            // The official parser subtracts 228 including the line terminator; after split, 227 fixed characters remain.
            "kospi_code.mst", "KOSPI", 227,
            new int[] {2, 1, 4, 4, 4, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 9, 5, 5, 1, 1, 1, 2, 1, 1, 1, 2, 2, 2, 3, 1, 3, 12, 12, 8, 15, 21, 2, 7, 1, 1, 1, 1, 1, 9, 9, 9, 5, 9, 8, 9, 3, 1, 1, 1},
            12, 34, 49, 54);
    private static final MasterFormat KOSDAQ = new MasterFormat(
            // The official parser subtracts 222 including the line terminator; after split, 221 fixed characters remain.
            "kosdaq_code.mst", "KOSDAQ", 221,
            new int[] {2, 1, 4, 4, 4, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 9, 5, 5, 1, 1, 1, 2, 1, 1, 1, 2, 2, 2, 3, 1, 3, 12, 12, 8, 15, 21, 2, 7, 1, 1, 1, 1, 9, 9, 9, 5, 9, 8, 9, 3, 1, 1, 1},
            8, 29, 44, 49);

    private final URI kospiUri;
    private final URI kosdaqUri;
    private final Duration readTimeout;
    private final BinaryDownloader downloader;

    @Autowired
    public KisInstrumentCatalogProvider(
            @Value("${app.data.kis.catalog.kospi-url}") String kospiUrl,
            @Value("${app.data.kis.catalog.kosdaq-url}") String kosdaqUrl,
            @Value("${app.data.connect-timeout:3s}") Duration connectTimeout,
            @Value("${app.data.read-timeout:10s}") Duration readTimeout) {
        this(
                URI.create(kospiUrl),
                URI.create(kosdaqUrl),
                readTimeout,
                httpDownloader(connectTimeout));
    }

    KisInstrumentCatalogProvider(URI kospiUri, URI kosdaqUri, Duration readTimeout, BinaryDownloader downloader) {
        this.kospiUri = kospiUri;
        this.kosdaqUri = kosdaqUri;
        this.readTimeout = readTimeout;
        this.downloader = downloader;
    }

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public CatalogSnapshot fetchCatalog() {
        try {
            byte[] kospiArchive = checkedDownload(kospiUri);
            byte[] kosdaqArchive = checkedDownload(kosdaqUri);
            List<ProviderInstrument> instruments = new ArrayList<>();
            instruments.addAll(parseMaster(kospiArchive, KOSPI));
            instruments.addAll(parseMaster(kosdaqArchive, KOSDAQ));
            Map<String, ProviderInstrument> unique = new LinkedHashMap<>();
            for (ProviderInstrument instrument : instruments) {
                unique.putIfAbsent(instrument.market() + ":" + instrument.symbol(), instrument);
            }
            return new CatalogSnapshot(PROVIDER_ID, "KRX", Instant.now(), List.copyOf(unique.values()));
        } catch (ProviderException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable("KIS 종목 마스터 다운로드가 중단되었습니다.", exception);
        } catch (IOException | RuntimeException exception) {
            throw unavailable("KIS 종목 마스터를 읽을 수 없습니다.", exception);
        }
    }

    private byte[] checkedDownload(URI uri) throws IOException, InterruptedException {
        byte[] archive = downloader.download(uri, readTimeout);
        if (archive == null || archive.length == 0) {
            throw new ProviderException(HttpStatus.BAD_GATEWAY, "KIS_MASTER_EMPTY", "KIS 종목 마스터가 비어 있습니다.");
        }
        if (archive.length > MAX_ARCHIVE_BYTES) {
            throw new ProviderException(HttpStatus.BAD_GATEWAY, "KIS_MASTER_TOO_LARGE", "KIS 종목 마스터 압축 파일이 제한을 초과했습니다.");
        }
        return archive;
    }

    static List<ProviderInstrument> parseMaster(byte[] archive, MasterFormat format) throws IOException {
        String content = readZipEntry(archive, format.entryName());
        List<ProviderInstrument> instruments = new ArrayList<>();
        for (String rawLine : content.split("\\R")) {
            String line = rawLine;
            if (line.length() <= format.tailLength() + 21) {
                continue;
            }
            int headLength = line.length() - format.tailLength();
            String head = line.substring(0, headLength);
            String tail = line.substring(headLength);
            String shortCode = head.substring(0, Math.min(9, head.length())).trim();
            if (!DOMESTIC_SYMBOL.matcher(shortCode).matches() || head.length() < 21) {
                continue;
            }
            String standardCode = head.substring(9, 21).trim();
            String name = head.substring(21).trim();
            if (name.isBlank()) {
                continue;
            }
            List<String> fields = splitFixed(tail, format.widths());
            boolean etp = positive(field(fields, format.etpIndex()));
            boolean halted = positive(field(fields, format.haltedIndex()));
            boolean preferred = positive(field(fields, format.preferredIndex()));
            String type = etp ? "ETF" : preferred ? "PREFERRED" : "STOCK";
            LocalDate listedAt = parseDate(field(fields, format.listedDateIndex()));
            String isin = standardCode.startsWith("KR") ? standardCode : null;
            instruments.add(new ProviderInstrument(
                    standardCode.isBlank() ? "KRX:" + shortCode : standardCode,
                    "KRX",
                    format.exchange(),
                    shortCode,
                    name,
                    null,
                    type,
                    "KRW",
                    isin,
                    listedAt,
                    true,
                    !halted,
                    halted ? "HALTED" : "LISTED"));
        }
        return List.copyOf(instruments);
    }

    private static String readZipEntry(byte[] archive, String expectedName) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive), CP949)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/');
                if (entry.isDirectory() || !name.substring(name.lastIndexOf('/') + 1).equalsIgnoreCase(expectedName)) {
                    continue;
                }
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int total = 0;
                int read;
                while ((read = zip.read(buffer)) >= 0) {
                    total += read;
                    if (total > MAX_ENTRY_BYTES) {
                        throw new IOException("KIS master entry exceeds the size limit.");
                    }
                    output.write(buffer, 0, read);
                }
                return output.toString(CP949);
            }
        }
        throw new IOException("KIS master entry not found: " + expectedName);
    }

    private static List<String> splitFixed(String value, int[] widths) {
        List<String> fields = new ArrayList<>(widths.length);
        int offset = 0;
        for (int width : widths) {
            int end = Math.min(value.length(), offset + width);
            fields.add(offset >= value.length() ? "" : value.substring(offset, end).trim());
            offset += width;
        }
        return fields;
    }

    private static String field(List<String> fields, int index) {
        return index >= 0 && index < fields.size() ? fields.get(index) : "";
    }

    private static boolean positive(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        return !normalized.isBlank() && !normalized.equals("N") && !normalized.equals("0");
    }

    private static LocalDate parseDate(String value) {
        if (value == null || !value.matches("\\d{8}")) {
            return null;
        }
        try {
            return LocalDate.of(
                    Integer.parseInt(value.substring(0, 4)),
                    Integer.parseInt(value.substring(4, 6)),
                    Integer.parseInt(value.substring(6, 8)));
        } catch (DateTimeException | NumberFormatException ignored) {
            return null;
        }
    }

    private ProviderException unavailable(String message, Exception cause) {
        return new ProviderException(HttpStatus.SERVICE_UNAVAILABLE, "KIS_MASTER_UNAVAILABLE", message, cause);
    }

    private static BinaryDownloader httpDownloader(Duration connectTimeout) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        return (uri, timeout) -> {
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(timeout).GET().build();
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("KIS master HTTP status " + response.statusCode());
            }
            return response.body();
        };
    }

    @FunctionalInterface
    interface BinaryDownloader {
        byte[] download(URI uri, Duration timeout) throws IOException, InterruptedException;
    }

    record MasterFormat(
            String entryName,
            String exchange,
            int tailLength,
            int[] widths,
            int etpIndex,
            int haltedIndex,
            int listedDateIndex,
            int preferredIndex) {
    }
}
