package com.codegym.locketclone.photo.consumer;

import com.codegym.locketclone.calorie.CalorieService;
import com.codegym.locketclone.common.config.CacheConfig;
import com.codegym.locketclone.common.config.RabbitMQConfig;
import com.codegym.locketclone.expense.ExpenseService;
import com.codegym.locketclone.photo.Photo;
import com.codegym.locketclone.photo.PhotoRepository;
import com.codegym.locketclone.photo.PhotoStatus;
import com.codegym.locketclone.photo.dto.FeedFanoutEvent;
import com.codegym.locketclone.photo.dto.PhotoUploadEvent;
import com.codegym.locketclone.storage.StorageService;
import com.codegym.locketclone.storage.UploadedFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Component
@Lazy(false)
@Slf4j
@RequiredArgsConstructor
public class PhotoProcessingConsumer {

    private final PhotoRepository photoRepository;
    private final StorageService storageService;

    @Autowired(required = false)
    private RabbitTemplate rabbitTemplate;

    @Autowired(required = false)
    private CalorieService calorieService;

    @Autowired(required = false)
    private ExpenseService expenseService;

    @Autowired(required = false)
    private CacheManager cacheManager;

    @RabbitListener(
            queues = RabbitMQConfig.PHOTO_UPLOAD_QUEUE,
            concurrency = "${rabbitmq.worker.photo-concurrency:1}"
    )
    @Transactional
    public void processPhotoUpload(PhotoUploadEvent event) {
        if (event == null || event.photoId() == null || event.tempKey() == null) {
            log.warn("[PHOTO-WORKER] Bỏ qua PhotoUploadEvent rỗng hoặc thiếu thông tin cần thiết: {}", event);
            return;
        }

        UUID photoId = event.photoId();
        UUID senderId = event.senderId();
        String tempKey = event.tempKey();

        log.info("[PHOTO-WORKER] Bắt đầu xử lý nền cho Photo ID: {}, Sender: {}, TempKey: {}", photoId, senderId, tempKey);

        Photo photo = photoRepository.findById(photoId).orElse(null);
        if (photo == null) {
            log.warn("[PHOTO-WORKER] Không tìm thấy bản ghi Photo ID: {} trong cơ sở dữ liệu. Bỏ qua.", photoId);
            return;
        }

        if (photo.getStatus() == PhotoStatus.READY) {
            log.info("[PHOTO-WORKER] Photo ID: {} đã ở trạng thái READY. Bỏ qua.", photoId);
            return;
        }

        if (photo.getStatus() == PhotoStatus.DELETED) {
            log.info("[PHOTO-WORKER] Photo ID: {} đã bị người dùng xóa. Tiến hành xóa file tạm và dừng.", photoId);
            storageService.deleteFile(tempKey);
            return;
        }

        UploadedFile uploadedFile;
        try {
            uploadedFile = storageService.processAndFinalizePhoto(tempKey);
        } catch (Exception ex) {
            log.error("[PHOTO-WORKER] Lỗi trong quá trình nén và tải ảnh lên S3 cho Photo ID {}: {}", photoId, ex.getMessage(), ex);
            markPhotoAsFailed(photo);
            throw new RuntimeException("Xử lý ảnh nền thất bại cho Photo ID: " + photoId, ex);
        }

        // Cập nhật thông tin ảnh hoàn chỉnh vào DB
        photo.setImageUrl(uploadedFile.secureUrl());
        photo.setThumbnailUrl(uploadedFile.thumbnailUrl());
        photo.setPublicId(uploadedFile.key());
        photo.setMimeType(uploadedFile.mimeType());
        photo.setFileSize(uploadedFile.bytes());
        photo.setWidth(uploadedFile.width());
        photo.setHeight(uploadedFile.height());
        photo.setStatus(PhotoStatus.READY);

        Photo savedPhoto = photoRepository.save(photo);
        log.info("[PHOTO-WORKER] Cập nhật Photo ID: {} thành công sang trạng thái READY", savedPhoto.getId());

        // Kích hoạt ghi nhật ký calo nếu có
        triggerCalorieLogIfPresent(savedPhoto, event);

        // Invalidate các phân vùng cache liên quan
        evictRelatedCaches(senderId, savedPhoto);

        // Kích hoạt Feed Fan-out làm ấm cache feed bạn bè
        triggerFeedFanoutIfPresent(savedPhoto, event);

        log.info("[PHOTO-WORKER] Hoàn tất toàn bộ quy trình xử lý nền cho Photo ID: {}", savedPhoto.getId());
    }

