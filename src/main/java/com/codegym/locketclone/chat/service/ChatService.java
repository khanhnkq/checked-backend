package com.codegym.locketclone.chat.service;

import com.codegym.locketclone.chat.dto.request.CreateConversationRequest;
import com.codegym.locketclone.chat.dto.request.DeleteMode;
import com.codegym.locketclone.chat.dto.request.PhotoReplyRequest;
import com.codegym.locketclone.chat.dto.request.SendMessageRequest;
import com.codegym.locketclone.chat.dto.response.ConversationResponse;
import com.codegym.locketclone.chat.dto.response.MarkReadResponse;
import com.codegym.locketclone.chat.dto.response.MessageCursorPageResponse;
import com.codegym.locketclone.chat.dto.response.MessageResponse;
import com.codegym.locketclone.chat.dto.response.UnreadTotalResponse;
import com.codegym.locketclone.common.config.CacheConfig;
import org.springframework.cache.annotation.Cacheable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ChatService {

    ConversationResponse getOrCreateConversation(UUID currentUserId, CreateConversationRequest request);

    @Cacheable(value = CacheConfig.CHAT_CONVERSATIONS_CACHE, key = "#currentUserId")
    List<ConversationResponse> getMyConversations(UUID currentUserId);

    MessageCursorPageResponse getMessages(UUID currentUserId, String conversationId, Instant before, Integer limit);

    MessageResponse sendMessage(UUID currentUserId, String conversationId, SendMessageRequest request);

    MarkReadResponse markAsRead(UUID currentUserId, String conversationId);

    UUID getRecipientId(String conversationId, UUID currentUserId);

    MessageResponse replyToPhoto(UUID currentUserId, UUID photoId, PhotoReplyRequest request);

    @Cacheable(value = CacheConfig.CHAT_UNREAD_CACHE, key = "#currentUserId")
    UnreadTotalResponse getTotalUnreadCount(UUID currentUserId);

    void deleteMessage(UUID currentUserId, String conversationId, String messageId, DeleteMode mode);

    void evictChatCaches(UUID userId, boolean evictUnread, boolean evictConversations);
}
