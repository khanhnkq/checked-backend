package com.codegym.locketclone.infra;

import com.codegym.locketclone.LocketCloneApplication;
import com.codegym.locketclone.chat.document.Conversation;
import com.codegym.locketclone.chat.dto.request.CreateConversationRequest;
import com.codegym.locketclone.chat.dto.request.SendMessageRequest;
import com.codegym.locketclone.chat.dto.response.ConversationResponse;
import com.codegym.locketclone.chat.dto.response.MessageResponse;
import com.codegym.locketclone.chat.dto.response.UnreadTotalResponse;
import com.codegym.locketclone.chat.repository.ConversationRepository;
import com.codegym.locketclone.chat.repository.MessageRepository;
import com.codegym.locketclone.chat.service.ChatService;
import com.codegym.locketclone.common.config.CacheConfig;
import com.codegym.locketclone.friendship.Friendship;
import com.codegym.locketclone.friendship.FriendshipRepository;
import com.codegym.locketclone.friendship.FriendshipStatus;
import com.codegym.locketclone.user.User;
import com.codegym.locketclone.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = LocketCloneApplication.class)
class ChatCacheTest {

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private ChatService chatService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FriendshipRepository friendshipRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    private final List<UUID> createdUserIds = new ArrayList<>();
    private final List<UUID> createdFriendshipIds = new ArrayList<>();
    private final List<String> createdConversationIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        Cache unreadCache = cacheManager.getCache(CacheConfig.CHAT_UNREAD_CACHE);
        Cache conversationsCache = cacheManager.getCache(CacheConfig.CHAT_CONVERSATIONS_CACHE);

        for (String convId : createdConversationIds) {
            try {
                mongoTemplate.remove(org.springframework.data.mongodb.core.query.Query.query(
                        org.springframework.data.mongodb.core.query.Criteria.where("conversationId").is(convId)),
                        com.codegym.locketclone.chat.document.Message.class);
            } catch (Exception ignored) {}
            try { conversationRepository.deleteById(convId); } catch (Exception ignored) {}
        }
        createdConversationIds.clear();

        for (UUID friendshipId : createdFriendshipIds) {
            try { friendshipRepository.deleteById(friendshipId); } catch (Exception ignored) {}
        }
        createdFriendshipIds.clear();

