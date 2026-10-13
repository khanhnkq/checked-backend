package com.codegym.locketclone.infra;

import com.codegym.locketclone.LocketCloneApplication;
import com.codegym.locketclone.common.config.CacheConfig;
import com.codegym.locketclone.friendship.Friendship;
import com.codegym.locketclone.friendship.FriendshipRepository;
import com.codegym.locketclone.friendship.FriendshipService;
import com.codegym.locketclone.friendship.FriendshipStatus;
import com.codegym.locketclone.friendship.dto.FriendProfileResponse;
import com.codegym.locketclone.friendship.invite.FriendInviteLink;
import com.codegym.locketclone.friendship.invite.FriendInviteLinkRepository;
import com.codegym.locketclone.friendship.invite.FriendInviteLinkService;
import com.codegym.locketclone.security.service.UserDetailsServiceImpl;
import com.codegym.locketclone.security.service.UserPrincipal;
import com.codegym.locketclone.user.User;
import com.codegym.locketclone.user.UserRepository;
import com.codegym.locketclone.user.UserService;
import com.codegym.locketclone.user.dto.UpdateProfileRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = LocketCloneApplication.class)
@TestPropertySource(properties = {
        "spring.data.redis.host=adapting-moth-229902.upstash.io",
        "spring.data.redis.port=6379",
        "spring.data.redis.password=gQAAAAAAA4IOAAIgcDE3ODJkNjY1ZjRmM2U0Zjk5OTY2YTVjODg5NTAwY2Q1Zg",
        "spring.data.redis.ssl.enabled=true",
        "spring.data.redis.timeout=5000ms",

        "spring.rabbitmq.host=leopard.lmq.cloudamqp.com",
        "spring.rabbitmq.port=5671",
        "spring.rabbitmq.username=kufeoult",
        "spring.rabbitmq.password=mv1YvpuQOsWylToYRguVJyPtCYxdItsj",
        "spring.rabbitmq.virtual-host=kufeoult",
        "spring.rabbitmq.ssl.enabled=true"
})
class UserAndFriendshipCacheTest {

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private UserDetailsServiceImpl userDetailsService;

    @Autowired
    private UserService userService;

    @Autowired
    private FriendshipService friendshipService;

    @Autowired
    private FriendInviteLinkService friendInviteLinkService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FriendshipRepository friendshipRepository;

    @Autowired
    private FriendInviteLinkRepository friendInviteLinkRepository;

    private final List<UUID> createdUserIds = new ArrayList<>();
    private final List<UUID> createdFriendshipIds = new ArrayList<>();
    private final List<UUID> createdLinkIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        Cache usersCache = cacheManager.getCache(CacheConfig.USERS_CACHE);
        Cache friendsCache = cacheManager.getCache(CacheConfig.FRIENDS_CACHE);

        for (UUID linkId : createdLinkIds) {
            try { friendInviteLinkRepository.deleteById(linkId); } catch (Exception ignored) {}
        }
        createdLinkIds.clear();

        for (UUID friendshipId : createdFriendshipIds) {
            try { friendshipRepository.deleteById(friendshipId); } catch (Exception ignored) {}
        }
        createdFriendshipIds.clear();

