package com.finwatch.disclosure.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class SecEdgarDisclosureProviderTest {

    @Test
    void parsesTickerCikMapAndColumnarRecentFilings() {
        Map<String, Object> tickers = Map.of(
                "0", Map.of("cik_str", 320193, "ticker", "AAPL", "title", "Apple Inc."),
                "1", Map.of("cik_str", 789019, "ticker", "MSFT", "title", "Microsoft Corp"),
                "2", Map.of("cik_str", 1067983, "ticker", "BRK-B", "title", "Berkshire Hathaway"));
        assertThat(SecEdgarDisclosureProvider.parseTickerMap(tickers))
                .containsEntry("AAPL", 320193L)
                .containsEntry("MSFT", 789019L)
                .containsEntry("BRK.B", 1067983L);

        Map<String, Object> recent = Map.of(
                "accessionNumber", List.of("0000320193-26-000077", "invalid"),
                "filingDate", List.of("2026-07-10", "2026-07-11"),
                "form", List.of("10-Q", "8-K"),
                "primaryDocument", List.of("aapl-20260627.htm", "bad/path.htm"),
                "primaryDocDescription", List.of("Quarterly report", "Invalid"));
        Map<String, Object> response = Map.of("filings", Map.of("recent", recent));

        var result = SecEdgarDisclosureProvider.normalizeSubmissions(
                response,
                320193L,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 7, 14));

        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.externalId()).isEqualTo("0000320193-26-000077");
            assertThat(item.disclosureType()).isEqualTo("10-Q");
            assertThat(item.source()).isEqualTo("SEC_EDGAR");
            assertThat(item.url()).isEqualTo(
                    "https://www.sec.gov/Archives/edgar/data/320193/000032019326000077/aapl-20260627.htm");
        });
    }
}
