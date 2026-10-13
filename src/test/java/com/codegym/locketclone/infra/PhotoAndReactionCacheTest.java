package com.codegym.locketclone.infra;

import com.codegym.locketclone.LocketCloneApplication;
import com.codegym.locketclone.common.config.CacheConfig;
import com.codegym.locketclone.friendship.Friendship;
import com.codegym.locketclone.friendship.FriendshipRepository;
import com.codegym.locketclone.friendship.FriendshipStatus;
import com.codegym.locketclone.photo.Photo;
import com.codegym.locketclone.photo.PhotoReactionRepository;
import com.codegym.locketclone.photo.PhotoRepository;
import com.codegym.locketclone.photo.PhotoService;
import com.codegym.locketclone.photo.PhotoStatus;
import com.codegym.locketclone.photo.RecipientScope;
import com.codegym.locketclone.photo.dto.PhotoReactionStats;
import com.codegym.locketclone.photo.dto.PhotoReactionSummaryResponse;
import com.codegym.locketclone.photo.dto.PhotoResponse;
import com.codegym.locketclone.photo.dto.UpdatePhotoExpenseRequest;
import com.codegym.locketclone.photo.dto.UpsertPhotoReactionRequest;
import com.codegym.locketclone.user.User;
import com.codegym.locketclone.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = LocketCloneApplication.class)
class PhotoAndReactionCacheTest {

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private PhotoService photoService;

    @Autowired
    private PhotoRepository photoRepository;

    @Autowired
    private PhotoReactionRepository photoReactionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FriendshipRepository friendshipRepository;

    private final List<UUID> createdPhotoIds = new ArrayList<>();
    private final List<UUID> createdUserIds = new ArrayList<>();
    private final List<UUID> createdFriendshipIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        Cache reactionsCache = cacheManager.getCache(CacheConfig.REACTIONS_SUMMARY_CACHE);
        Cache feedCache = cacheManager.getCache(CacheConfig.FEED_CACHE);
        Cache myPhotosCache = cacheManager.getCache(CacheConfig.MY_PHOTOS_CACHE);
        Cache detailCache = cacheManager.getCache(CacheConfig.PHOTO_DETAIL_CACHE);

        if (detailCache != null) {
            for (UUID userId : createdUserIds) {
                for (UUID photoId : createdPhotoIds) {
                    detailCache.evictIfPresent(userId + ":" + photoId);
                }
            }
        }

        for (UUID photoId : createdPhotoIds) {
            if (reactionsCache != null) reactionsCache.evictIfPresent(photoId);
            try { photoReactionRepository.deleteByPhotoId(photoId); } catch (Exception ignored) {}
            try { photoRepository.deleteById(photoId); } catch (Exception ignored) {}
        }
        createdPhotoIds.clear();

        for (UUID friendshipId : createdFriendshipIds) {
            try { friendshipRepository.deleteById(friendshipId); } catch (Exception ignored) {}
        }
        createdFriendshipIds.clear();

