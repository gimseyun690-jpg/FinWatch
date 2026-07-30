package com.finwatch.realtime;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import tools.jackson.databind.ObjectMapper;

@Component
public class RealtimeQuoteWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final RealtimeQuoteHub hub;
    private final RealtimeCandleAggregator candleAggregator;
    private final RealtimeSubscriptionManager subscriptionManager;
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public RealtimeQuoteWebSocketHandler(
            ObjectMapper objectMapper,
            RealtimeQuoteHub hub,
            RealtimeCandleAggregator candleAggregator,
            RealtimeSubscriptionManager subscriptionManager) {
        this.objectMapper = objectMapper;
        this.hub = hub;
        this.candleAggregator = candleAggregator;
        this.subscriptionManager = subscriptionManager;
        this.hub.addListener(this::broadcast);
        this.candleAggregator.addListener(candle -> broadcast(new RealtimeEvent("candle", candle)));
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.put(session.getId(), session);
        send(session, new RealtimeEvent("snapshot", hub.snapshot()));
        for (RealtimeFxRate fxRate : hub.fxSnapshot()) {
            send(session, new RealtimeEvent("fx", fxRate));
        }
        send(session, new RealtimeEvent("candles", candleAggregator.snapshot()));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
        subscriptionManager.releaseSession(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        sessions.remove(session.getId());
        subscriptionManager.releaseSession(session.getId());
        try {
            session.close(CloseStatus.SERVER_ERROR);
        } catch (IOException ignored) {
            // The transport is already unavailable.
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            var root = objectMapper.readTree(message.getPayload());
            if (!"select".equals(root.path("type").asText())) {
                return;
            }
            var result = subscriptionManager.select(
                    session.getId(),
                    root.path("market").asText(),
                    root.path("symbol").asText());
            send(session, new RealtimeEvent("subscription", result));
        } catch (RuntimeException exception) {
            send(session, new RealtimeEvent(
                    "subscription",
                    new RealtimeSubscriptionManager.SelectionResult(false, "", "", "구독 요청 형식이 올바르지 않습니다.")));
        }
    }

    private void broadcast(RealtimeEvent event) {
        for (WebSocketSession session : sessions.values()) {
            try {
                send(session, event);
            } catch (IOException exception) {
                sessions.remove(session.getId());
                try {
                    session.close(CloseStatus.SERVER_ERROR);
                } catch (IOException ignored) {
                    // The session is already closed.
                }
            }
        }
    }

    private void send(WebSocketSession session, Object payload) throws IOException {
        if (!session.isOpen()) {
            sessions.remove(session.getId());
            return;
        }
        String json = objectMapper.writeValueAsString(payload);
        synchronized (session) {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(json));
            }
        }
    }
}
