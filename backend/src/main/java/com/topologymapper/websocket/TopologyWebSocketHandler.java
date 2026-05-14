package com.topologymapper.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.topologymapper.store.TopologyStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class TopologyWebSocketHandler extends TextWebSocketHandler {

    @Autowired
    private TopologyStore store;

    @Autowired
    private ObjectMapper objectMapper;

    private final Set<WebSocketSession> sessions =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
        log.debug("WS client connected: {}", session.getId());
        sendToSession(session, buildFullStateMessage());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.debug("WS client disconnected: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // Reserved for future client → server commands (e.g. TRIGGER_SCAN)
        log.debug("Received from client {}: {}", session.getId(), message.getPayload());
    }

    public void broadcastToAll(Object payload) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.error("Failed to serialize broadcast payload", e);
            return;
        }
        TextMessage msg = new TextMessage(json);
        sessions.removeIf(s -> !s.isOpen());
        sessions.forEach(s -> sendToSession(s, msg));
    }

    private void sendToSession(WebSocketSession session, TextMessage msg) {
        try {
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(msg);
                }
            }
        } catch (IOException e) {
            log.warn("Failed to send to session {}: {}", session.getId(), e.getMessage());
        }
    }

    private TextMessage buildFullStateMessage() throws Exception {
        Map<String, Object> payload = Map.of(
                "type", "FULL_STATE",
                "devices", store.allDevices(),
                "links", store.allLinks()
        );
        return new TextMessage(objectMapper.writeValueAsString(payload));
    }
}
