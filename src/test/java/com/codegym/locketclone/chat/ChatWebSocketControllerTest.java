package com.codegym.locketclone.chat;

import com.codegym.locketclone.chat.controller.ChatWebSocketController;
import com.codegym.locketclone.chat.document.MessageType;
import com.codegym.locketclone.chat.dto.request.SendMessageRequest;
import com.codegym.locketclone.chat.dto.response.MarkReadResponse;
import com.codegym.locketclone.chat.dto.response.MessageResponse;
import com.codegym.locketclone.chat.dto.websocket.MarkChatReadPayload;
import com.codegym.locketclone.chat.dto.websocket.ReadReceiptEvent;
import com.codegym.locketclone.chat.dto.websocket.SendChatMessagePayload;
import com.codegym.locketclone.chat.dto.websocket.TypingIndicatorEvent;
import com.codegym.locketclone.chat.dto.websocket.TypingIndicatorPayload;
import com.codegym.locketclone.chat.service.ChatService;
import com.codegym.locketclone.common.exception.AppException;
import com.codegym.locketclone.common.exception.ErrorCode;
import com.codegym.locketclone.common.exception.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.security.Principal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatWebSocketControllerTest {

    @Mock
    private ChatService chatService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private Principal principal;

    private ChatWebSocketController webSocketController;

    private UUID senderId;
    private UUID recipientId;

    @BeforeEach
    void setUp() {
        webSocketController = new ChatWebSocketController(chatService, messagingTemplate);

        senderId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        recipientId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    }

    @Test
    @DisplayName("sendMessage - Gửi tin nhắn qua STOMP và dispatch tới cả 2 user")
    void sendMessage_success() {
        when(principal.getName()).thenReturn(senderId.toString());

        SendChatMessagePayload payload = SendChatMessagePayload.builder()
                .conversationId("conv-100")
                .content("Chào bạn nhé!")
                .type(MessageType.TEXT)
                .build();

        MessageResponse response = MessageResponse.builder()
                .id("msg-500")
                .conversationId("conv-100")
                .senderId(senderId)
                .content("Chào bạn nhé!")
                .type(MessageType.TEXT)
                .sentAt(Instant.now())
                .build();

        when(chatService.sendMessage(eq(senderId), eq("conv-100"), any(SendMessageRequest.class)))
                .thenReturn(response);
        when(chatService.getRecipientId("conv-100", senderId)).thenReturn(recipientId);

        webSocketController.sendMessage(payload, principal);

        // Kiểm tra dispatch tới recipient
        verify(messagingTemplate).convertAndSendToUser(
                eq(recipientId.toString()), eq("/queue/messages"), eq(response));

        // Kiểm tra dispatch lại tới sender
        verify(messagingTemplate).convertAndSendToUser(
                eq(senderId.toString()), eq("/queue/messages"), eq(response));
    }

    @Test
    @DisplayName("markAsRead - Đánh dấu đã đọc qua STOMP và dispatch ReadReceiptEvent tới đối phương")
    void markAsRead_success() {
        when(principal.getName()).thenReturn(senderId.toString());

        MarkChatReadPayload payload = MarkChatReadPayload.builder()
                .conversationId("conv-100")
                .build();

        Instant readAt = Instant.now();
        MarkReadResponse markResponse = MarkReadResponse.builder()
                .conversationId("conv-100")
                .readAt(readAt)
                .build();

        when(chatService.markAsRead(senderId, "conv-100")).thenReturn(markResponse);
        when(chatService.getRecipientId("conv-100", senderId)).thenReturn(recipientId);

        webSocketController.markAsRead(payload, principal);

        ArgumentCaptor<ReadReceiptEvent> eventCaptor = ArgumentCaptor.forClass(ReadReceiptEvent.class);
        verify(messagingTemplate).convertAndSendToUser(
                eq(recipientId.toString()), eq("/queue/read-receipts"), eventCaptor.capture());

        ReadReceiptEvent event = eventCaptor.getValue();
        assertEquals("conv-100", event.getConversationId());
        assertEquals(senderId, event.getReaderId());
        assertEquals(readAt, event.getReadAt());
    }

    @Test
    @DisplayName("sendMessage - Bỏ qua khi Principal Name không phải UUID hợp lệ")
    void sendMessage_whenPrincipalInvalidUuid_doesNotThrow() {
        when(principal.getName()).thenReturn("invalid-uuid-string");

        SendChatMessagePayload payload = SendChatMessagePayload.builder()
                .conversationId("conv-100")
                .content("Chào")
                .build();

        webSocketController.sendMessage(payload, principal);

        verifyNoInteractions(chatService);
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("handleAppException - Trả về ErrorResponse đúng mã lỗi khi có ngoại lệ nghiệp vụ")
    void handleAppException_returnsErrorResponse() {
        when(principal.getName()).thenReturn(senderId.toString());
        AppException ex = new AppException(ErrorCode.FORBIDDEN);

        ErrorResponse response = webSocketController.handleAppException(ex, principal);

        assertNotNull(response);
        assertEquals(403, response.getStatus());
        assertEquals(ErrorCode.FORBIDDEN.getMessage(), response.getMessage());
    }

    @Test
    @DisplayName("handleTyping - Dispatch TypingIndicatorEvent tới đối phương qua STOMP")
    void handleTyping_success() {
        when(principal.getName()).thenReturn(senderId.toString());

        TypingIndicatorPayload payload = TypingIndicatorPayload.builder()
                .conversationId("conv-100")
                .isTyping(true)
                .build();

        when(chatService.getRecipientId("conv-100", senderId)).thenReturn(recipientId);

        webSocketController.handleTyping(payload, principal);

        ArgumentCaptor<TypingIndicatorEvent> eventCaptor = ArgumentCaptor.forClass(TypingIndicatorEvent.class);
        verify(messagingTemplate).convertAndSendToUser(
                eq(recipientId.toString()), eq("/queue/typing"), eventCaptor.capture());

        TypingIndicatorEvent event = eventCaptor.getValue();
        assertEquals("conv-100", event.getConversationId());
        assertEquals(senderId, event.getUserId());
        assertTrue(event.isTyping());
    }

    @Test
    @DisplayName("TypingIndicatorEvent serialization - Đảm bảo serialize đúng trường isTyping")
    void typingIndicatorEventSerialization_containsIsTyping() throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

        TypingIndicatorEvent event = TypingIndicatorEvent.builder()
                .conversationId("conv-100")
                .userId(senderId)
                .isTyping(true)
                .build();

        String json = mapper.writeValueAsString(event);
        assertTrue(json.contains("\"isTyping\":true"));
        assertFalse(json.contains("\"typing\":true"));
    }
}
