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
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public RealtimeQuoteWebSocketHandler(ObjectMapper objectMapper, RealtimeQuoteHub hub) {
        this.objectMapper = objectMapper;
        this.hub = hub;
        this.hub.addListener(this::broadcast);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.put(session.getId(), session);
        send(session, new RealtimeEvent("snapshot", hub.snapshot()));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        sessions.remove(session.getId());
        try {
            session.close(CloseStatus.SERVER_ERROR);
        } catch (IOException ignored) {
            // The transport is already unavailable.
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
