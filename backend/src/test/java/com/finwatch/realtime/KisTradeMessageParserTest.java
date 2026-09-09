package com.finwatch.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

class KisTradeMessageParserTest {

    @Test
    void parsesKisDomesticTradeTick() {
        String message = tick(
                "H0STCNT0", "005930", "101530", "85000", "2", "1200", "1.43", "12345678", "20260714");

        var quotes = KisTradeMessageParser.parse(message);

        assertThat(quotes).hasSize(1);
        LiveQuote quote = quotes.getFirst();
        assertThat(quote.market()).isEqualTo("KRX");
        assertThat(quote.symbol()).isEqualTo("005930");
        assertThat(quote.price()).isEqualByComparingTo("85000");
        assertThat(quote.change()).isEqualByComparingTo("1200");
        assertThat(quote.changeRate()).isEqualByComparingTo("1.43");
        assertThat(quote.volume()).isEqualByComparingTo("12345678");
        assertThat(quote.asOf()).isEqualTo(Instant.parse("2026-07-14T01:15:30Z"));
        assertThat(quote.source()).isEqualTo("KIS_KRX_WS");
        assertThat(quote.sessionStatus()).isEqualTo("REGULAR");
    }

    @Test
    void parsesNxtAndUnifiedDomesticTradeTicksWithoutDuplicatingTheInstrumentMarket() {
        var nxt = KisTradeMessageParser.parse(tick(
                "H0NXCNT0", "005930", "081530", "85100", "2", "1300", "1.55", "200", "20260714"));
        var unified = KisTradeMessageParser.parse(tick(
                "H0UNCNT0", "005930", "181530", "85200", "2", "1400", "1.67", "500", "20260714"));

        assertThat(nxt).singleElement().satisfies(quote -> {
            assertThat(quote.market()).isEqualTo("KRX");
            assertThat(quote.source()).isEqualTo("KIS_NXT_WS");
            assertThat(quote.price()).isEqualByComparingTo("85100");
        });
        assertThat(unified).singleElement().satisfies(quote -> {
            assertThat(quote.market()).isEqualTo("KRX");
            assertThat(quote.source()).isEqualTo("KIS_UNIFIED_WS");
            assertThat(quote.price()).isEqualByComparingTo("85200");
        });
    }

    @Test
    void appliesNegativeSignCodesAndRejectsOtherTransactionIds() {
        var quotes = KisTradeMessageParser.parse(tick(
                "H0STCNT0",
                "000660", "101531", "270000", "5", "3500", "1.28", "123", "20260714"));

        assertThat(quotes.getFirst().change()).isEqualByComparingTo("-3500");
        assertThat(quotes.getFirst().changeRate()).isEqualByComparingTo("-1.28");
        assertThat(KisTradeMessageParser.parse("0|OTHER|001|value")).isEmpty();
    }

    private String tick(
            String transactionId,
            String symbol,
            String time,
            String price,
            String sign,
            String change,
            String changeRate,
            String volume,
            String date) {
        String[] fields = new String[46];
        Arrays.fill(fields, "");
        fields[0] = symbol;
        fields[1] = time;
        fields[2] = price;
        fields[3] = sign;
        fields[4] = change;
        fields[5] = changeRate;
        fields[13] = volume;
        fields[33] = date;
        return "0|" + transactionId + "|001|" + String.join("^", fields);
    }
}
