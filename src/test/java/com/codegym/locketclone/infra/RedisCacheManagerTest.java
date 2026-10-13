package com.codegym.locketclone.infra;

import com.codegym.locketclone.LocketCloneApplication;
import com.codegym.locketclone.common.config.CacheConfig;
import com.codegym.locketclone.common.config.CustomCacheErrorHandler;
import com.codegym.locketclone.security.service.UserPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = LocketCloneApplication.class)
class RedisCacheManagerTest {

    @Autowired
    private CacheManager cacheManager;

    @Test
    @DisplayName("Verify CacheManager Bean: Phải là RedisCacheManager đa TTL")
    void testCacheManagerBeanType() {
        assertNotNull(cacheManager, "CacheManager không được null");
        assertInstanceOf(RedisCacheManager.class, cacheManager, "CacheManager phải là instance của RedisCacheManager");
    }

    @Test
    @DisplayName("Verify Redis Cache: Ghi, đọc và xóa đối tượng UserPrincipal qua Jackson Serializer")
    void testUserPrincipalCaching() {
        Cache usersCache = cacheManager.getCache(CacheConfig.USERS_CACHE);
        assertNotNull(usersCache, "Cache 'users' phải tồn tại");

        UUID userId = UUID.randomUUID();
        UserPrincipal principal = new UserPrincipal(
                userId,
                "khanhnkq",
                "khanh@example.com",
                "raw_password_should_not_leak",
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                true,
                "Quốc Khánh",
                "https://cdn.example.com/avatars/khanh.jpg",
                true
        );

        // 1. Ghi đối tượng UserPrincipal vào cache Redis
        usersCache.put(userId, principal);

        // 2. Đọc lại từ cache Redis (chờ socket TLS flush nếu đang chạy full suite)
        Cache.ValueWrapper wrapper = null;
        for (int i = 0; i < 10; i++) {
            wrapper = usersCache.get(userId);
            if (wrapper != null && wrapper.get() != null) break;
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        }
        assertNotNull(wrapper, "Dữ liệu cache đọc về không được null");
        assertNotNull(wrapper.get(), "Nội dung đối tượng trong cache không được null");
        assertInstanceOf(UserPrincipal.class, wrapper.get(), "Đối tượng đọc về phải được deserialize thành UserPrincipal");

        UserPrincipal cachedPrincipal = (UserPrincipal) wrapper.get();
        assertEquals(userId, cachedPrincipal.getId());
        assertEquals("khanhnkq", cachedPrincipal.getUsername());
        assertEquals("khanh@example.com", cachedPrincipal.getEmail());
        assertEquals("Quốc Khánh", cachedPrincipal.getDisplayName());
        assertEquals("https://cdn.example.com/avatars/khanh.jpg", cachedPrincipal.getAvatarUrl());
        assertTrue(cachedPrincipal.isVerified());
        assertTrue(cachedPrincipal.getProfileCompleted());
        assertEquals(1, cachedPrincipal.getAuthorities().size());
        assertEquals("ROLE_USER", cachedPrincipal.getAuthorities().iterator().next().getAuthority());

        // 3. Dọn dẹp cache
        assertTrue(usersCache.evictIfPresent(userId), "evictIfPresent phải trả về true khi xóa key thành công");
        assertNull(usersCache.get(userId), "Cache phải được dọn sạch sau khi evict");
    }

    @Test
    @DisplayName("Verify Redis Cache: Ghi, đọc List<FriendProfileResponse> qua Jackson Serializer")
    void testFriendProfileListCaching() {
        Cache friendsCache = cacheManager.getCache(CacheConfig.FRIENDS_CACHE);
        assertNotNull(friendsCache, "Cache 'friends' phải tồn tại");

        UUID userId = UUID.randomUUID();
        com.codegym.locketclone.friendship.dto.FriendProfileResponse friend = com.codegym.locketclone.friendship.dto.FriendProfileResponse.builder()
                .id(UUID.randomUUID())
                .username("testfriend")
                .firstName("Friend")
                .lastName("Test")
                .displayName("Friend Test")
                .avatarUrl("https://example.com/avatar.jpg")
                .isGoldMember(false)
                .build();

        friendsCache.put(userId, List.of(friend));

        Cache.ValueWrapper wrapper = null;
        for (int i = 0; i < 10; i++) {
            wrapper = friendsCache.get(userId);
            if (wrapper != null && wrapper.get() != null) break;
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        }
        assertNotNull(wrapper, "Wrapper không được null");
        Object val = wrapper.get();
        assertInstanceOf(List.class, val);
        List<?> list = (List<?>) val;
        assertEquals(1, list.size());
        assertInstanceOf(com.codegym.locketclone.friendship.dto.FriendProfileResponse.class, list.get(0));

        assertTrue(friendsCache.evictIfPresent(userId));
        assertNull(friendsCache.get(userId));
    }

    @Test
    @DisplayName("Verify Graceful Degradation: CustomCacheErrorHandler không làm ném lỗi sập app")
    void testCustomCacheErrorHandlerDegradation() {
        CustomCacheErrorHandler errorHandler = new CustomCacheErrorHandler();
        RuntimeException testException = new RuntimeException("Simulated Redis Connection Timeout");

        Cache usersCache = cacheManager.getCache(CacheConfig.USERS_CACHE);

        // Xác nhận cả 4 phương thức xử lý lỗi đều nuốt lỗi an toàn và ghi log WARN
        assertDoesNotThrow(() -> errorHandler.handleCacheGetError(testException, usersCache, "test-key"));
        assertDoesNotThrow(() -> errorHandler.handleCachePutError(testException, usersCache, "test-key", "test-val"));
        assertDoesNotThrow(() -> errorHandler.handleCacheEvictError(testException, usersCache, "test-key"));
        assertDoesNotThrow(() -> errorHandler.handleCacheClearError(testException, usersCache));
    }
}
