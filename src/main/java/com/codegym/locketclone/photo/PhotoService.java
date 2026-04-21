package com.codegym.locketclone.photo;

import com.codegym.locketclone.photo.dto.PhotoResponse;
import com.codegym.locketclone.photo.dto.PhotoReactionResponse;
import com.codegym.locketclone.photo.dto.PhotoReactionSummaryResponse;
import com.codegym.locketclone.photo.dto.UpsertPhotoReactionRequest;
import com.codegym.locketclone.photo.dto.UpdatePhotoExpenseRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface PhotoService {

    // Lấy feed ảnh; có thể filter theo 1 người bạn (friendId)
    Slice<PhotoResponse> getFeedPhotos(UUID userId, UUID friendId, Pageable pageable);

    Page<PhotoResponse> getMyPhotos(UUID userId, Pageable pageable);

    PhotoResponse getPhotoDetail(UUID userId, UUID photoId);

    PhotoResponse uploadPhoto(MultipartFile file,
                              String caption,
                              BigDecimal amount,
                              String note,
                              UUID categoryId,
                              RecipientScope recipientScope,
                              List<UUID> recipientIds,
                              LocalDateTime takenAt,
                              UUID senderId);

    PhotoResponse updatePhotoExpense(UUID userId, UUID photoId, UpdatePhotoExpenseRequest request);

    PhotoReactionResponse upsertReaction(UUID userId, UUID photoId, UpsertPhotoReactionRequest request);

    void removeReaction(UUID userId, UUID photoId);

    PhotoReactionSummaryResponse getReactionSummary(UUID userId, UUID photoId);
}