package com.finwatch.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;

import org.junit.jupiter.api.Test;

import com.finwatch.data.provider.FinnhubMarketDataClient;
import com.finwatch.fx.realtime.FinnhubFxRealtimeStore;

import tools.jackson.databind.ObjectMapper;

class FinnhubRealtimeClientTest {

    @Test
    void publishesOnlyAcceptedProviderTimestampedFxTicksToHub() {
        ObjectMapper objectMapper = new ObjectMapper();
        RealtimeQuoteHub hub = new RealtimeQuoteHub();
        var events = new ArrayList<RealtimeEvent>();
        hub.addListener(events::add);
        FinnhubFxRealtimeStore store = new FinnhubFxRealtimeStore();
        FinnhubRealtimeClient client = new FinnhubRealtimeClient(
                "test-token",
                "wss://ws.finnhub.io",
                Duration.ofSeconds(1),
                Duration.ofSeconds(30),
                true,
                "OANDA:USD_KRW",
                objectMapper,
                mock(FinnhubMarketDataClient.class),
                new FinnhubTradeMessageParser(objectMapper),
                new UsMarketSessionResolver(),
                store,
                hub);
        Instant newest = Instant.now().minusSeconds(2);

        client.handleMessage(frame("1382.50", newest));
        client.handleMessage(frame("1200", newest.minusSeconds(1)));
        client.handleMessage("""
                {"type":"trade","data":[{"s":"OANDA:USD_KRW","p":1500,"v":1}]}
                """);

        assertThat(store.latest("OANDA:USD_KRW", Duration.ofMinutes(2), Instant.now()))
                .get().extracting(FinnhubFxRealtimeStore.Snapshot::rate)
                .satisfies(rate -> assertThat(rate).isEqualByComparingTo("1382.50"));
        assertThat(hub.findFx("USD", "KRW")).get().satisfies(fx -> {
            assertThat(fx.rate()).isEqualByComparingTo("1382.50");
            assertThat(fx.rateType()).isEqualTo("LIVE");
            assertThat(fx.source()).isEqualTo("FINNHUB_WS");
            assertThat(fx.providerSymbol()).isEqualTo("OANDA:USD_KRW");
            assertThat(fx.asOf()).isEqualTo(newest.truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
        });
        assertThat(events).singleElement()
                .satisfies(event -> assertThat(event.type()).isEqualTo("fx"));
    }

    @Test
    void publishesRecentUsTradeWithProviderTimeBasedAfterHoursStatus() {
        RealtimeQuoteHub hub = new RealtimeQuoteHub();
        var events = new ArrayList<RealtimeEvent>();
        hub.addListener(events::add);
        FinnhubRealtimeClient client = stockClientAt(hub, "2026-07-14T21:00:30Z");
        client.updateSubscriptions(java.util.List.of("AAPL"));
        client.updateInstrumentMarkets(java.util.Map.of("AAPL", "NASDAQ"));

        client.handleMessage(stockFrame("AAPL", "203.30", "2026-07-14T21:00:00Z"));

        assertThat(events).extracting(RealtimeEvent::type).containsOnly("quote");
        assertThat(events).extracting(event -> ((LiveQuote) event.data()).sessionStatus())
                .containsExactly("AFTER_HOURS");
        assertThat(hub.find("NASDAQ", "AAPL")).get().satisfies(quote -> {
            assertThat(quote.price()).isEqualByComparingTo("203.30");
            assertThat(quote.sessionStatus()).isEqualTo("AFTER_HOURS");
            assertThat(quote.source()).isEqualTo("FINNHUB_WS");
        });
    }

    @Test
    void rejectsStockTradesWithoutProviderTimeOrOutsideFreshStreamingWindow() {
        RealtimeQuoteHub hub = new RealtimeQuoteHub();
        FinnhubRealtimeClient client = stockClientAt(hub, "2026-07-14T21:00:00Z");
        client.updateSubscriptions(java.util.List.of("AAPL"));
        client.updateInstrumentMarkets(java.util.Map.of("AAPL", "NASDAQ"));

        client.handleMessage("""
                {"type":"trade","data":[{"s":"AAPL","p":201.10,"v":2}]}
                """);
        client.handleMessage(stockFrame("AAPL", "202.20", "2026-07-14T21:00:06Z"));
        client.handleMessage(stockFrame("AAPL", "203.30", "2026-07-14T20:57:59Z"));

        assertThat(hub.find("NASDAQ", "AAPL")).isEmpty();
    }

    @Test
    void rejectsTradesOutsideUsStreamingSessionsBeforeTheyReachQuotesOrAlerts() {
        RealtimeQuoteHub hub = new RealtimeQuoteHub();
        FinnhubRealtimeClient client = stockClientAt(hub, "2026-07-15T01:00:30Z");
        client.updateSubscriptions(java.util.List.of("AAPL"));
        client.updateInstrumentMarkets(java.util.Map.of("AAPL", "NASDAQ"));

        client.handleMessage(stockFrame("AAPL", "204.40", "2026-07-15T01:00:00Z"));

        assertThat(hub.find("NASDAQ", "AAPL")).isEmpty();
    }

    private String frame(String rate, Instant asOf) {
        return """
                {"type":"trade","data":[
                  {"s":"OANDA:USD_KRW","p":%s,"t":%d,"v":1}
                ]}
                """.formatted(rate, asOf.toEpochMilli());
    }

    private String stockFrame(String symbol, String price, String asOf) {
        return """
                {"type":"trade","data":[
                  {"s":"%s","p":%s,"t":%d,"v":2}
                ]}
                """.formatted(symbol, price, Instant.parse(asOf).toEpochMilli());
    }

    private FinnhubRealtimeClient stockClientAt(RealtimeQuoteHub hub, String now) {
        ObjectMapper objectMapper = new ObjectMapper();
        return new FinnhubRealtimeClient(
                "test-token",
                "wss://ws.finnhub.io",
                Duration.ofSeconds(1),
                Duration.ofSeconds(30),
                false,
                "OANDA:USD_KRW",
                objectMapper,
                mock(FinnhubMarketDataClient.class),
                new FinnhubTradeMessageParser(objectMapper),
                new UsMarketSessionResolver(),
                new FinnhubFxRealtimeStore(),
                hub,
                Clock.fixed(Instant.parse(now), ZoneOffset.UTC));
    }
}