        for (UUID userId : createdUserIds) {
            if (feedCache != null) feedCache.evictIfPresent(userId);
            if (myPhotosCache != null) myPhotosCache.evictIfPresent(userId);
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

    private Photo createTestPhoto(User sender, RecipientScope scope) {
        LocalDateTime now = LocalDateTime.now();
        Photo photo = Photo.builder()
                .sender(sender)
                .imageUrl("https://example.com/photo_" + UUID.randomUUID() + ".jpg")
                .caption("Test Photo")
                .amount(new BigDecimal("50000.00"))
                .recipientScope(scope)
                .status(PhotoStatus.READY)
                .takenAt(now)
                .occurredAt(now)
                .createdAt(now)
                .build();
        Photo saved = photoRepository.save(photo);
        createdPhotoIds.add(saved.getId());
        return saved;
    }

    @Test
    @DisplayName("Verify @Cacheable trên Reactions Summary: Lần 1 nạp cache Redis, lần 2 hit cache")
    void testReactionStatsCaching() {
        User sender = createTestUser("sender_react");
        User viewer = createTestUser("viewer_react");

        Friendship friendship = friendshipRepository.save(Friendship.builder()
                .user(sender).friend(viewer).status(FriendshipStatus.ACCEPTED).build());
        createdFriendshipIds.add(friendship.getId());

        Photo photo = createTestPhoto(sender, RecipientScope.ALL_FRIENDS);

        Cache reactionsCache = cacheManager.getCache(CacheConfig.REACTIONS_SUMMARY_CACHE);
        assertNotNull(reactionsCache);
        reactionsCache.evict(photo.getId());

        // Lần 1: Gọi getReactionSummary -> getReactionStats được gọi và nạp vào Redis
        PhotoReactionSummaryResponse res1 = photoService.getReactionSummary(viewer.getId(), photo.getId());
        assertNotNull(res1);
        assertEquals(0L, res1.totalCount());

        // Xác nhận Redis chứa key reactions_summary::{photoId}
        Cache.ValueWrapper wrapper = reactionsCache.get(photo.getId());
        assertNotNull(wrapper, "Redis cache phải chứa key reactions_summary sau lần gọi 1");
        assertInstanceOf(PhotoReactionStats.class, wrapper.get());

        // Lần 2: Hit cache
        PhotoReactionSummaryResponse res2 = photoService.getReactionSummary(viewer.getId(), photo.getId());
        assertNotNull(res2);
        assertEquals(res1.photoId(), res2.photoId());
        assertEquals(res1.totalCount(), res2.totalCount());
    }

    @Test
    @DisplayName("Verify @CacheEvict khi Thả Reaction và Xóa Reaction: Xóa key cache reactions_summary")
    void testReactionUpsertAndRemoveEviction() {
        User sender = createTestUser("sender_evict");
        User viewer = createTestUser("viewer_evict");

        Friendship friendship = friendshipRepository.save(Friendship.builder()
                .user(sender).friend(viewer).status(FriendshipStatus.ACCEPTED).build());
        createdFriendshipIds.add(friendship.getId());

        Photo photo = createTestPhoto(sender, RecipientScope.ALL_FRIENDS);
        Cache reactionsCache = cacheManager.getCache(CacheConfig.REACTIONS_SUMMARY_CACHE);
        assertNotNull(reactionsCache);

        // Nạp cache
        photoService.getReactionSummary(viewer.getId(), photo.getId());
        Cache.ValueWrapper reactionWrapper = null;
        for (int i = 0; i < 10; i++) {
            reactionWrapper = reactionsCache.get(photo.getId());
            if (reactionWrapper != null && reactionWrapper.get() != null) break;
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        }
        assertNotNull(reactionWrapper, "Cache reactions_summary phải có dữ liệu");

        // 1. Thả reaction -> Kích hoạt @CacheEvict
        photoService.upsertReaction(viewer.getId(), photo.getId(), new UpsertPhotoReactionRequest("LIKE"));
        assertCacheEvicted(reactionsCache, photo.getId(), "Cache phải bị evict sau khi upsertReaction");

        // Nạp lại cache và kiểm tra count = 1
        PhotoReactionSummaryResponse summaryAfterLike = photoService.getReactionSummary(viewer.getId(), photo.getId());
        assertEquals(1L, summaryAfterLike.totalCount());
        assertEquals("LIKE", summaryAfterLike.myReaction());

        Cache.ValueWrapper reloadedWrapper = null;
        for (int i = 0; i < 15; i++) {
            reloadedWrapper = reactionsCache.get(photo.getId());
            if (reloadedWrapper != null && reloadedWrapper.get() != null) break;
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        }
        assertNotNull(reloadedWrapper, "Cache phải được nạp lại");

        // 2. Xóa reaction -> Kích hoạt @CacheEvict
        photoService.removeReaction(viewer.getId(), photo.getId());
        assertCacheEvicted(reactionsCache, photo.getId(), "Cache phải bị evict sau khi removeReaction");
    }

    @Test
    @DisplayName("Verify @Cacheable trên Photo Detail: Cache chi tiết ảnh và evict khi cập nhật/xóa")
    void testPhotoDetailCachingAndEviction() {
        User sender = createTestUser("sender_detail");
        Photo photo = createTestPhoto(sender, RecipientScope.ALL_FRIENDS);

        Cache detailCache = cacheManager.getCache(CacheConfig.PHOTO_DETAIL_CACHE);
        assertNotNull(detailCache);
        String cacheKey = sender.getId() + ":" + photo.getId();

        detailCache.evict(cacheKey);

        // Lần 1: Nạp cache chi tiết ảnh
        PhotoResponse detail1 = photoService.getPhotoDetail(sender.getId(), photo.getId());
        assertNotNull(detail1);
        assertEquals(photo.getId(), detail1.id());

        // Kiểm tra Redis cache có chứa (hỗ trợ độ trễ mạng cloud Upstash TLS)
        Cache.ValueWrapper wrapper = null;
        for (int i = 0; i < 10; i++) {
            wrapper = detailCache.get(cacheKey);
            if (wrapper != null && wrapper.get() != null) break;
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        }
        assertNotNull(wrapper, "Cache photo_detail phải có dữ liệu");
        assertInstanceOf(PhotoResponse.class, wrapper.get());

        // Lần 2: Hit cache
        PhotoResponse detail2 = photoService.getPhotoDetail(sender.getId(), photo.getId());
        assertEquals(detail1.id(), detail2.id());

        System.out.println("DEBUG DETAIL CACHE BEFORE UPDATE: " + detailCache.get(cacheKey));
        photoService.updatePhotoExpense(sender.getId(), photo.getId(), new UpdatePhotoExpenseRequest(
                new BigDecimal("99000.00"),
                "Updated note",
                null
        ));
        System.out.println("DEBUG DETAIL CACHE AFTER UPDATE: " + detailCache.get(cacheKey));
        assertNull(detailCache.get(cacheKey), "Cache photo_detail phải bị evict khi update expense");
    }

    @Test
    @DisplayName("Verify @Cacheable trên Feed và MyPhotos trang 0: Serialize & Deserialize qua Redis thành công")
    void testFeedAndMyPhotosPage0Caching() {
        User user = createTestUser("feed_user");
        Photo photo = createTestPhoto(user, RecipientScope.ALL_FRIENDS);

        Cache feedCache = cacheManager.getCache(CacheConfig.FEED_CACHE);
        Cache myPhotosCache = cacheManager.getCache(CacheConfig.MY_PHOTOS_CACHE);
        assertNotNull(feedCache);
        assertNotNull(myPhotosCache);

        feedCache.evict(user.getId());
        myPhotosCache.evict(user.getId());

        // 1. Test Feed trang 0 (pageNumber == 0)
        Slice<PhotoResponse> feed1 = photoService.getFeedPhotos(user.getId(), null, PageRequest.of(0, 10));
        assertNotNull(feed1);
        assertFalse(feed1.getContent().isEmpty());

        // Kiểm tra Redis cache Feed (hỗ trợ độ trễ mạng cloud Upstash TLS)
        Cache.ValueWrapper feedWrapper = getCacheWithRetry(feedCache, user.getId());
        assertNotNull(feedWrapper, "Feed trang 0 phải được lưu vào Redis");
        assertInstanceOf(Slice.class, feedWrapper.get());

        // Lần 2: Hit cache Feed và deserialize an toàn
        Slice<PhotoResponse> feed2 = photoService.getFeedPhotos(user.getId(), null, PageRequest.of(0, 10));
        assertEquals(feed1.getContent().size(), feed2.getContent().size());

        // 2. Test MyPhotos trang 0 (pageNumber == 0)
        Page<PhotoResponse> myPhotos1 = photoService.getMyPhotos(user.getId(), PageRequest.of(0, 10));
        assertNotNull(myPhotos1);
        assertFalse(myPhotos1.getContent().isEmpty());

        // Kiểm tra Redis cache MyPhotos (hỗ trợ độ trễ mạng cloud Upstash TLS)
        Cache.ValueWrapper myPhotosWrapper = getCacheWithRetry(myPhotosCache, user.getId());
        assertNotNull(myPhotosWrapper, "MyPhotos trang 0 phải được lưu vào Redis");
        assertInstanceOf(Page.class, myPhotosWrapper.get());

        // Lần 2: Hit cache MyPhotos và deserialize an toàn
        Page<PhotoResponse> myPhotos2 = photoService.getMyPhotos(user.getId(), PageRequest.of(0, 10));
        assertEquals(myPhotos1.getContent().size(), myPhotos2.getContent().size());

        // 3. Xóa ảnh -> Evict cả MyPhotos và Feed
        photoService.deleteTransaction(user.getId(), photo.getId());
        assertCacheEvicted(feedCache, user.getId(), "Feed cache phải bị xóa khi delete transaction");
        assertCacheEvicted(myPhotosCache, user.getId(), "MyPhotos cache phải bị xóa khi delete transaction");
    }

    private void assertCacheEvicted(Cache cache, Object key, String message) {
        Cache.ValueWrapper wrapper = cache.get(key);
        for (int i = 0; i < 15 && wrapper != null; i++) {
            try {
                Thread.sleep(100);
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
