package com.codegym.locketclone.chat;

import com.codegym.locketclone.chat.document.*;
import com.codegym.locketclone.chat.dto.request.CreateConversationRequest;
import com.codegym.locketclone.chat.dto.request.DeleteMode;
import com.codegym.locketclone.chat.dto.request.SendMessageRequest;
import com.codegym.locketclone.chat.dto.response.ConversationResponse;
import com.codegym.locketclone.chat.dto.response.MarkReadResponse;
import com.codegym.locketclone.chat.dto.response.MessageCursorPageResponse;
import com.codegym.locketclone.chat.dto.response.MessageResponse;
import com.codegym.locketclone.chat.dto.response.UnreadTotalResponse;
import com.codegym.locketclone.chat.repository.ConversationRepository;
import com.codegym.locketclone.chat.repository.MessageRepository;
import com.codegym.locketclone.chat.service.impl.ChatServiceImpl;
import com.codegym.locketclone.common.exception.AppException;
import com.codegym.locketclone.common.exception.ErrorCode;
import com.codegym.locketclone.friendship.Friendship;
import com.codegym.locketclone.friendship.FriendshipRepository;
import com.codegym.locketclone.photo.PhotoRepository;
import com.codegym.locketclone.user.User;
import com.codegym.locketclone.user.UserRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceImplTest {

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FriendshipRepository friendshipRepository;

    @Mock
    private PhotoRepository photoRepository;

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private ChatServiceImpl chatService;

    private UUID currentUserId;
    private UUID friendId;
    private User friendUser;

    @BeforeEach
    void setUp() {
        chatService = new ChatServiceImpl(
                conversationRepository,
                messageRepository,
                userRepository,
                friendshipRepository,
                photoRepository,
                mongoTemplate,
                messagingTemplate
        );

        currentUserId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        friendId = UUID.fromString("22222222-2222-2222-2222-222222222222");

        friendUser = User.builder()
                .id(friendId)
                .username("khanh_friend")
                .firstName("Khanh")
                .lastName("Nguyen")
                .avatarUrl("https://example.com/avatar.jpg")
                .build();
    }

    @Test
    @DisplayName("getOrCreateConversation - Ném ngoại lệ khi tự chat với chính mình")
    void getOrCreateConversation_whenSameUser_throwsException() {
        CreateConversationRequest request = CreateConversationRequest.builder()
                .friendId(currentUserId)
                .build();

        AppException ex = assertThrows(AppException.class,
                () -> chatService.getOrCreateConversation(currentUserId, request));

        assertEquals(ErrorCode.CANNOT_CHAT_WITH_SELF, ex.getErrorCode());
        verifyNoInteractions(conversationRepository);
    }

    @Test
    @DisplayName("getOrCreateConversation - Ném ngoại lệ khi không tìm thấy bạn bè")
    void getOrCreateConversation_whenFriendNotFound_throwsException() {
        CreateConversationRequest request = CreateConversationRequest.builder()
                .friendId(friendId)
                .build();

        when(userRepository.findById(friendId)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class,
                () -> chatService.getOrCreateConversation(currentUserId, request));

        assertEquals(ErrorCode.USER_NOT_FOUND, ex.getErrorCode());
        verifyNoInteractions(conversationRepository);
    }

    @Test
    @DisplayName("getOrCreateConversation - Ném ngoại lệ khi chưa là bạn bè")
    void getOrCreateConversation_whenNotFriends_throwsException() {
        CreateConversationRequest request = CreateConversationRequest.builder()
                .friendId(friendId)
                .build();

        when(userRepository.findById(friendId)).thenReturn(Optional.of(friendUser));
        when(friendshipRepository.findAcceptedBetweenUsers(currentUserId, friendId)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class,
                () -> chatService.getOrCreateConversation(currentUserId, request));

        assertEquals(ErrorCode.CANNOT_CHAT_WITH_NON_FRIEND, ex.getErrorCode());
        verifyNoInteractions(conversationRepository);
    }

    @Test
    @DisplayName("getOrCreateConversation - Trả về cuộc hội thoại đã tồn tại qua pairKey")
    void getOrCreateConversation_whenExists_returnsExisting() {
        CreateConversationRequest request = CreateConversationRequest.builder()
                .friendId(friendId)
                .build();

        Conversation existing = Conversation.builder()
                .id("conv-123")
                .pairKey("11111111-1111-1111-1111-111111111111_22222222-2222-2222-2222-222222222222")
                .participantIds(List.of(currentUserId, friendId))
                .readStatus(new HashMap<>())
                .build();

        when(userRepository.findById(friendId)).thenReturn(Optional.of(friendUser));
        when(friendshipRepository.findAcceptedBetweenUsers(currentUserId, friendId))
                .thenReturn(Optional.of(new Friendship()));
        when(conversationRepository.findByPairKey(any())).thenReturn(Optional.of(existing));

        ConversationResponse response = chatService.getOrCreateConversation(currentUserId, request);

        assertNotNull(response);
        assertEquals("conv-123", response.getId());
        assertEquals("Khanh Nguyen", response.getFriend().getFullName());
        assertEquals(0L, response.getUnreadCount());
        verify(conversationRepository, never()).save(any());
    }

    @Test
    @DisplayName("getOrCreateConversation - Tạo mới cuộc hội thoại với pairKey nếu chưa có")
    void getOrCreateConversation_whenNew_createsAndReturns() {
        CreateConversationRequest request = CreateConversationRequest.builder()
                .friendId(friendId)
                .build();

        Conversation created = Conversation.builder()
                .id("conv-new")
                .pairKey("11111111-1111-1111-1111-111111111111_22222222-2222-2222-2222-222222222222")
                .participantIds(List.of(currentUserId, friendId))
                .readStatus(new HashMap<>())
                .build();

        when(userRepository.findById(friendId)).thenReturn(Optional.of(friendUser));
        when(friendshipRepository.findAcceptedBetweenUsers(currentUserId, friendId))
                .thenReturn(Optional.of(new Friendship()));
        when(conversationRepository.findByPairKey(any())).thenReturn(Optional.empty());
        when(conversationRepository.findByParticipantIds(any())).thenReturn(Optional.empty());
        when(conversationRepository.save(any())).thenReturn(created);

        ConversationResponse response = chatService.getOrCreateConversation(currentUserId, request);

        assertNotNull(response);
        assertEquals("conv-new", response.getId());
        verify(conversationRepository).save(any(Conversation.class));
    }

    @Test
    @DisplayName("getMyConversations - Trả về danh sách cuộc trò chuyện với unread count được tối ưu")
    void getMyConversations_returnsList() {
        Conversation conv = Conversation.builder()
                .id("conv-1")
                .participantIds(List.of(currentUserId, friendId))
                .lastMessage(LastMessageInfo.builder()
                        .messageId("msg-1")
                        .senderId(friendId)
                        .content("Alo bạn ơi")
                        .sentAt(Instant.now())
                        .build())
                .updatedAt(Instant.now())
                .build();

        when(conversationRepository.findByParticipantIdsContainingOrderByUpdatedAtDesc(currentUserId))
                .thenReturn(List.of(conv));
        when(userRepository.findAllById(any())).thenReturn(List.of(friendUser));
        when(messageRepository.countByConversationIdAndSenderIdNotAndReadByNotContaining("conv-1", currentUserId, currentUserId))
                .thenReturn(3L);

        List<ConversationResponse> result = chatService.getMyConversations(currentUserId);

        assertEquals(1, result.size());
        assertEquals("conv-1", result.get(0).getId());
        assertEquals(3L, result.get(0).getUnreadCount());
        assertEquals("Khanh Nguyen", result.get(0).getFriend().getFullName());
    }

    @Test
    @DisplayName("getMyConversations - Không truy vấn DB count nếu tin nhắn cuối do chính mình gửi")
    void getMyConversations_whenLastMessageSentByMe_unreadCountZeroWithoutDbQuery() {
        Conversation conv = Conversation.builder()
                .id("conv-2")
                .participantIds(List.of(currentUserId, friendId))
                .lastMessage(LastMessageInfo.builder()
                        .messageId("msg-2")
                        .senderId(currentUserId)
                        .content("Tôi gửi nè")
                        .sentAt(Instant.now())
                        .build())
                .updatedAt(Instant.now())
                .build();

        when(conversationRepository.findByParticipantIdsContainingOrderByUpdatedAtDesc(currentUserId))
                .thenReturn(List.of(conv));
        when(userRepository.findAllById(any())).thenReturn(List.of(friendUser));

        List<ConversationResponse> result = chatService.getMyConversations(currentUserId);

        assertEquals(1, result.size());
        assertEquals(0L, result.get(0).getUnreadCount());
        verifyNoInteractions(messageRepository);
    }

    @Test
    @DisplayName("getMessages - Ném lỗi khi không tìm thấy cuộc trò chuyện")
    void getMessages_whenNotFound_throwsException() {
        when(conversationRepository.findById("invalid-id")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class,
                () -> chatService.getMessages(currentUserId, "invalid-id", null, 20));

        assertEquals(ErrorCode.CONVERSATION_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("getMessages - Ném lỗi khi người dùng không thuộc cuộc trò chuyện")
    void getMessages_whenNotParticipant_throwsForbidden() {
        UUID strangerId = UUID.randomUUID();
        Conversation conv = Conversation.builder()
                .id("conv-stranger")
                .participantIds(List.of(friendId, strangerId))
                .build();

        when(conversationRepository.findById("conv-stranger")).thenReturn(Optional.of(conv));

        AppException ex = assertThrows(AppException.class,
                () -> chatService.getMessages(currentUserId, "conv-stranger", null, 20));

        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    @DisplayName("getMessages - Trả về danh sách tin nhắn phân trang cursor")
    void getMessages_returnsPageSuccessfully() {
        Conversation conv = Conversation.builder()
                .id("conv-1")
                .participantIds(List.of(currentUserId, friendId))
                .build();

        Instant t1 = Instant.now();
        Instant t2 = t1.minusSeconds(60);

        Message m1 = Message.builder()
                .id("msg-1")
                .conversationId("conv-1")
                .senderId(friendId)
                .content("Hello")
                .type(MessageType.TEXT)
                .readBy(Set.of(currentUserId, friendId))
                .sentAt(t1)
                .build();

        Message m2 = Message.builder()
                .id("msg-2")
                .conversationId("conv-1")
                .senderId(currentUserId)
                .content("Hi friend")
                .type(MessageType.TEXT)
                .readBy(Set.of(currentUserId))
                .sentAt(t2)
                .build();

        when(conversationRepository.findById("conv-1")).thenReturn(Optional.of(conv));
        when(messageRepository.findByConversationIdOrderBySentAtDesc(eq("conv-1"), any(Pageable.class)))
                .thenReturn(List.of(m1, m2));

        MessageCursorPageResponse page = chatService.getMessages(currentUserId, "conv-1", null, 10);

        assertNotNull(page);
        assertEquals(2, page.getMessages().size());
        assertFalse(page.isHasMore());
        assertTrue(page.getMessages().get(0).isRead());
        assertFalse(page.getMessages().get(1).isRead());
    }

    @Test
    @DisplayName("sendMessage - Lưu tin nhắn và cập nhật lastMessage của hội thoại")
    void sendMessage_success() {
        Conversation conv = Conversation.builder()
                .id("conv-1")
                .participantIds(List.of(currentUserId, friendId))
                .readStatus(new HashMap<>())
                .build();

        SendMessageRequest req = SendMessageRequest.builder()
                .content("Ăn trưa chưa?")
                .type(MessageType.TEXT)
                .build();

        when(conversationRepository.findById("conv-1")).thenReturn(Optional.of(conv));
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message m = invocation.getArgument(0);
            m.setId("new-msg-id");
            return m;
        });

        MessageResponse response = chatService.sendMessage(currentUserId, "conv-1", req);

        assertNotNull(response);
        assertEquals("new-msg-id", response.getId());
        assertEquals("Ăn trưa chưa?", response.getContent());
        assertEquals(currentUserId, response.getSenderId());
        verify(conversationRepository).save(conv);
        assertEquals("new-msg-id", conv.getLastMessage().getMessageId());
    }

    @Test
    @DisplayName("markAsRead - Cập nhật readStatus và đánh dấu tin nhắn")
    void markAsRead_success() {
        Conversation conv = Conversation.builder()
                .id("conv-1")
                .participantIds(List.of(currentUserId, friendId))
                .readStatus(new HashMap<>())
                .build();

        when(conversationRepository.findById("conv-1")).thenReturn(Optional.of(conv));

        MarkReadResponse response = chatService.markAsRead(currentUserId, "conv-1");

        assertNotNull(response);
        assertEquals("conv-1", response.getConversationId());
        assertNotNull(response.getReadAt());
        verify(conversationRepository).save(conv);
        verify(mongoTemplate).updateMulti(any(Query.class), any(Update.class), eq(Message.class));
    }

    @Test
    @DisplayName("getRecipientId - Trả về ID của bạn bè trong cuộc hội thoại")
    void getRecipientId_success() {
        Conversation conv = Conversation.builder()
                .id("conv-1")
                .participantIds(List.of(currentUserId, friendId))
                .build();

        when(conversationRepository.findById("conv-1")).thenReturn(Optional.of(conv));

        UUID result = chatService.getRecipientId("conv-1", currentUserId);

        assertEquals(friendId, result);
    }

    @Test
    @DisplayName("MessageResponse serialization - Đảm bảo serialize đúng trường isRead thay vì read")
    void messageResponseSerialization_containsIsRead() throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

        MessageResponse response = MessageResponse.builder()
                .id("msg-1")
                .content("hello")
                .isRead(true)
                .sentAt(Instant.now())
                .build();

        String json = mapper.writeValueAsString(response);
        assertTrue(json.contains("\"isRead\":true"));
        assertFalse(json.contains("\"read\":true"));
    }

    @Test
    @DisplayName("MessageCursorPageResponse serialization - Đảm bảo serialize đúng trường hasMore")
    void cursorPageSerialization_containsHasMore() throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

        MessageCursorPageResponse response = MessageCursorPageResponse.builder()
                .messages(List.of())
                .hasMore(true)
                .build();

        String json = mapper.writeValueAsString(response);
        assertTrue(json.contains("\"hasMore\":true"));
        assertFalse(json.contains("\"more\":true"));
    }

    @Test
    @DisplayName("getTotalUnreadCount - Tính tổng unread count chính xác")
    void getTotalUnreadCount_success() {
        Conversation conv1 = Conversation.builder()
                .id("conv-1")
                .participantIds(List.of(currentUserId, friendId))
                .lastMessage(LastMessageInfo.builder()
                        .senderId(friendId)
                        .sentAt(Instant.now())
                        .build())
                .build();

        Conversation conv2 = Conversation.builder()
                .id("conv-2")
                .participantIds(List.of(currentUserId, friendId))
                .lastMessage(LastMessageInfo.builder()
                        .senderId(currentUserId)
                        .sentAt(Instant.now())
                        .build())
                .build();

        when(conversationRepository.findByParticipantIdsContainingOrderByUpdatedAtDesc(currentUserId))
                .thenReturn(List.of(conv1, conv2));
        when(messageRepository.countByConversationIdAndSenderIdNotAndReadByNotContaining("conv-1", currentUserId, currentUserId))
                .thenReturn(3L);

        UnreadTotalResponse total = chatService.getTotalUnreadCount(currentUserId);

        assertNotNull(total);
        assertEquals(3L, total.getTotalUnread());
    }

    @Test
    @DisplayName("deleteMessage - Xóa FOR_ME thêm user vào deletedFor")
    void deleteMessage_forMe_success() {
        Conversation conv = Conversation.builder()
                .id("conv-1")
                .participantIds(List.of(currentUserId, friendId))
                .build();

        Message msg = Message.builder()
                .id("msg-1")
                .conversationId("conv-1")
                .senderId(friendId)
                .content("Chào")
                .deletedFor(new HashSet<>())
                .build();

        when(conversationRepository.findById("conv-1")).thenReturn(Optional.of(conv));
        when(messageRepository.findById("msg-1")).thenReturn(Optional.of(msg));

        chatService.deleteMessage(currentUserId, "conv-1", "msg-1", DeleteMode.FOR_ME);

        assertTrue(msg.getDeletedFor().contains(currentUserId));
        verify(messageRepository).save(msg);
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("deleteMessage - Thu hồi FOR_EVERYONE thành công khi là người gửi")
    void deleteMessage_forEveryone_success() {
        Conversation conv = Conversation.builder()
                .id("conv-1")
                .participantIds(List.of(currentUserId, friendId))
                .lastMessage(LastMessageInfo.builder()
                        .messageId("msg-1")
                        .content("Nội dung cũ")
                        .build())
                .build();

        Message msg = Message.builder()
                .id("msg-1")
                .conversationId("conv-1")
                .senderId(currentUserId)
                .content("Nội dung cũ")
                .sentAt(Instant.now().minusSeconds(60))
                .build();

        when(conversationRepository.findById("conv-1")).thenReturn(Optional.of(conv));
        when(messageRepository.findById("msg-1")).thenReturn(Optional.of(msg));

        chatService.deleteMessage(currentUserId, "conv-1", "msg-1", DeleteMode.FOR_EVERYONE);

        assertEquals("Tin nhắn đã được thu hồi", msg.getContent());
        assertEquals(MessageType.REVOKED, msg.getType());
        verify(messageRepository).save(msg);
        verify(conversationRepository).save(conv);
        verify(messagingTemplate).convertAndSendToUser(eq(friendId.toString()), eq("/queue/message-revoked"), any());
        verify(messagingTemplate).convertAndSendToUser(eq(currentUserId.toString()), eq("/queue/message-revoked"), any());
    }

    @Test
    @DisplayName("deleteMessage - Ném ngoại lệ khi thu hồi tin nhắn của người khác")
    void deleteMessage_forEveryone_whenNotSender_throwsException() {
        Conversation conv = Conversation.builder()
                .id("conv-1")
                .participantIds(List.of(currentUserId, friendId))
                .build();

        Message msg = Message.builder()
                .id("msg-1")
                .conversationId("conv-1")
                .senderId(friendId)
                .sentAt(Instant.now())
                .build();

        when(conversationRepository.findById("conv-1")).thenReturn(Optional.of(conv));
        when(messageRepository.findById("msg-1")).thenReturn(Optional.of(msg));

        AppException ex = assertThrows(AppException.class,
                () -> chatService.deleteMessage(currentUserId, "conv-1", "msg-1", DeleteMode.FOR_EVERYONE));

        assertEquals(ErrorCode.CANNOT_REVOKE_OTHERS_MESSAGE, ex.getErrorCode());
        verify(messageRepository, never()).save(any());
    }

    @Test
    @DisplayName("deleteMessage - Ném ngoại lệ khi thu hồi tin nhắn đã quá thời hạn")
    void deleteMessage_forEveryone_whenExpired_throwsException() {
        Conversation conv = Conversation.builder()
                .id("conv-1")
                .participantIds(List.of(currentUserId, friendId))
                .build();

        Message msg = Message.builder()
                .id("msg-1")
                .conversationId("conv-1")
                .senderId(currentUserId)
                .sentAt(Instant.now().minusSeconds(7200)) // 2 tiếng trước (> 60 phút)
                .build();

        when(conversationRepository.findById("conv-1")).thenReturn(Optional.of(conv));
        when(messageRepository.findById("msg-1")).thenReturn(Optional.of(msg));

        AppException ex = assertThrows(AppException.class,
                () -> chatService.deleteMessage(currentUserId, "conv-1", "msg-1", DeleteMode.FOR_EVERYONE));

        assertEquals(ErrorCode.MESSAGE_REVOKE_TIME_EXPIRED, ex.getErrorCode());
        verify(messageRepository, never()).save(any());
    }
}
