package com.codegym.locketclone.chat.service.impl;

import com.codegym.locketclone.chat.document.*;
import com.codegym.locketclone.chat.dto.request.CreateConversationRequest;
import com.codegym.locketclone.chat.dto.request.DeleteMode;
import com.codegym.locketclone.chat.dto.request.PhotoReplyRequest;
import com.codegym.locketclone.chat.dto.request.SendMessageRequest;
import com.codegym.locketclone.chat.dto.response.*;
import com.codegym.locketclone.chat.repository.ConversationRepository;
import com.codegym.locketclone.chat.repository.MessageRepository;
import com.codegym.locketclone.chat.service.ChatService;
import com.codegym.locketclone.common.exception.AppException;
import com.codegym.locketclone.common.exception.ErrorCode;
import com.codegym.locketclone.friendship.FriendshipRepository;
import com.codegym.locketclone.photo.Photo;
import com.codegym.locketclone.photo.PhotoRepository;
import com.codegym.locketclone.photo.PhotoStatus;
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
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
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
    private final PhotoRepository photoRepository;
    private final MongoTemplate mongoTemplate;
    private final SimpMessagingTemplate messagingTemplate;

    @Value("${app.chat.default-page-size:30}")
    private int defaultPageSize = 30;

    @Value("${app.chat.max-page-size:50}")
    private int maxPageSize = 50;

    @Value("${app.chat.revoke-time-limit-minutes:60}")
    private long revokeTimeLimitMinutes = 60;

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

        // Đảm bảo chỉ giữ lại 1 cuộc hội thoại mới nhất cho mỗi bạn bè
        Map<UUID, Conversation> latestByFriend = new LinkedHashMap<>();
        for (Conversation conv : conversations) {
            UUID friendId = conv.getParticipantIds().stream()
                    .filter(id -> !id.equals(currentUserId))
                    .findFirst()
                    .orElse(null);
            if (friendId != null && !latestByFriend.containsKey(friendId)) {
                latestByFriend.put(friendId, conv);
            }
        }

        Set<UUID> friendIds = latestByFriend.keySet();
        Map<UUID, User> userMap = userRepository.findAllById(friendIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return latestByFriend.entrySet().stream()
                .map(entry -> {
                    UUID friendId = entry.getKey();
                    Conversation conv = entry.getValue();
                    User friend = userMap.get(friendId);
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
                .filter(m -> m.getDeletedFor() == null || !m.getDeletedFor().contains(currentUserId))
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
                    .map(rm -> {
                        String content = (rm.getType() == MessageType.REVOKED)
                                ? "Tin nhắn đã được thu hồi"
                                : rm.getContent();
                        return ReplyToInfo.builder()
                                .messageId(rm.getId())
                                .senderId(rm.getSenderId())
                                .content(content)
                                .type(rm.getType())
                                .build();
                    })
                    .orElse(null);
        }

        MessageType messageType = request.getType() != null ? request.getType() : MessageType.TEXT;
        PhotoContextInfo photoContext = request.getPhotoContext();
        if (messageType == MessageType.PHOTO_REPLY && (photoContext == null || photoContext.getPhotoId() == null)) {
            messageType = MessageType.TEXT;
            photoContext = null;
        } else if (messageType == MessageType.PHOTO_REPLY) {
            Photo photo = photoRepository.findAccessiblePhotoById(photoContext.getPhotoId(), currentUserId, PhotoStatus.DELETED)
                    .orElse(null);
            if (photo != null) {
                String imageUrl = StringUtils.hasText(photo.getThumbnailUrl()) ? photo.getThumbnailUrl() : photo.getImageUrl();
                photoContext.setImageUrl(imageUrl);
                if (photo.getSender() != null) {
                    photoContext.setSenderName(photo.getSender().getDisplayName());
                }
            } else {
                messageType = MessageType.TEXT;
                photoContext = null;
            }
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

    @Override
    public MessageResponse replyToPhoto(UUID currentUserId, UUID photoId, PhotoReplyRequest request) {
        Photo photo = photoRepository.findAccessiblePhotoById(photoId, currentUserId, PhotoStatus.DELETED)
                .orElseThrow(() -> new AppException(ErrorCode.PHOTO_NOT_ACCESSIBLE));

        UUID photoOwnerId = photo.getSender().getId();
        if (currentUserId.equals(photoOwnerId)) {
            throw new AppException(ErrorCode.CANNOT_CHAT_WITH_SELF);
        }

        // Tạo hoặc lấy conversation 1-1 với chủ ảnh
        CreateConversationRequest createConvReq = CreateConversationRequest.builder()
                .friendId(photoOwnerId)
                .build();
        ConversationResponse convResponse = getOrCreateConversation(currentUserId, createConvReq);
        String conversationId = convResponse.getId();

        // Tạo snapshot photoContext tin cậy từ database
        String imageUrl = StringUtils.hasText(photo.getThumbnailUrl()) ? photo.getThumbnailUrl() : photo.getImageUrl();
        PhotoContextInfo photoContext = PhotoContextInfo.builder()
                .photoId(photo.getId())
                .imageUrl(imageUrl)
                .senderName(photo.getSender().getDisplayName())
                .build();

        SendMessageRequest sendMsgReq = SendMessageRequest.builder()
                .content(request.getContent())
                .type(MessageType.PHOTO_REPLY)
                .replyToMessageId(request.getReplyToMessageId())
                .photoContext(photoContext)
                .build();

        MessageResponse response = sendMessage(currentUserId, conversationId, sendMsgReq);

        // Gửi realtime qua STOMP WebSocket
        try {
            messagingTemplate.convertAndSendToUser(photoOwnerId.toString(), "/queue/messages", response);
            messagingTemplate.convertAndSendToUser(currentUserId.toString(), "/queue/messages", response);
        } catch (Exception ex) {
            log.warn("Lỗi gửi tin nhắn realtime WebSocket qua Photo Reply: {}", ex.getMessage());
        }

        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public UnreadTotalResponse getTotalUnreadCount(UUID currentUserId) {
        List<Conversation> conversations = conversationRepository
                .findByParticipantIdsContainingOrderByUpdatedAtDesc(currentUserId);

        long total = conversations.stream()
                .mapToLong(conv -> computeUnreadCount(conv, currentUserId))
                .sum();

        return UnreadTotalResponse.builder()
                .totalUnread(total)
                .build();
    }

    @Override
    public void deleteMessage(UUID currentUserId, String conversationId, String messageId, DeleteMode mode) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND));

        validateParticipant(conversation, currentUserId);

        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new AppException(ErrorCode.MESSAGE_NOT_FOUND));

        if (!conversationId.equals(message.getConversationId())) {
            throw new AppException(ErrorCode.MESSAGE_NOT_FOUND);
        }

        if (mode == null) {
            mode = DeleteMode.FOR_ME;
        }

        if (mode == DeleteMode.FOR_ME) {
            if (message.getDeletedFor() == null) {
                message.setDeletedFor(new HashSet<>());
            }
            message.getDeletedFor().add(currentUserId);
            messageRepository.save(message);
            log.info("User {} đã xóa tin nhắn {} ở phía mình", currentUserId, messageId);
            return;
        }

        if (mode == DeleteMode.FOR_EVERYONE) {
            if (!currentUserId.equals(message.getSenderId())) {
                throw new AppException(ErrorCode.CANNOT_REVOKE_OTHERS_MESSAGE);
            }

            if (message.getType() == MessageType.REVOKED) {
                log.info("Tin nhắn {} đã được thu hồi trước đó", messageId);
                return;
            }

            Instant now = Instant.now();
            Instant sentAt = message.getSentAt() != null ? message.getSentAt() : now;
            long diffMinutes = Duration.between(sentAt, now).toMinutes();
            if (diffMinutes > revokeTimeLimitMinutes) {
                throw new AppException(ErrorCode.MESSAGE_REVOKE_TIME_EXPIRED);
            }

            message.setContent("Tin nhắn đã được thu hồi");
            message.setType(MessageType.REVOKED);
            message.setPhotoContext(null);
            message.setReplyTo(null);
            messageRepository.save(message);

            // Cập nhật lastMessage nếu tin bị thu hồi là tin cuối
            if (conversation.getLastMessage() != null && messageId.equals(conversation.getLastMessage().getMessageId())) {
                conversation.getLastMessage().setContent("Tin nhắn đã được thu hồi");
                conversation.getLastMessage().setType(MessageType.REVOKED);
                conversationRepository.save(conversation);
            }

            MessageRevokedEvent event = MessageRevokedEvent.builder()
                    .conversationId(conversationId)
                    .messageId(messageId)
                    .revokedAt(now)
                    .build();

            UUID recipientId = getOtherParticipantId(conversation, currentUserId);
            try {
                if (recipientId != null) {
                    messagingTemplate.convertAndSendToUser(recipientId.toString(), "/queue/message-revoked", event);
                }
                messagingTemplate.convertAndSendToUser(currentUserId.toString(), "/queue/message-revoked", event);
            } catch (Exception ex) {
                log.warn("Lỗi gửi sự kiện thu hồi tin nhắn WebSocket: {}", ex.getMessage());
            }

            log.info("User {} đã thu hồi tin nhắn {} trong hội thoại {}", currentUserId, messageId, conversationId);
        }
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
