package com.codegym.locketclone.chat.service.impl;

import com.codegym.locketclone.chat.document.*;
import com.codegym.locketclone.chat.dto.request.CreateConversationRequest;
import com.codegym.locketclone.chat.dto.request.SendMessageRequest;
import com.codegym.locketclone.chat.dto.response.*;
import com.codegym.locketclone.chat.repository.ConversationRepository;
import com.codegym.locketclone.chat.repository.MessageRepository;
import com.codegym.locketclone.chat.service.ChatService;
import com.codegym.locketclone.common.exception.AppException;
import com.codegym.locketclone.common.exception.ErrorCode;
import com.codegym.locketclone.friendship.FriendshipRepository;
import com.codegym.locketclone.user.User;
import com.codegym.locketclone.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final FriendshipRepository friendshipRepository;
    private final MongoTemplate mongoTemplate;

    @Value("${app.chat.default-page-size:30}")
    private int defaultPageSize = 30;

    @Value("${app.chat.max-page-size:50}")
    private int maxPageSize = 50;

    @Override
    public ConversationResponse getOrCreateConversation(UUID currentUserId, CreateConversationRequest request) {
        UUID friendId = request.getFriendId();

        if (currentUserId.equals(friendId)) {
            throw new AppException(ErrorCode.CANNOT_CHAT_WITH_SELF);
        }

        User friend = userRepository.findById(friendId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        boolean areFriends = friendshipRepository.findAcceptedBetweenUsers(currentUserId, friendId).isPresent();
        if (!areFriends) {
            throw new AppException(ErrorCode.CANNOT_CHAT_WITH_NON_FRIEND);
        }

        List<UUID> sortedParticipantIds = sortParticipants(currentUserId, friendId);
        String pairKey = buildPairKey(currentUserId, friendId);

        Conversation conversation;
        try {
            conversation = conversationRepository.findByPairKey(pairKey)
                    .or(() -> conversationRepository.findByParticipantIds(sortedParticipantIds))
                    .orElseGet(() -> {
                        Instant now = Instant.now();
                        Map<String, Instant> readStatus = new HashMap<>();
                        readStatus.put(currentUserId.toString(), now);
                        readStatus.put(friendId.toString(), now);

                        Conversation newConv = Conversation.builder()
                                .pairKey(pairKey)
                                .participantIds(sortedParticipantIds)
                                .readStatus(readStatus)
                                .createdAt(now)
                                .updatedAt(now)
                                .build();
                        return conversationRepository.save(newConv);
                    });
        } catch (DuplicateKeyException e) {
            log.info("Phát hiện tạo cuộc trò chuyện đồng thời cho pairKey: {}, đọc lại bản ghi vừa tạo", pairKey);
            conversation = conversationRepository.findByPairKey(pairKey)
                    .orElseThrow(() -> e);
        }

        long unreadCount = computeUnreadCount(conversation, currentUserId);

        return toConversationResponse(conversation, friend, unreadCount);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConversationResponse> getMyConversations(UUID currentUserId) {
        List<Conversation> conversations = conversationRepository
                .findByParticipantIdsContainingOrderByUpdatedAtDesc(currentUserId);

        if (conversations.isEmpty()) {
            return Collections.emptyList();
        }

        Set<UUID> friendIds = conversations.stream()
                .flatMap(conv -> conv.getParticipantIds().stream())
                .filter(id -> !id.equals(currentUserId))
                .collect(Collectors.toSet());

        Map<UUID, User> userMap = userRepository.findAllById(friendIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return conversations.stream()
                .map(conv -> {
                    UUID friendId = conv.getParticipantIds().stream()
                            .filter(id -> !id.equals(currentUserId))
                            .findFirst()
                            .orElse(null);

                    User friend = (friendId != null) ? userMap.get(friendId) : null;
                    long unreadCount = computeUnreadCount(conv, currentUserId);

                    return toConversationResponse(conv, friend, unreadCount);
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public MessageCursorPageResponse getMessages(UUID currentUserId, String conversationId, Instant before, Integer limit) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND));

        validateParticipant(conversation, currentUserId);

        int pageSize = (limit != null && limit > 0) ? Math.min(limit, maxPageSize) : defaultPageSize;
        Pageable pageable = PageRequest.of(0, pageSize + 1);

        List<Message> messages;
        if (before != null) {
            messages = messageRepository.findByConversationIdAndSentAtBeforeOrderBySentAtDesc(
                    conversationId, before, pageable);
        } else {
            messages = messageRepository.findByConversationIdOrderBySentAtDesc(
                    conversationId, pageable);
        }

        boolean hasMore = messages.size() > pageSize;
        if (hasMore) {
            messages = new ArrayList<>(messages.subList(0, pageSize));
        }

        Instant nextCursor = null;
        if (!messages.isEmpty() && hasMore) {
            nextCursor = messages.get(messages.size() - 1).getSentAt();
        }

        UUID otherUserId = getOtherParticipantId(conversation, currentUserId);

        List<MessageResponse> responseList = messages.stream()
                .map(m -> toMessageResponse(m, currentUserId, otherUserId))
                .toList();

        return MessageCursorPageResponse.builder()
                .messages(responseList)
                .nextCursor(nextCursor)
                .hasMore(hasMore)
                .build();
    }

    @Override
    public MessageResponse sendMessage(UUID currentUserId, String conversationId, SendMessageRequest request) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND));

        validateParticipant(conversation, currentUserId);

        ReplyToInfo replyToInfo = null;
        if (request.getReplyToMessageId() != null && !request.getReplyToMessageId().isBlank()) {
            replyToInfo = messageRepository.findById(request.getReplyToMessageId())
                    .filter(rm -> conversationId.equals(rm.getConversationId())) // Chặn trích dẫn tin nhắn từ cuộc trò chuyện khác
                    .map(rm -> ReplyToInfo.builder()
                            .messageId(rm.getId())
                            .senderId(rm.getSenderId())
                            .content(rm.getContent())
                            .type(rm.getType())
                            .build())
                    .orElse(null);
        }

        MessageType messageType = request.getType() != null ? request.getType() : MessageType.TEXT;
        PhotoContextInfo photoContext = request.getPhotoContext();
        if (messageType == MessageType.PHOTO_REPLY && (photoContext == null || photoContext.getPhotoId() == null)) {
            messageType = MessageType.TEXT;
            photoContext = null;
        }

        Instant now = Instant.now();
        Set<UUID> readBy = new HashSet<>();
        readBy.add(currentUserId);

        Message message = Message.builder()
                .conversationId(conversationId)
                .senderId(currentUserId)
                .content(request.getContent().trim())
                .type(messageType)
                .replyTo(replyToInfo)
                .photoContext(photoContext)
                .readBy(readBy)
                .sentAt(now)
                .build();

        Message savedMessage = messageRepository.save(message);

        conversation.setLastMessage(LastMessageInfo.builder()
                .messageId(savedMessage.getId())
                .senderId(currentUserId)
                .content(savedMessage.getContent())
                .type(savedMessage.getType())
                .sentAt(savedMessage.getSentAt())
                .build());

        if (conversation.getReadStatus() == null) {
            conversation.setReadStatus(new HashMap<>());
        }
        conversation.getReadStatus().put(currentUserId.toString(), now);
        conversation.setUpdatedAt(now);
        conversationRepository.save(conversation);

        UUID otherUserId = getOtherParticipantId(conversation, currentUserId);
        return toMessageResponse(savedMessage, currentUserId, otherUserId);
    }

    @Override
    public MarkReadResponse markAsRead(UUID currentUserId, String conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND));

        validateParticipant(conversation, currentUserId);

        Instant now = Instant.now();
        if (conversation.getReadStatus() == null) {
            conversation.setReadStatus(new HashMap<>());
        }
        conversation.getReadStatus().put(currentUserId.toString(), now);
        conversationRepository.save(conversation);

        Query query = new Query(Criteria.where("conversationId").is(conversationId)
                .and("senderId").ne(currentUserId)
                .and("readBy").ne(currentUserId));
        Update update = new Update().addToSet("readBy", currentUserId);
        mongoTemplate.updateMulti(query, update, Message.class);

        return MarkReadResponse.builder()
                .conversationId(conversationId)
                .readAt(now)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public UUID getRecipientId(String conversationId, UUID currentUserId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND));

        validateParticipant(conversation, currentUserId);
        return getOtherParticipantId(conversation, currentUserId);
    }

    private List<UUID> sortParticipants(UUID u1, UUID u2) {
        return Stream.of(u1, u2)
                .sorted(Comparator.comparing(UUID::toString))
                .toList();
    }

    private String buildPairKey(UUID u1, UUID u2) {
        List<UUID> sorted = sortParticipants(u1, u2);
        return sorted.get(0).toString() + "_" + sorted.get(1).toString();
    }

    /**
     * Tối ưu hóa tính unread count:
     * - Nếu cuộc trò chuyện chưa có tin nhắn hoặc người gửi cuối là user hiện tại -> unread = 0 (0ms, không query DB).
     * - Nếu thời điểm xem gần nhất >= thời điểm gửi của tin nhắn cuối -> unread = 0 (0ms, không query DB).
     * - Chỉ khi có tin nhắn mới từ đối phương sau mốc đọc mới truy vấn count.
     */
    private long computeUnreadCount(Conversation conv, UUID currentUserId) {
        LastMessageInfo lastMsg = conv.getLastMessage();
        if (lastMsg == null || currentUserId.equals(lastMsg.getSenderId())) {
            return 0L;
        }

        Instant myLastRead = conv.getReadStatus() != null ? conv.getReadStatus().get(currentUserId.toString()) : null;
        if (myLastRead != null && lastMsg.getSentAt() != null && !lastMsg.getSentAt().isAfter(myLastRead)) {
            return 0L;
        }

        return messageRepository.countByConversationIdAndSenderIdNotAndReadByNotContaining(
                conv.getId(), currentUserId, currentUserId);
    }

    private void validateParticipant(Conversation conversation, UUID userId) {
        if (conversation.getParticipantIds() == null || !conversation.getParticipantIds().contains(userId)) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
    }

    private UUID getOtherParticipantId(Conversation conversation, UUID currentUserId) {
        if (conversation.getParticipantIds() == null) {
            return null;
        }
        return conversation.getParticipantIds().stream()
                .filter(id -> !id.equals(currentUserId))
                .findFirst()
                .orElse(null);
    }

    private ConversationResponse toConversationResponse(Conversation conv, User friend, long unreadCount) {
        FriendSummaryResponse friendSummary = null;
        if (friend != null) {
            friendSummary = FriendSummaryResponse.builder()
                    .id(friend.getId())
                    .fullName(friend.getDisplayName())
                    .username(friend.getUsername())
                    .avatarUrl(friend.getAvatarUrl())
                    .build();
        }

        return ConversationResponse.builder()
                .id(conv.getId())
                .friend(friendSummary)
                .lastMessage(conv.getLastMessage())
                .unreadCount(unreadCount)
                .createdAt(conv.getCreatedAt())
                .updatedAt(conv.getUpdatedAt())
                .build();
    }

    private MessageResponse toMessageResponse(Message m, UUID currentUserId, UUID otherUserId) {
        boolean isRead;
        if (m.getSenderId().equals(currentUserId)) {
            isRead = otherUserId != null && m.getReadBy() != null && m.getReadBy().contains(otherUserId);
        } else {
            isRead = m.getReadBy() != null && m.getReadBy().contains(currentUserId);
        }

        return MessageResponse.builder()
                .id(m.getId())
                .conversationId(m.getConversationId())
                .senderId(m.getSenderId())
                .content(m.getContent())
                .type(m.getType())
                .replyTo(m.getReplyTo())
                .photoContext(m.getPhotoContext())
                .isRead(isRead)
                .sentAt(m.getSentAt())
                .build();
    }
}
