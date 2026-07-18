package com.finwatch.disclosure.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;

class OpenDartDisclosureProviderTest {

    @Test
    void parsesListedStockToCorpCodeMappingFromOfficialZipShape() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <result>
                  <list><corp_code>00126380</corp_code><corp_name>삼성전자</corp_name><stock_code>005930</stock_code></list>
                  <list><corp_code>00000001</corp_code><corp_name>비상장</corp_name><stock_code> </stock_code></list>
                </result>
                """;

        var result = OpenDartDisclosureProvider.parseCorpCodeArchive(zip(xml));

        assertThat(result).containsExactly(Map.entry("005930", "00126380"));
    }

    @Test
    void normalizesDisclosureListAndNoDataStatus() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 7, 14);
        var response = Map.<String, Object>of(
                "status", "000",
                "message", "정상",
                "list", List.of(Map.of(
                        "rcept_no", "20260714000123",
                        "report_nm", "반기보고서 (2026.06)",
                        "rcept_dt", "20260714",
                        "corp_name", "삼성전자",
                        "flr_nm", "삼성전자",
                        "pblntf_ty", "A")));

        var result = OpenDartDisclosureProvider.normalizeList(response, from, to);

        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.externalId()).isEqualTo("20260714000123");
            assertThat(item.source()).isEqualTo("OPENDART");
            assertThat(item.url()).contains("rcpNo=20260714000123");
            assertThat(item.disclosureType()).isEqualTo("A");
        });
        assertThat(OpenDartDisclosureProvider.normalizeList(
                Map.of("status", "013", "message", "조회된 데이터가 없습니다."), from, to).items()).isEmpty();
    }

    private byte[] zip(String xml) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("CORPCODE.xml"));
            zip.write(xml.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }
}
