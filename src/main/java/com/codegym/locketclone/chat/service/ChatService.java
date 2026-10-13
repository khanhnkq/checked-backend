package com.codegym.locketclone.chat.service;

import com.codegym.locketclone.chat.dto.request.CreateConversationRequest;
import com.codegym.locketclone.chat.dto.request.SendMessageRequest;
import com.codegym.locketclone.chat.dto.response.ConversationResponse;
import com.codegym.locketclone.chat.dto.response.MarkReadResponse;
import com.codegym.locketclone.chat.dto.response.MessageCursorPageResponse;
import com.codegym.locketclone.chat.dto.response.MessageResponse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ChatService {

    ConversationResponse getOrCreateConversation(UUID currentUserId, CreateConversationRequest request);

    List<ConversationResponse> getMyConversations(UUID currentUserId);

    MessageCursorPageResponse getMessages(UUID currentUserId, String conversationId, Instant before, Integer limit);

    MessageResponse sendMessage(UUID currentUserId, String conversationId, SendMessageRequest request);

    MarkReadResponse markAsRead(UUID currentUserId, String conversationId);

    UUID getRecipientId(String conversationId, UUID currentUserId);
}
