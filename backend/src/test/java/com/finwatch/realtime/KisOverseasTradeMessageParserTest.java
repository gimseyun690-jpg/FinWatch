package com.finwatch.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

class KisOverseasTradeMessageParserTest {

    @Test
    void parsesTheOfficialHdfscnt0TwentyFiveFieldContract() {
        Instant receivedAt = Instant.parse("2026-08-11T02:00:01Z");

        var trades = KisOverseasTradeMessageParser.parse(tick(
                "AAPL", "20260811", "110000", "213.40", "5", "1.35", "0.63", "123456"), receivedAt);

        assertThat(trades).singleElement().satisfies(trade -> {
            assertThat(trade.providerSymbol()).isEqualTo("AAPL");
            assertThat(trade.price()).isEqualByComparingTo("213.40");
            assertThat(trade.change()).isEqualByComparingTo("-1.35");
            assertThat(trade.changeRate()).isEqualByComparingTo("-0.63");
            assertThat(trade.volume()).isEqualByComparingTo("123456");
            assertThat(trade.asOf()).isEqualTo(Instant.parse("2026-08-11T02:00:00Z"));
        });
    }

    @Test
    void usesReceiptTimeOnlyWhenKisDoesNotSupplyAValidKoreaTimestamp() {
        Instant receivedAt = Instant.parse("2026-08-11T02:00:01Z");

        var trades = KisOverseasTradeMessageParser.parse(
                tick("AAPL", "", "", "213.40", "2", "1.35", "0.63", "123456"),
                receivedAt);

        assertThat(trades).singleElement().extracting(KisOverseasTradeMessageParser.KisOverseasTrade::asOf)
                .isEqualTo(receivedAt);
        assertThat(trades.getFirst().providerTimestamp()).isFalse();
    }

    @Test
    void ignoresAFrameForAnotherKisTransactionId() {
        assertThat(KisOverseasTradeMessageParser.parse("0|H0STCNT0|001|AAPL", Instant.now())).isEmpty();
    }

    private String tick(
            String symbol,
            String koreaDate,
            String koreaTime,
            String last,
            String sign,
            String difference,
            String rate,
            String volume) {
        String[] fields = new String[25];
        Arrays.fill(fields, "");
        fields[0] = symbol;
        fields[5] = koreaDate;
        fields[6] = koreaTime;
        fields[10] = last;
        fields[11] = sign;
        fields[12] = difference;
        fields[13] = rate;
        fields[19] = volume;
        return "0|HDFSCNT0|001|" + String.join("^", fields);
    }
}