    private void triggerFeedFanoutIfPresent(Photo photo, PhotoUploadEvent event) {
        if (rabbitTemplate != null) {
            try {
                FeedFanoutEvent fanoutEvent = new FeedFanoutEvent(
                        photo.getId(),
                        photo.getSender().getId(),
                        event.recipientScope(),
                        event.recipientIds()
                );
                rabbitTemplate.convertAndSend(
                        RabbitMQConfig.CHECKED_DIRECT_EXCHANGE,
                        RabbitMQConfig.FEED_FANOUT_ROUTING_KEY,
                        fanoutEvent
                );
                log.info("[PHOTO-WORKER] Đã xuất bản FeedFanoutEvent vào RabbitMQ cho Photo ID: {}", photo.getId());
            } catch (Exception ex) {
                log.warn("[PHOTO-WORKER] Lỗi khi xuất bản FeedFanoutEvent cho Photo ID {}: {}", photo.getId(), ex.getMessage());
            }
        }
    }

    private void markPhotoAsFailed(Photo photo) {
        try {
            photo.setStatus(PhotoStatus.FAILED);
            photoRepository.save(photo);
            log.warn("[PHOTO-WORKER] Đã đánh dấu Photo ID: {} sang trạng thái FAILED", photo.getId());
        } catch (Exception e) {
            log.error("[PHOTO-WORKER] Không thể cập nhật trạng thái FAILED cho Photo ID: {}: {}", photo.getId(), e.getMessage());
        }
    }

    private void triggerCalorieLogIfPresent(Photo photo, PhotoUploadEvent event) {
        if (calorieService != null && photo.getCalories() != null && photo.getCalories() > 0) {
            try {
                calorieService.createLogFromPhoto(photo, event.mealType());
                log.info("[PHOTO-WORKER] Tự động tạo CalorieLog từ Photo ID: {} thành công", photo.getId());
            } catch (Exception ex) {
                log.warn("[PHOTO-WORKER] Không thể tạo CalorieLog từ Photo ID: {}: {}", photo.getId(), ex.getMessage());
            }
        }
    }

    private void evictRelatedCaches(UUID senderId, Photo photo) {
        evictCache(CacheConfig.MY_PHOTOS_CACHE, senderId);

        if (expenseService != null && photo.getAmount() != null) {
            LocalDateTime dt = photo.getOccurredAt() != null ? photo.getOccurredAt() : photo.getCreatedAt();
            if (dt != null) {
                try {
                    String monthKey = dt.format(DateTimeFormatter.ofPattern("yyyyMM"));
                    expenseService.evictExpenseCaches(senderId, monthKey, dt.getYear());
                } catch (Exception ex) {
                    log.warn("[PHOTO-WORKER] Lỗi khi evict expense cache cho Photo ID {}: {}", photo.getId(), ex.getMessage());
                }
            }
        }

        if (calorieService != null && photo.getCalories() != null && photo.getCalories() > 0) {
            LocalDateTime dt = photo.getOccurredAt() != null ? photo.getOccurredAt() : photo.getCreatedAt();
            if (dt != null) {
                try {
                    calorieService.evictCalorieCaches(senderId, dt.toLocalDate());
                } catch (Exception ex) {
                    log.warn("[PHOTO-WORKER] Lỗi khi evict calorie cache cho Photo ID {}: {}", photo.getId(), ex.getMessage());
                }
            }
        }
    }

    private void evictCache(String cacheName, Object key) {
        if (cacheManager != null && key != null) {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.evict(key);
            }
        }
    }
}