        for (UUID userId : createdUserIds) {
            if (unreadCache != null) unreadCache.evictIfPresent(userId);
            if (conversationsCache != null) conversationsCache.evictIfPresent(userId);
            try { userRepository.deleteById(userId); } catch (Exception ignored) {}
        }
        createdUserIds.clear();
    }

    private User createTestUser(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = User.builder()
                .username(prefix + "_" + suffix)
                .email(prefix + "_" + suffix + "@example.com")
                .password("$2a$10$encodedpassword")
                .firstName("Chat")
                .lastName("User")
                .isVerified(true)
                .build();
        User saved = userRepository.save(user);
        createdUserIds.add(saved.getId());
        return saved;
    }

    private void establishFriendship(User u1, User u2) {
        Friendship f = Friendship.builder()
                .user(u1)
                .friend(u2)
                .status(FriendshipStatus.ACCEPTED)
                .build();
        Friendship saved = friendshipRepository.save(f);
        createdFriendshipIds.add(saved.getId());
    }

    @Test
    @DisplayName("PR5 - Partition 5: Cache Badge Tin Nhắn Chưa Đọc (chat_unread) hoạt động chuẩn xác")
    void testChatUnreadCountCache_HitAndEviction() {
        User userA = createTestUser("chat_a");
        User userB = createTestUser("chat_b");
        establishFriendship(userA, userB);

        Cache unreadCache = cacheManager.getCache(CacheConfig.CHAT_UNREAD_CACHE);
        assertNotNull(unreadCache);

        unreadCache.evict(userB.getId());

        // 1. Lần 1: Gọi getTotalUnreadCount -> Lưu vào cache
        UnreadTotalResponse unread1 = chatService.getTotalUnreadCount(userB.getId());
        assertNotNull(unread1);
        assertEquals(0, unread1.getTotalUnread());

        Cache.ValueWrapper wrapper = getCacheWithRetry(unreadCache, userB.getId());
        assertNotNull(wrapper, "Tổng số unread phải được lưu vào Redis");

        // Lần 2: Hit cache
        UnreadTotalResponse unread2 = chatService.getTotalUnreadCount(userB.getId());
        assertEquals(unread1.getTotalUnread(), unread2.getTotalUnread());

        // 2. Tạo cuộc trò chuyện và userA gửi tin nhắn cho userB
        ConversationResponse conv = chatService.getOrCreateConversation(userA.getId(),
                CreateConversationRequest.builder().friendId(userB.getId()).build());
        createdConversationIds.add(conv.getId());

        SendMessageRequest sendMsg = SendMessageRequest.builder()
                .content("Chào bạn, đây là tin nhắn test cache!")
                .build();
        MessageResponse msg = chatService.sendMessage(userA.getId(), conv.getId(), sendMsg);
        assertNotNull(msg);

        // 3. Cache unread của userB phải bị evict khi nhận tin mới
        assertCacheEvicted(unreadCache, userB.getId(), "Cache unread của người nhận phải bị evict khi có tin nhắn mới");

        // Gọi lại unread -> Phải bằng 1
        UnreadTotalResponse unreadAfterMsg = chatService.getTotalUnreadCount(userB.getId());
        assertEquals(1, unreadAfterMsg.getTotalUnread());

        // 4. userB gọi markAsRead -> Cache unread phải bị evict tiếp tục
        chatService.markAsRead(userB.getId(), conv.getId());
        assertCacheEvicted(unreadCache, userB.getId(), "Cache unread phải bị evict khi user đánh dấu đã đọc");

        // Gọi lại unread -> Phải về 0
        UnreadTotalResponse unreadAfterRead = chatService.getTotalUnreadCount(userB.getId());
        assertEquals(0, unreadAfterRead.getTotalUnread());
    }

    @Test
    @DisplayName("PR5 - Partition 5: Cache Danh Sách Cuộc Trò Chuyện (chat_conversations) hoạt động chuẩn xác")
    void testChatConversationsCache_HitAndEviction() {
        User userA = createTestUser("chat_c");
        User userB = createTestUser("chat_d");
        establishFriendship(userA, userB);

        Cache conversationsCache = cacheManager.getCache(CacheConfig.CHAT_CONVERSATIONS_CACHE);
        assertNotNull(conversationsCache);

        conversationsCache.evict(userA.getId());
        conversationsCache.evict(userB.getId());

        // Tạo cuộc trò chuyện
        ConversationResponse conv = chatService.getOrCreateConversation(userA.getId(),
                CreateConversationRequest.builder().friendId(userB.getId()).build());
        createdConversationIds.add(conv.getId());

        // 1. Lần 1: Gọi getMyConversations cho userA -> Lưu vào cache
        List<ConversationResponse> list1 = chatService.getMyConversations(userA.getId());
        assertNotNull(list1);

        Cache.ValueWrapper wrapper = getCacheWithRetry(conversationsCache, userA.getId());
        assertNotNull(wrapper, "Conversations của userA phải được lưu vào Redis");
        assertInstanceOf(List.class, wrapper.get());

        // Lần 2: Hit cache và deserialize an toàn (các trường Instant, LastMessageInfo, FriendSummary)
        List<ConversationResponse> list2 = chatService.getMyConversations(userA.getId());
        assertEquals(list1.size(), list2.size());
        if (!list1.isEmpty()) {
            assertEquals(list1.get(0).getId(), list2.get(0).getId());
        }

        // 2. Gửi tin nhắn -> Phải evict conversations của cả 2 bên
        SendMessageRequest sendMsg = SendMessageRequest.builder()
                .content("Tin nhắn cập nhật preview")
                .build();
        chatService.sendMessage(userA.getId(), conv.getId(), sendMsg);

        assertCacheEvicted(conversationsCache, userA.getId(), "Conversations cache của người gửi phải bị evict khi gửi tin nhắn");
        assertCacheEvicted(conversationsCache, userB.getId(), "Conversations cache của người nhận phải bị evict khi nhận tin nhắn");

        // 3. Đọc lại danh sách sau khi gửi tin nhắn -> Có preview lastMessage
        List<ConversationResponse> updatedList = chatService.getMyConversations(userA.getId());
        assertFalse(updatedList.isEmpty());
        assertNotNull(updatedList.get(0).getLastMessage());
        assertEquals("Tin nhắn cập nhật preview", updatedList.get(0).getLastMessage().getContent());
    }

    private void assertCacheEvicted(Cache cache, Object key, String message) {
        Cache.ValueWrapper wrapper = cache.get(key);
        if (wrapper != null) {
            try {
                Thread.sleep(250);
            } catch (InterruptedException ignored) {}
            wrapper = cache.get(key);
        }
        assertNull(wrapper, message);
    }

    private Cache.ValueWrapper getCacheWithRetry(Cache cache, Object key) {
        Cache.ValueWrapper wrapper = cache.get(key);
        if (wrapper == null) {
            try {
                Thread.sleep(250);
            } catch (InterruptedException ignored) {
            }
            wrapper = cache.get(key);
        }
        return wrapper;
    }
}
