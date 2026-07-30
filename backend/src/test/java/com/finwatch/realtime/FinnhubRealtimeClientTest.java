package com.finwatch.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
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

    private String frame(String rate, Instant asOf) {
        return """
                {"type":"trade","data":[
                  {"s":"OANDA:USD_KRW","p":%s,"t":%d,"v":1}
                ]}
                """.formatted(rate, asOf.toEpochMilli());
    }
}
