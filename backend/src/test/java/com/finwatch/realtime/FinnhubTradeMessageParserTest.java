package com.finwatch.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

class FinnhubTradeMessageParserTest {

    private final FinnhubTradeMessageParser parser = new FinnhubTradeMessageParser(new ObjectMapper());

    @Test
    void parsesFinnhubTradeFrames() {
        var trades = parser.parse("""
                {"type":"trade","data":[
                  {"s":"AAPL","p":317.31,"t":1784000000123,"v":12},
                  {"s":"NVDA","p":164.72,"t":1784000001123,"v":3}
                ]}
                """);

        assertThat(trades).hasSize(2);
        assertThat(trades.getFirst().symbol()).isEqualTo("AAPL");
        assertThat(trades.getFirst().price()).isEqualByComparingTo("317.31");
        assertThat(trades.getFirst().volume()).isEqualByComparingTo("12");
        assertThat(trades.getFirst().asOf()).isEqualTo(Instant.ofEpochMilli(1784000000123L));
        assertThat(trades.getFirst().providerTimestamp()).isTrue();
    }

    @Test
    void ignoresPingAndMalformedFrames() {
        assertThat(parser.parse("{\"type\":\"ping\"}")).isEmpty();
        assertThat(parser.parse("not-json")).isEmpty();
    }

    @Test
    void marksFramesWithoutProviderTimeSoTheyCannotBecomeLiveFxQuotes() {
        var trades = parser.parse("""
                {"type":"trade","data":[{"s":"OANDA:USD_KRW","p":1382.5,"v":1}]}
                """);

        assertThat(trades).singleElement()
                .extracting(FinnhubTradeMessageParser.FinnhubTrade::providerTimestamp)
                .isEqualTo(false);
    }
}
