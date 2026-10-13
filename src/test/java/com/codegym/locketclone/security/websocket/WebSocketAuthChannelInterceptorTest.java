package com.codegym.locketclone.security.websocket;

import com.codegym.locketclone.security.jwt.JwtUtils;
import com.codegym.locketclone.security.service.UserDetailsServiceImpl;
import com.codegym.locketclone.security.service.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;

import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebSocketAuthChannelInterceptorTest {

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private UserDetailsServiceImpl userDetailsService;

    @Mock
    private MessageChannel messageChannel;

    private WebSocketAuthChannelInterceptor interceptor;

    private UUID userId;
    private UserPrincipal userPrincipal;

    @BeforeEach
    void setUp() {
        interceptor = new WebSocketAuthChannelInterceptor(jwtUtils, userDetailsService);

        userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        userPrincipal = new UserPrincipal(
                userId,
                "khanhnkq",
                "khanhnkq.dev@gmail.com",
                "secret",
                Collections.emptyList()
        );
    }

    @Test
    @DisplayName("preSend - CONNECT với JWT Bearer hợp lệ gán Authentication thành công")
    void preSend_connectWithValidToken_authenticatesSuccessfully() {
        String token = "valid-jwt-token";
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer " + token);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        when(jwtUtils.validateToken(token)).thenReturn(true);
        when(jwtUtils.getUserIdFromJWT(token)).thenReturn(userId);
        when(userDetailsService.loadUserById(userId)).thenReturn(userPrincipal);

        Message<?> result = interceptor.preSend(message, messageChannel);

        assertNotNull(result);
        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
        assertNotNull(resultAccessor.getUser());
        assertInstanceOf(Authentication.class, resultAccessor.getUser());
        assertEquals(userId.toString(), resultAccessor.getUser().getName());
    }

    @Test
    @DisplayName("preSend - CONNECT không có Token ném AuthenticationCredentialsNotFoundException")
    void preSend_connectWithoutToken_throwsException() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThrows(AuthenticationCredentialsNotFoundException.class,
                () -> interceptor.preSend(message, messageChannel));

        verifyNoInteractions(jwtUtils);
    }

    @Test
    @DisplayName("preSend - CONNECT với Token không hợp lệ ném ngoại lệ")
    void preSend_connectWithInvalidToken_throwsException() {
        String token = "invalid-token";
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer " + token);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        when(jwtUtils.validateToken(token)).thenReturn(false);

        assertThrows(AuthenticationCredentialsNotFoundException.class,
                () -> interceptor.preSend(message, messageChannel));

        verify(jwtUtils, never()).getUserIdFromJWT(any());
    }

    @Test
    @DisplayName("preSend - Command không phải CONNECT (ví dụ SUBSCRIBE) được bỏ qua không cần xác thực")
    void preSend_nonConnectCommand_bypassesAuth() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, messageChannel);

        assertNotNull(result);
        verifyNoInteractions(jwtUtils);
    }
}
