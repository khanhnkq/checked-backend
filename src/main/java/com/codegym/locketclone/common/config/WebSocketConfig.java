package com.codegym.locketclone.common.config;

import com.codegym.locketclone.security.websocket.WebSocketAuthChannelInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Arrays;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthChannelInterceptor authChannelInterceptor;

    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:5173}")
    private String allowedOrigins;

    @Value("${app.websocket.heartbeat-incoming-ms:10000}")
    private long heartbeatIncomingMs;

    @Value("${app.websocket.heartbeat-outgoing-ms:10000}")
    private long heartbeatOutgoingMs;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] originPatterns = resolveOriginPatterns();

        // 1. Endpoint hỗ trợ SockJS fallback (cho Web Client / Browsers)
        registry.addEndpoint("/ws-chat")
                .setAllowedOriginPatterns(originPatterns)
                .withSockJS();

        // 2. Endpoint WebSocket thuần (cho Native iOS URLSessionWebSocketTask)
        registry.addEndpoint("/ws-chat")
                .setAllowedOriginPatterns(originPatterns);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Cấu hình TaskScheduler cho STOMP Heartbeat (mặc định 10s ping / 10s pong chống ngắt kết nối Heroku 55s)
        ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(1);
        taskScheduler.setThreadNamePrefix("ws-heartbeat-");
        taskScheduler.initialize();

        registry.enableSimpleBroker("/queue", "/topic")
                .setTaskScheduler(taskScheduler)
                .setHeartbeatValue(new long[]{heartbeatIncomingMs, heartbeatOutgoingMs});

        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authChannelInterceptor);
    }

    private String[] resolveOriginPatterns() {
        if (!StringUtils.hasText(allowedOrigins)) {
            return new String[]{"*"};
        }
        return Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toArray(String[]::new);
    }
}
