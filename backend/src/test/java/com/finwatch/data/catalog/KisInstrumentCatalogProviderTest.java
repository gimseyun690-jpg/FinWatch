package com.finwatch.data.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;

class KisInstrumentCatalogProviderTest {

    private static final Charset CP949 = Charset.forName("MS949");

    @Test
    void parsesCp949MasterFromZipWithoutExtractingFiles() throws Exception {
        int[] widths = {1, 1, 8, 1};
        var format = new KisInstrumentCatalogProvider.MasterFormat(
                "sample.mst", "KOSPI", 11, widths, 0, 1, 2, 3);
        String line = "005930   " + "KR7005930003" + "삼성전자" + "Y" + "1" + "19750611" + "0" + "\n";

        var instruments = KisInstrumentCatalogProvider.parseMaster(zip("sample.mst", line), format);

        assertThat(instruments).singleElement().satisfies(instrument -> {
            assertThat(instrument.symbol()).isEqualTo("005930");
            assertThat(instrument.name()).isEqualTo("삼성전자");
            assertThat(instrument.exchange()).isEqualTo("KOSPI");
            assertThat(instrument.instrumentType()).isEqualTo("ETF");
            assertThat(instrument.tradable()).isFalse();
            assertThat(instrument.status()).isEqualTo("HALTED");
            assertThat(instrument.listedAt()).isEqualTo(LocalDate.of(1975, 6, 11));
            assertThat(instrument.isin()).isEqualTo("KR7005930003");
        });
    }

    private byte[] zip(String entryName, String content) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry(entryName));
            zip.write(content.getBytes(CP949));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }
}
