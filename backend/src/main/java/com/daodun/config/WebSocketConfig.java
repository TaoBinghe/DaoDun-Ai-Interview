package com.daodun.config;

import com.daodun.websocket.InterviewWebSocketHandler;
import com.daodun.websocket.WsAuthHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final InterviewWebSocketHandler interviewWebSocketHandler;
    private final WsAuthHandshakeInterceptor wsAuthHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(interviewWebSocketHandler, "/ws/interview")
                .addInterceptors(wsAuthHandshakeInterceptor)
                .setAllowedOriginPatterns(
                        "http://localhost:5000",
                        "http://127.0.0.1:5000",
                        "http://localhost:5173",
                        "http://127.0.0.1:5173",
                        "http://localhost:5174",
                        "http://127.0.0.1:5174"
                );
    }
}
