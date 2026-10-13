package com.codegym.locketclone.chat.controller;

import com.codegym.locketclone.chat.dto.request.CreateConversationRequest;
import com.codegym.locketclone.chat.dto.request.DeleteMode;
import com.codegym.locketclone.chat.dto.request.SendMessageRequest;
import com.codegym.locketclone.chat.dto.response.ConversationResponse;
import com.codegym.locketclone.chat.dto.response.MarkReadResponse;
import com.codegym.locketclone.chat.dto.response.MessageCursorPageResponse;
import com.codegym.locketclone.chat.dto.response.MessageResponse;
import com.codegym.locketclone.chat.dto.response.UnreadTotalResponse;
import com.codegym.locketclone.chat.service.ChatService;
import com.codegym.locketclone.security.service.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@Tag(name = "Chat", description = "Quản lý cuộc trò chuyện và tin nhắn 1-1 (MongoDB NoSQL)")
@RestController
@RequestMapping({"/api/v1/conversations", "/api/conversations", "/api/v1/chat", "/api/chat"})
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @Operation(summary = "Lấy danh sách các cuộc trò chuyện của tôi")
    @GetMapping
    public ResponseEntity<List<ConversationResponse>> getMyConversations(
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        return ResponseEntity.ok(chatService.getMyConversations(currentUser.getId()));
    }

    @Operation(summary = "Mở hoặc tạo cuộc trò chuyện với bạn bè")
    @PostMapping
    public ResponseEntity<ConversationResponse> getOrCreateConversation(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody CreateConversationRequest request
    ) {
        return ResponseEntity.ok(chatService.getOrCreateConversation(currentUser.getId(), request));
    }

    @Operation(summary = "Lấy danh sách tin nhắn trong cuộc trò chuyện (Cursor-based pagination)")
    @GetMapping("/{conversationId}/messages")
    public ResponseEntity<MessageCursorPageResponse> getMessages(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable String conversationId,
            @RequestParam(required = false) Instant before,
            @RequestParam(required = false, defaultValue = "30") Integer limit
    ) {
        return ResponseEntity.ok(chatService.getMessages(currentUser.getId(), conversationId, before, limit));
    }

    @Operation(summary = "Gửi tin nhắn trong cuộc trò chuyện (REST fallback)")
    @PostMapping("/{conversationId}/messages")
    public ResponseEntity<MessageResponse> sendMessage(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable String conversationId,
            @Valid @RequestBody SendMessageRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(chatService.sendMessage(currentUser.getId(), conversationId, request));
    }

    @Operation(summary = "Đánh dấu đã đọc cuộc trò chuyện")
    @PutMapping("/{conversationId}/read")
    public ResponseEntity<MarkReadResponse> markAsRead(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable String conversationId
    ) {
        return ResponseEntity.ok(chatService.markAsRead(currentUser.getId(), conversationId));
    }

    @Operation(summary = "Lấy tổng số tin nhắn chưa đọc của tôi trên tất cả các cuộc trò chuyện")
    @GetMapping("/unread-total")
    public ResponseEntity<UnreadTotalResponse> getTotalUnreadCount(
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        return ResponseEntity.ok(chatService.getTotalUnreadCount(currentUser.getId()));
    }

    @Operation(summary = "Xóa hoặc thu hồi tin nhắn trong cuộc trò chuyện")
    @DeleteMapping("/{conversationId}/messages/{messageId}")
    public ResponseEntity<Void> deleteMessage(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable String conversationId,
            @PathVariable String messageId,
            @RequestParam(defaultValue = "FOR_ME") DeleteMode mode
    ) {
        chatService.deleteMessage(currentUser.getId(), conversationId, messageId, mode);
        return ResponseEntity.noContent().build();
    }
}
