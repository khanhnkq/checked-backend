package com.codegym.locketclone.photo.consumer;

import com.codegym.locketclone.common.config.CacheConfig;
import com.codegym.locketclone.common.config.RabbitMQConfig;
import com.codegym.locketclone.friendship.FriendshipRepository;
import com.codegym.locketclone.photo.PhotoService;
import com.codegym.locketclone.photo.RecipientScope;
import com.codegym.locketclone.photo.dto.FeedFanoutEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Worker xử lý sự kiện lan tỏa (Fan-out) và làm ấm (Pre-warm) bộ nhớ đệm Feed
 * khi một ảnh mới được chuyển sang trạng thái READY.
 */
@Component
@Lazy(false)
@Slf4j
@RequiredArgsConstructor
public class FeedFanoutConsumer {

    private final FriendshipRepository friendshipRepository;
    private final PhotoService photoService;

    @Autowired(required = false)
    private CacheManager cacheManager;

    @RabbitListener(
            queues = RabbitMQConfig.FEED_FANOUT_QUEUE,
            concurrency = "${rabbitmq.worker.fanout-concurrency:1}"
    )
    public void processFeedFanout(FeedFanoutEvent event) {
        if (event == null || event.photoId() == null || event.senderId() == null) {
            log.warn("[FEED-FANOUT-WORKER] Bỏ qua FeedFanoutEvent rỗng hoặc thiếu thông tin: {}", event);
            return;
        }

        UUID photoId = event.photoId();
        UUID senderId = event.senderId();
        RecipientScope scope = event.recipientScope() != null ? event.recipientScope() : RecipientScope.ALL_FRIENDS;

        log.info("[FEED-FANOUT-WORKER] Bắt đầu xử lý Feed Fan-out cho Photo ID: {}, Sender: {}, Scope: {}",
                photoId, senderId, scope);

        // 1. Dọn dẹp cache cá nhân của sender (My Photos)
        evictSenderCache(senderId);

        // 2. Xác định danh sách bạn bè / người nhận cần cập nhật feed
        List<UUID> targetUserIds = resolveTargetUserIds(senderId, scope, event.recipientIds());
        if (targetUserIds.isEmpty()) {
            log.info("[FEED-FANOUT-WORKER] Không có người nhận nào cho Photo ID: {}. Kết thúc.", photoId);
            return;
        }

        log.info("[FEED-FANOUT-WORKER] Tìm thấy {} người nhận cần làm ấm bộ đệm Feed cho Photo ID: {}",
                targetUserIds.size(), photoId);

        // 3. Tiến hành evict cache cũ và gọi làm ấm trước (Pre-warm) trang 0 cho từng người nhận
        int successCount = 0;
        int failureCount = 0;

        for (UUID targetId : targetUserIds) {
            try {
                evictFeedCache(targetId);
                // Gọi getFeedPhotos qua Spring Service proxy để kích hoạt @Cacheable nạp lại dữ liệu mới vào Redis
                photoService.getFeedPhotos(targetId, null, PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt")));
                successCount++;
            } catch (Exception ex) {
                failureCount++;
                log.warn("[FEED-FANOUT-WORKER] Lỗi khi làm ấm Feed cache cho User ID {}: {}", targetId, ex.getMessage());
            }
        }

        log.info("[FEED-FANOUT-WORKER] Hoàn tất Feed Fan-out cho Photo ID: {}. Thành công: {}, Thất bại: {}",
                photoId, successCount, failureCount);
    }

    private List<UUID> resolveTargetUserIds(UUID senderId, RecipientScope scope, List<UUID> recipientIds) {
        if (scope == RecipientScope.SELECTED_FRIENDS && recipientIds != null && !recipientIds.isEmpty()) {
            return new ArrayList<>(recipientIds);
        }
        return friendshipRepository.findAcceptedFriendIds(senderId);
    }

    private void evictFeedCache(UUID userId) {
        if (cacheManager == null) return;
        Cache feedCache = cacheManager.getCache(CacheConfig.FEED_CACHE);
        if (feedCache != null) {
            feedCache.evict(userId);
            feedCache.evict(userId.toString());
        }
    }

    private void evictSenderCache(UUID senderId) {
        if (cacheManager == null) return;
        Cache myPhotosCache = cacheManager.getCache(CacheConfig.MY_PHOTOS_CACHE);
        if (myPhotosCache != null) {
            myPhotosCache.evict(senderId);
            myPhotosCache.evict(senderId.toString());
        }
    }
}
