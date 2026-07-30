package com.finwatch.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import tools.jackson.databind.ObjectMapper;

class RealtimeQuoteWebSocketHandlerTest {

    @Test
    void sendsLatestFxEventWhenBrowserConnects() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        RealtimeQuoteHub hub = new RealtimeQuoteHub();
        Instant fetchedAt = Instant.parse("2026-07-30T03:00:02Z");
        hub.publish(new RealtimeFxRate(
                "USD",
                "KRW",
                new BigDecimal("1382.50"),
                "LIVE",
                "FINNHUB_WS",
                "OANDA:USD_KRW",
                fetchedAt.minusSeconds(1),
                fetchedAt));
        RealtimeCandleAggregator candleAggregator = new RealtimeCandleAggregator(hub);
        RealtimeQuoteWebSocketHandler handler = new RealtimeQuoteWebSocketHandler(
                objectMapper,
                hub,
                candleAggregator,
                mock(RealtimeSubscriptionManager.class));
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("session-1");
        when(session.isOpen()).thenReturn(true);

        handler.afterConnectionEstablished(session);

        ArgumentCaptor<TextMessage> messages = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, times(3)).sendMessage(messages.capture());
        var fxPayload = messages.getAllValues().stream()
                .map(TextMessage::getPayload)
                .map(objectMapper::readTree)
                .filter(node -> "fx".equals(node.path("type").asText()))
                .findFirst()
                .orElseThrow();
        assertThat(fxPayload.path("data").path("baseCurrency").asText()).isEqualTo("USD");
        assertThat(fxPayload.path("data").path("quoteCurrency").asText()).isEqualTo("KRW");
        assertThat(fxPayload.path("data").path("rate").decimalValue()).isEqualByComparingTo("1382.50");
        assertThat(fxPayload.path("data").path("rateType").asText()).isEqualTo("LIVE");
        assertThat(fxPayload.path("data").path("source").asText()).isEqualTo("FINNHUB_WS");
        assertThat(fxPayload.path("data").path("providerSymbol").asText()).isEqualTo("OANDA:USD_KRW");
        assertThat(fxPayload.path("data").path("asOf").asText()).isEqualTo("2026-07-30T03:00:01Z");
        assertThat(fxPayload.path("data").path("fetchedAt").asText()).isEqualTo("2026-07-30T03:00:02Z");
    }
}
