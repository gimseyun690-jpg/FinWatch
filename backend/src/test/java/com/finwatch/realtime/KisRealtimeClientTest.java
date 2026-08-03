package com.finwatch.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;

import com.finwatch.data.provider.KisMarketDataClient;

import tools.jackson.databind.ObjectMapper;

class KisRealtimeClientTest {

    @Test
    void waitsForProviderAckInsteadOfTreatingSendCompletionAsConnected() {
        Fixture fixture = fixture();
        try {
            fixture.client().updateSubscriptions(List.of("005930"));

            fixture.client().handleSocketOpen(fixture.socket(), "approval", Instant.parse("2026-08-03T00:00:00Z"));

            assertThat(provider(fixture.hub()).state()).isEqualTo("SUBSCRIBING");

            fixture.client().handleMessage(fixture.socket(), successfulAck(), Instant.parse("2026-08-03T00:00:01Z"));

            assertThat(provider(fixture.hub()).state()).isEqualTo("CONNECTED");
        } finally {
            fixture.client().stop();
        }
    }

    @Test
    void keepsFailedSubscriptionTruthfullyDegradedUntilAValidTickRecoversIt() {
        Fixture fixture = fixture();
        try {
            fixture.client().updateSubscriptions(List.of("005930"));
            fixture.client().handleSocketOpen(fixture.socket(), "approval", Instant.parse("2026-08-03T00:00:00Z"));

            fixture.client().handleMessage(fixture.socket(), failedAck(), Instant.parse("2026-08-03T00:00:01Z"));

            assertThat(provider(fixture.hub()).state()).isEqualTo("DEGRADED");
            assertThat(provider(fixture.hub()).message()).contains("subscription rejected");

            Instant tickAt = Instant.parse("2026-08-03T00:00:02Z");
            fixture.client().handleMessage(fixture.socket(), tick(), tickAt);

            assertThat(provider(fixture.hub()).state()).isEqualTo("CONNECTED");
            assertThat(fixture.client().lastTickAt()).isEqualTo(tickAt);
        } finally {
            fixture.client().stop();
        }
    }

    @Test
    void pingPongRefreshesInboundLivenessButAStaleSubscribedSocketIsAborted() {
        Fixture fixture = fixture();
        try {
            Instant openedAt = Instant.parse("2026-08-03T00:00:00Z");
            fixture.client().updateSubscriptions(List.of("005930"));
            fixture.client().handleSocketOpen(fixture.socket(), "approval", openedAt);

            Instant pingAt = openedAt.plusSeconds(60);
            fixture.client().handleMessage(fixture.socket(), pingPong(), pingAt);
            fixture.client().checkLiveness(openedAt.plusSeconds(149));

            verify(fixture.socket(), never()).abort();
            verify(fixture.socket()).sendPong(any(ByteBuffer.class));
            assertThat(fixture.client().lastInboundAt()).isEqualTo(pingAt);

            fixture.client().checkLiveness(openedAt.plusSeconds(151));

            verify(fixture.socket()).abort();
            assertThat(provider(fixture.hub()).state()).isEqualTo("DEGRADED");
            assertThat(provider(fixture.hub()).message()).contains("91초 동안 없어");
        } finally {
            fixture.client().stop();
        }
    }

    @Test
    void ignoresLateMessagesFromASupersededSocket() {
        Fixture fixture = fixture();
        WebSocket replacement = mockSocket();
        try {
            fixture.client().updateSubscriptions(List.of("005930"));
            fixture.client().handleSocketOpen(
                    fixture.socket(), "approval", Instant.parse("2026-08-03T00:00:00Z"));
            fixture.client().handleSocketOpen(
                    replacement, "approval", Instant.parse("2026-08-03T00:00:02Z"));

            fixture.client().handleMessage(
                    fixture.socket(), failedAck(), Instant.parse("2026-08-03T00:00:03Z"));

            assertThat(provider(fixture.hub()).state()).isEqualTo("SUBSCRIBING");
            assertThat(fixture.client().lastInboundAt()).isEqualTo(Instant.parse("2026-08-03T00:00:02Z"));
        } finally {
            fixture.client().stop();
        }
    }

    private Fixture fixture() {
        WebSocket socket = mockSocket();
        RealtimeQuoteHub hub = new RealtimeQuoteHub();
        KisRealtimeClient client = new KisRealtimeClient(
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
                new ObjectMapper(),
                mock(KisMarketDataClient.class),
                hub);
        return new Fixture(client, socket, hub);
    }

    private WebSocket mockSocket() {
        WebSocket socket = mock(WebSocket.class);
        when(socket.sendText(anyString(), eq(true))).thenReturn(CompletableFuture.completedFuture(socket));
        when(socket.sendPong(any(ByteBuffer.class))).thenReturn(CompletableFuture.completedFuture(socket));
        return socket;
    }

    private RealtimeProviderStatus provider(RealtimeQuoteHub hub) {
        return hub.snapshot().providers().stream()
                .filter(status -> "KIS".equals(status.provider()))
                .findFirst()
                .orElseThrow();
    }

    private String successfulAck() {
        return """
                {"header":{"tr_id":"H0STCNT0"},"body":{"rt_cd":"0","msg1":"OK"}}
                """;
    }

    private String failedAck() {
        return """
                {"header":{"tr_id":"H0STCNT0"},"body":{"rt_cd":"1","msg1":"subscription rejected"}}
                """;
    }

    private String pingPong() {
        return """
                {"header":{"tr_id":"PINGPONG"}}
                """;
    }

    private String tick() {
        String[] fields = new String[46];
        java.util.Arrays.fill(fields, "");
        fields[0] = "005930";
        fields[1] = "090002";
        fields[2] = "210000";
        fields[3] = "2";
        fields[4] = "500";
        fields[5] = "0.24";
        fields[13] = "12345";
        fields[33] = "20260803";
        return "0|H0STCNT0|001|" + String.join("^", fields);
    }

    private record Fixture(KisRealtimeClient client, WebSocket socket, RealtimeQuoteHub hub) {
    }
}
