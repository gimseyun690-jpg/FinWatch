package com.finwatch.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.http.WebSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

class KisOverseasRealtimeClientTest {

    @Test
    void publishesAnHdfscnt0TradeUsingTheCanonicalUsInstrument() {
        RealtimeQuoteHub hub = new RealtimeQuoteHub();
        KisOverseasRealtimeClient client = client(hub);
        WebSocket socket = mockSocket();
        Instant openedAt = Instant.parse("2026-08-11T09:00:00Z");
        try {
            client.updateSubscriptions(List.of(KisOverseasSubscription.standard("NASDAQ", "AAPL")));
            client.handleSocketOpen(socket, "approval", openedAt);
            client.handleMessage(socket, tick("AAPL", "20260811", "180001", "213.40"), openedAt.plusSeconds(1));

            assertThat(hub.find("NASDAQ", "AAPL")).get().satisfies(quote -> {
                assertThat(quote.price()).isEqualByComparingTo("213.40");
                assertThat(quote.source()).isEqualTo("KIS_OVERSEAS_WS");
                assertThat(quote.sessionStatus()).isEqualTo("PRE_MARKET");
            });
            assertThat(client.lastTickAt()).isEqualTo(openedAt.plusSeconds(1));
        } finally {
            client.stop();
        }
    }

    @Test
    void usesDaytimeSessionForTheSpecialRbaqSubscription() {
        RealtimeQuoteHub hub = new RealtimeQuoteHub();
        KisOverseasRealtimeClient client = client(hub);
        WebSocket socket = mockSocket();
        Instant openedAt = Instant.parse("2026-08-11T09:00:00Z");
        try {
            client.updateSubscriptions(List.of(KisOverseasSubscription.daytime("NASDAQ", "AAPL")));
            client.handleSocketOpen(socket, "approval", openedAt);
            client.handleMessage(socket, tick("AAPL", "20260811", "180001", "213.40"), openedAt.plusSeconds(1));

            assertThat(hub.find("NASDAQ", "AAPL")).get()
                    .extracting(LiveQuote::sessionStatus)
                    .isEqualTo("US_DAYTIME");
        } finally {
            client.stop();
        }
    }

    private KisOverseasRealtimeClient client(RealtimeQuoteHub hub) {
        return new KisOverseasRealtimeClient(
                "app-key",
                "app-secret",
                "prod",
                "https://openapi.koreainvestment.com:9443",
                "https://openapivts.koreainvestment.com:29443",
                "ws://ops.koreainvestment.com:21000",
                "ws://ops.koreainvestment.com:31000",
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                Duration.ofSeconds(30),
                Duration.ofSeconds(90),
                Duration.ofSeconds(15),
                40,
                new ObjectMapper(),
                new UsMarketSessionResolver(),
                hub);
    }

    private WebSocket mockSocket() {
        WebSocket socket = mock(WebSocket.class);
        when(socket.sendText(anyString(), eq(true))).thenReturn(CompletableFuture.completedFuture(socket));
        return socket;
    }

    private String tick(String symbol, String koreaDate, String koreaTime, String last) {
        String[] fields = new String[25];
        Arrays.fill(fields, "");
        fields[0] = symbol;
        fields[5] = koreaDate;
        fields[6] = koreaTime;
        fields[10] = last;
        fields[11] = "2";
        fields[12] = "1.35";
        fields[13] = "0.63";
        fields[19] = "123456";
        return "0|HDFSCNT0|001|" + String.join("^", fields);
    }
}
