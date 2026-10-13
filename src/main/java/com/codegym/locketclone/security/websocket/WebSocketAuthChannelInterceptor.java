package com.codegym.locketclone.security.websocket;

import com.codegym.locketclone.security.jwt.JwtUtils;
import com.codegym.locketclone.security.service.UserDetailsServiceImpl;
import com.codegym.locketclone.security.service.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtUtils jwtUtils;
    private final UserDetailsServiceImpl userDetailsService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            accessor = StompHeaderAccessor.wrap(message);
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = extractToken(accessor);

            if (!StringUtils.hasText(token) || !jwtUtils.validateToken(token)) {
                log.warn("WebSocket kết nối bị từ chối: JWT token không hợp lệ hoặc bị thiếu");
                throw new AuthenticationCredentialsNotFoundException("JWT token không hợp lệ hoặc bị thiếu");
            }

            try {
                UUID userId = jwtUtils.getUserIdFromJWT(token);
                UserPrincipal userPrincipal = (UserPrincipal) userDetailsService.loadUserById(userId);

                WebSocketUserPrincipal wsPrincipal = new WebSocketUserPrincipal(userId, userPrincipal);
                Authentication auth = new UsernamePasswordAuthenticationToken(
                        wsPrincipal, null, userPrincipal.getAuthorities());

                if (!accessor.isMutable()) {
                    StompHeaderAccessor mutableAccessor = StompHeaderAccessor.wrap(message);
                    mutableAccessor.setUser(auth);
                    log.info("WebSocket xác thực thành công cho User ID: {}", userId);
                    return MessageBuilder.createMessage(message.getPayload(), mutableAccessor.getMessageHeaders());
                } else {
                    accessor.setUser(auth);
                    log.info("WebSocket xác thực thành công cho User ID: {}", userId);
                }
            } catch (Exception ex) {
                log.error("Lỗi khi tải thông tin User cho kết nối WebSocket: {}", ex.getMessage());
                throw new AuthenticationCredentialsNotFoundException("Không thể xác thực danh tính người dùng: " + ex.getMessage());
            }
        }

        return message;
    }

    private String extractToken(StompHeaderAccessor accessor) {
        // 1. Kiểm tra header Authorization (Bearer <token>)
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (StringUtils.hasText(authHeader)) {
            if (authHeader.startsWith("Bearer ") || authHeader.startsWith("bearer ")) {
                return authHeader.substring(7).trim();
            }
            return authHeader.trim();
        }

        // 2. Fallback kiểm tra header token / access_token
        String tokenHeader = accessor.getFirstNativeHeader("token");
        if (StringUtils.hasText(tokenHeader)) {
            return tokenHeader.trim();
        }

        String accessTokenHeader = accessor.getFirstNativeHeader("access_token");
        if (StringUtils.hasText(accessTokenHeader)) {
            return accessTokenHeader.trim();
        }

        return null;
    }
}
