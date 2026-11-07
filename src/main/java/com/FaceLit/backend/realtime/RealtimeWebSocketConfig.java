package com.FaceLit.backend.realtime;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class RealtimeWebSocketConfig implements WebSocketConfigurer {

    private final RealtimeWebSocketHandler handler;
    @org.springframework.beans.factory.annotation.Value("${facelit.allowed-origins:http://localhost:8081,http://localhost:19006}")
    private String[] allowedOrigins;

    public RealtimeWebSocketConfig(RealtimeWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/realtime")
                .setAllowedOrigins(allowedOrigins);
    }
}