        for (UUID userId : createdUserIds) {
            if (usersCache != null) usersCache.evictIfPresent(userId);
            if (friendsCache != null) friendsCache.evictIfPresent(userId);
            try { userRepository.deleteById(userId); } catch (Exception ignored) {}
        }
        createdUserIds.clear();
    }

    private User createTestUser(String prefix) {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        User user = User.builder()
                .email(prefix + "_" + unique + "@example.com")
                .username(prefix + "_" + unique)
                .password("$2a$10$encodedpassword")
                .firstName("Test")
                .lastName("User")
                .isVerified(true)
                .build();
        User saved = userRepository.save(user);
        createdUserIds.add(saved.getId());
        return saved;
    }

    @Test
    @DisplayName("Verify @Cacheable trên UserDetailsServiceImpl.loadUserById: Lần 1 nạp cache, lần 2 hit cache")
    void testLoadUserByIdCaching() {
        User user = createTestUser("cache_user");
        UUID userId = user.getId();
        Cache usersCache = cacheManager.getCache(CacheConfig.USERS_CACHE);
        assertNotNull(usersCache);

        // Đảm bảo cache trống ban đầu
        usersCache.evict(userId);
        assertNull(usersCache.get(userId));

        // Lần 1: Gọi method loadUserById -> Spring AOP lưu UserPrincipal vào Redis
        UserDetails principal1 = userDetailsService.loadUserById(userId);
        assertNotNull(principal1);
        assertEquals(user.getUsername(), principal1.getUsername());

        // Kiểm tra Redis cache đã chứa dữ liệu
        Cache.ValueWrapper wrapper = usersCache.get(userId);
        assertNotNull(wrapper, "Redis cache phải chứa key sau lần gọi 1");
        assertInstanceOf(UserPrincipal.class, wrapper.get());

        // Lần 2: Gọi lại -> dữ liệu trả về từ Redis cache
        UserDetails principal2 = userDetailsService.loadUserById(userId);
        assertNotNull(principal2);
        assertEquals(principal1.getUsername(), principal2.getUsername());
    }

    @Test
    @DisplayName("Verify @CacheEvict trên UserServiceImpl.updateCurrentUserProfile: Xóa key cache users")
    void testUpdateProfileEvictsCache() {
        User user = createTestUser("evict_user");
        UUID userId = user.getId();
        Cache usersCache = cacheManager.getCache(CacheConfig.USERS_CACHE);
        assertNotNull(usersCache);

        // Nạp vào cache
        userDetailsService.loadUserById(userId);
        assertNotNull(usersCache.get(userId), "Cache phải có trước khi update profile");

        // Cập nhật profile qua UserService -> Kích hoạt @CacheEvict
        UpdateProfileRequest updateRequest = new UpdateProfileRequest(
                null,
                "UpdatedFirstName",
                "UpdatedLastName",
                null,
                null
        );
        userService.updateCurrentUserProfile(userId, updateRequest);

        // Kiểm tra Redis cache đã bị xóa
        assertNull(usersCache.get(userId), "Cache 'users' phải bị xóa sạch sau khi update profile");
    }

    @Test
    @DisplayName("Verify @Cacheable trên FriendshipServiceImpl.getAllFriends: Cache danh sách bạn bè")
    void testGetAllFriendsCaching() {
        User userA = createTestUser("friend_a");
        User userB = createTestUser("friend_b");

        Friendship friendship = friendshipRepository.save(Friendship.builder()
                .user(userA)
                .friend(userB)
                .status(FriendshipStatus.ACCEPTED)
                .build());
        createdFriendshipIds.add(friendship.getId());

        Cache friendsCache = cacheManager.getCache(CacheConfig.FRIENDS_CACHE);
        assertNotNull(friendsCache);
        friendsCache.evict(userA.getId());

        // Lần 1: Query DB và nạp vào Redis
        List<FriendProfileResponse> friends1 = friendshipService.getAllFriends(userA.getId());
        assertEquals(1, friends1.size());
        assertEquals(userB.getUsername(), friends1.get(0).username());

        // Kiểm tra cache Redis có chứa List
        Cache.ValueWrapper wrapper = friendsCache.get(userA.getId());
        assertNotNull(wrapper, "Cache 'friends' phải có giá trị");
        assertInstanceOf(List.class, wrapper.get());

        // Lần 2: Hit cache
        List<FriendProfileResponse> friends2 = friendshipService.getAllFriends(userA.getId());
        assertEquals(1, friends2.size());
        assertEquals(friends1.get(0).id(), friends2.get(0).id());
    }

    @Test
    @DisplayName("Verify Evict 2 chiều khi acceptByToken: Xóa cache friends của cả 2 phía")
    void testAcceptInviteLinkEvictsBothFriendsCache() {
        User owner = createTestUser("owner_invite");
        User invitee = createTestUser("invitee");

        Cache friendsCache = cacheManager.getCache(CacheConfig.FRIENDS_CACHE);
        assertNotNull(friendsCache);

        // Giả lập nạp cache bạn bè cho cả 2 user trước khi kết bạn
        FriendProfileResponse dummyProfile = FriendProfileResponse.builder()
                .id(UUID.randomUUID())
                .username("dummy")
                .firstName("Dummy")
                .lastName("Friend")
                .displayName("Dummy Friend")
                .isGoldMember(false)
                .build();
        friendsCache.put(owner.getId(), List.of(dummyProfile));
        friendsCache.put(invitee.getId(), List.of(dummyProfile));
        assertNotNull(friendsCache.get(owner.getId()));
        assertNotNull(friendsCache.get(invitee.getId()));

        // Tạo invite link
        String token = "test-token-" + UUID.randomUUID();
        FriendInviteLink link = friendInviteLinkRepository.save(FriendInviteLink.builder()
                .owner(owner)
                .token(token)
                .maxUses(50)
                .usedCount(0)
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build());
        createdLinkIds.add(link.getId());

        // Invitee chấp nhận invite link qua service
        var response = friendInviteLinkService.acceptByToken(invitee.getId(), token);
        assertNotNull(response);
        createdFriendshipIds.add(response.friendshipId());

        // Kiểm tra cache friends của CẢ 2 PHÍA đã bị xóa sạch (Eviction 2 chiều)
        assertNull(friendsCache.get(owner.getId()), "Cache friends của owner phải bị evict");
        assertNull(friendsCache.get(invitee.getId()), "Cache friends của invitee phải bị evict");
    }
}
