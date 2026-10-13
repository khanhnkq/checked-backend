package com.codegym.locketclone.photo;

import com.codegym.locketclone.common.config.CacheConfig;
import com.codegym.locketclone.photo.dto.PhotoReactionResponse;
import com.codegym.locketclone.photo.dto.PhotoReactionStats;
import com.codegym.locketclone.photo.dto.PhotoReactionSummaryResponse;
import com.codegym.locketclone.photo.dto.PhotoResponse;
import com.codegym.locketclone.photo.dto.UpdatePhotoExpenseRequest;
import com.codegym.locketclone.photo.dto.UpdatePhotoTransactionRequest;
import com.codegym.locketclone.photo.dto.UpsertPhotoReactionRequest;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface PhotoService {

    @Cacheable(value = CacheConfig.FEED_CACHE, key = "#userId", condition = "#p2.pageNumber == 0 and #p1 == null")
    Slice<PhotoResponse> getFeedPhotos(UUID userId, UUID friendId, Pageable pageable);

    @Cacheable(value = CacheConfig.MY_PHOTOS_CACHE, key = "#userId", condition = "#p1.pageNumber == 0")
    Page<PhotoResponse> getMyPhotos(UUID userId, Pageable pageable);

    @Cacheable(value = CacheConfig.PHOTO_DETAIL_CACHE, key = "#userId + ':' + #photoId")
    PhotoResponse getPhotoDetail(UUID userId, UUID photoId);

    default PhotoResponse uploadPhoto(MultipartFile file,
                              String caption,
                              BigDecimal amount,
                              String transactionType,
                              String note,
                              UUID categoryId,
                              RecipientScope recipientScope,
                              List<UUID> recipientIds,
                              LocalDateTime takenAt,
                              UUID senderId) {
        return uploadPhoto(file, caption, amount, transactionType, note, categoryId, recipientScope, recipientIds, takenAt, null, null, null, null, null, null, senderId);
    }

    default PhotoResponse uploadPhoto(MultipartFile file,
                              String caption,
                              BigDecimal amount,
                              String transactionType,
                              String note,
                              UUID categoryId,
                              RecipientScope recipientScope,
                              List<UUID> recipientIds,
                              LocalDateTime takenAt,
                              String locationName,
                              BigDecimal latitude,
                              BigDecimal longitude,
                              String dishName,
                              Integer calories,
                              UUID senderId) {
        return uploadPhoto(file, caption, amount, transactionType, note, categoryId, recipientScope, recipientIds, takenAt, locationName, latitude, longitude, dishName, calories, null, senderId);
    }

    PhotoResponse uploadPhoto(MultipartFile file,
                              String caption,
                              BigDecimal amount,
                              String transactionType,
                              String note,
                              UUID categoryId,
                              RecipientScope recipientScope,
                              List<UUID> recipientIds,
                              LocalDateTime takenAt,
                              String locationName,
                              BigDecimal latitude,
                              BigDecimal longitude,
                              String dishName,
                              Integer calories,
                              com.codegym.locketclone.calorie.MealType mealType,
                              UUID senderId);

    @Caching(evict = {
        @CacheEvict(value = CacheConfig.PHOTO_DETAIL_CACHE, key = "#userId + ':' + #photoId"),
        @CacheEvict(value = CacheConfig.FEED_CACHE, key = "#userId"),
        @CacheEvict(value = CacheConfig.MY_PHOTOS_CACHE, key = "#userId")
    })
    PhotoResponse updatePhotoExpense(UUID userId, UUID photoId, UpdatePhotoExpenseRequest request);

    @Caching(evict = {
        @CacheEvict(value = CacheConfig.PHOTO_DETAIL_CACHE, key = "#userId + ':' + #photoId"),
        @CacheEvict(value = CacheConfig.FEED_CACHE, key = "#userId"),
        @CacheEvict(value = CacheConfig.MY_PHOTOS_CACHE, key = "#userId")
    })
    PhotoResponse updatePhotoTransaction(UUID userId, UUID photoId, UpdatePhotoTransactionRequest request);

    @Caching(evict = {
        @CacheEvict(value = CacheConfig.PHOTO_DETAIL_CACHE, key = "#userId + ':' + #photoId"),
        @CacheEvict(value = CacheConfig.FEED_CACHE, key = "#userId"),
        @CacheEvict(value = CacheConfig.MY_PHOTOS_CACHE, key = "#userId"),
        @CacheEvict(value = CacheConfig.REACTIONS_SUMMARY_CACHE, key = "#photoId")
    })
    void deleteTransaction(UUID userId, UUID photoId);

    @CacheEvict(value = CacheConfig.REACTIONS_SUMMARY_CACHE, key = "#photoId")
    PhotoReactionResponse upsertReaction(UUID userId, UUID photoId, UpsertPhotoReactionRequest request);

    @CacheEvict(value = CacheConfig.REACTIONS_SUMMARY_CACHE, key = "#photoId")
    void removeReaction(UUID userId, UUID photoId);

    PhotoReactionSummaryResponse getReactionSummary(UUID userId, UUID photoId);

    @Cacheable(value = CacheConfig.REACTIONS_SUMMARY_CACHE, key = "#photoId")
    PhotoReactionStats getReactionStats(UUID photoId);
}