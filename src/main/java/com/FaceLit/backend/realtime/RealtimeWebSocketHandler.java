package com.FaceLit.backend.realtime;

import com.FaceLit.backend.auth.service.roleandpermission.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class RealtimeWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(RealtimeWebSocketHandler.class);
    private final JwtService jwtService;
    private final ObjectMapper objectMapper;
    private final Map<String, AuthenticatedSession> sessions = new ConcurrentHashMap<>();

    public RealtimeWebSocketHandler(JwtService jwtService, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        JsonNode request;
        try {
            request = objectMapper.readTree(message.getPayload());
        } catch (IOException exception) {
            closeForPolicyViolation(session);
            return;
        }

        String type = request.path("type").asText();
        if ("authenticate".equals(type)) {
            authenticate(session, request.path("token").asText());
            return;
        }

        AuthenticatedSession authenticated = sessions.get(session.getId());
        if (authenticated == null || !jwtService.validateToken(authenticated.token())) {
            sessions.remove(session.getId());
            closeForPolicyViolation(session);
            return;
        }

        if ("sync".equals(type)) {
            send(session, Map.of("type", "sync"));
            return;
        }

        closeForPolicyViolation(session);
    }

    private void authenticate(WebSocketSession session, String token) throws IOException {
        if (token.isBlank() || !jwtService.validateToken(token)) {
            closeForPolicyViolation(session);
            return;
        }

        AuthenticatedSession authenticated = new AuthenticatedSession(
                session,
                jwtService.extractUserId(token).toString(),
                normalizeRole(jwtService.extractRole(token)),
                token);
        sessions.put(session.getId(), authenticated);
        send(session, Map.of("type", "ready"));
    }

    public void publish(RealtimeChange event, String requestPath) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (IOException exception) {
            log.error("Could not serialize realtime change for resource {}", event.resource(), exception);
            return;
        }

        for (AuthenticatedSession recipient : sessions.values()) {
            if (!recipient.session().isOpen()) {
                sessions.remove(recipient.session().getId());
                continue;
            }

            if (!jwtService.validateToken(recipient.token())) {
                sessions.remove(recipient.session().getId());
                closeForPolicyViolation(recipient.session());
                continue;
            }

            if (isRecipientAllowed(recipient, requestPath, event.actorId())) {
                try {
                    synchronized (recipient.session()) {
                        recipient.session().sendMessage(new TextMessage(payload));
                    }
                } catch (IOException exception) {
                    log.warn("Could not deliver realtime change to session {}", recipient.session().getId(), exception);
                    sessions.remove(recipient.session().getId());
                    closeSession(recipient.session());
                }
            }
        }
    }

    private boolean isRecipientAllowed(AuthenticatedSession recipient, String path, String actorId) {
        if (path.startsWith("/api/profile/configuration")) {
            return recipient.userId().equals(actorId);
        }
        if (path.startsWith("/api/admin")) {
            return "COORDINATOR".equals(recipient.role());
        }
        if (path.startsWith("/api/environment")) {
            return "INSTRUCTOR".equals(recipient.role());
        }
        if (path.startsWith("/api/academic/me")) {
            return true;
        }
        if (path.startsWith("/api/academic/programs") || path.startsWith("/api/academic/chips")) {
            return "COORDINATOR".equals(recipient.role()) || "INSTRUCTOR".equals(recipient.role());
        }
        if (path.startsWith("/api/academic")) {
            return "COORDINATOR".equals(recipient.role());
        }
        return true;
    }

    private String normalizeRole(String role) {
        return switch (role == null ? "" : role.toUpperCase()) {
            case "COORDINADOR" -> "COORDINATOR";
            case "APRENDIZ" -> "APPRENTICE";
            default -> role == null ? "" : role.toUpperCase();
        };
    }

    private void send(WebSocketSession session, Object payload) throws IOException {
        synchronized (session) {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
            }
        }
    }

    private void closeForPolicyViolation(WebSocketSession session) {
        closeSession(session, CloseStatus.POLICY_VIOLATION);
    }

    private void closeSession(WebSocketSession session) {
        closeSession(session, CloseStatus.SERVER_ERROR);
    }

    private void closeSession(WebSocketSession session, CloseStatus status) {
        try {
            if (session.isOpen()) {
                session.close(status);
            }
        } catch (IOException exception) {
            log.warn("Could not close realtime session {}", session.getId(), exception);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
    }

    private record AuthenticatedSession(WebSocketSession session, String userId, String role, String token) {
    }
}
