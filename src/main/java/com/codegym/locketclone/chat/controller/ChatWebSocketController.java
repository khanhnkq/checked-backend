package com.codegym.locketclone.chat.controller;

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
import com.codegym.locketclone.common.exception.ErrorResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

@Controller
@Slf4j
@RequiredArgsConstructor
public class ChatWebSocketController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chat.send")
    public void sendMessage(@Valid @Payload SendChatMessagePayload payload, Principal principal) {
        if (principal == null) {
            log.warn("Bỏ qua tin nhắn STOMP: Principal is null");
            return;
        }

        UUID senderId = parsePrincipalId(principal);
        if (senderId == null) {
            return;
        }

        SendMessageRequest request = SendMessageRequest.builder()
                .content(payload.getContent())
                .type(payload.getType())
                .replyToMessageId(payload.getReplyToMessageId())
                .photoContext(payload.getPhotoContext())
                .build();

        MessageResponse response = chatService.sendMessage(senderId, payload.getConversationId(), request);
        UUID recipientId = chatService.getRecipientId(payload.getConversationId(), senderId);

        // 1. Gửi tin nhắn đến người nhận
        if (recipientId != null) {
            messagingTemplate.convertAndSendToUser(recipientId.toString(), "/queue/messages", response);
        }

        // 2. Gửi phản hồi lại cho người gửi (để đồng bộ ID, timestamp)
        messagingTemplate.convertAndSendToUser(senderId.toString(), "/queue/messages", response);
        log.info("Đã gửi tin nhắn realtime: msgId={}, convId={}, sender={}, recipient={}",
                response.getId(), payload.getConversationId(), senderId, recipientId);
    }

    @MessageMapping("/chat.read")
    public void markAsRead(@Valid @Payload MarkChatReadPayload payload, Principal principal) {
        if (principal == null) {
            log.warn("Bỏ qua đánh dấu đã đọc STOMP: Principal is null");
            return;
        }

        UUID readerId = parsePrincipalId(principal);
        if (readerId == null) {
            return;
        }

        MarkReadResponse response = chatService.markAsRead(readerId, payload.getConversationId());
        UUID partnerId = chatService.getRecipientId(payload.getConversationId(), readerId);

        if (partnerId != null) {
            ReadReceiptEvent event = ReadReceiptEvent.builder()
                    .conversationId(payload.getConversationId())
                    .readerId(readerId)
                    .readAt(response.getReadAt())
                    .build();

            messagingTemplate.convertAndSendToUser(partnerId.toString(), "/queue/read-receipts", event);
            log.info("Đã gửi sự kiện đã đọc: convId={}, reader={}, partner={}",
                    payload.getConversationId(), readerId, partnerId);
        }
    }

    @MessageMapping("/chat.typing")
    public void handleTyping(@Valid @Payload TypingIndicatorPayload payload, Principal principal) {
        if (principal == null || payload == null || payload.getConversationId() == null) {
            log.warn("Bỏ qua typing STOMP: Principal hoặc payload không hợp lệ");
            return;
        }

        UUID senderId = parsePrincipalId(principal);
        if (senderId == null) {
            return;
        }

        UUID recipientId = chatService.getRecipientId(payload.getConversationId(), senderId);
        if (recipientId != null) {
            TypingIndicatorEvent event = TypingIndicatorEvent.builder()
                    .conversationId(payload.getConversationId())
                    .userId(senderId)
                    .isTyping(payload.isTyping())
                    .build();

            messagingTemplate.convertAndSendToUser(recipientId.toString(), "/queue/typing", event);
        }
    }

    @MessageExceptionHandler(AppException.class)
    @SendToUser("/queue/errors")
    public ErrorResponse handleAppException(AppException ex, Principal principal) {
        log.warn("Lỗi nghiệp vụ chat WebSocket cho user [{}]: {}",
                principal != null ? principal.getName() : "anonymous", ex.getMessage());
        return new ErrorResponse(ex.getErrorCode().getStatusCode().value(), ex.getMessage());
    }

    private UUID parsePrincipalId(Principal principal) {
        try {
            return UUID.fromString(principal.getName());
        } catch (IllegalArgumentException ex) {
            log.error("Principal name không phải UUID hợp lệ: {}", principal.getName());
            return null;
        }
    }
}
