package com.codegym.locketclone.photo;

import com.codegym.locketclone.photo.dto.PhotoResponse;
import com.codegym.locketclone.photo.dto.PhotoReactionResponse;
import com.codegym.locketclone.photo.dto.PhotoReactionSummaryResponse;
import com.codegym.locketclone.photo.dto.UpsertPhotoReactionRequest;
import com.codegym.locketclone.photo.dto.UpdatePhotoExpenseRequest;
import com.codegym.locketclone.photo.dto.UpdatePhotoTransactionRequest;
import com.codegym.locketclone.security.service.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Slice;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Tag(name = "Photos", description = "Chia sẻ khoảnh khắc ảnh kèm giao dịch chi tiêu, feed ảnh bạn bè, cảm xúc (reactions)")
@RestController
@RequestMapping("/api/v1/photos")
@RequiredArgsConstructor
public class PhotoController {
    private final PhotoService photoService;

    @Operation(summary = "Đăng tải ảnh khoảnh khắc mới", description = "Upload ảnh (JPEG/PNG/WEBP) kèm thông tin tài chính (số tiền, danh mục, loại giao dịch, ghi chú) và phạm vi bạn bè nhận ảnh (ALL_FRIENDS hoặc SELECTED_FRIENDS).")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PhotoResponse> uploadPhoto(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "caption", required = false) String caption,
            @RequestParam(value = "amount", required = false) BigDecimal amount,
            @RequestParam(value = "transactionType", required = false) String transactionType,
            @RequestParam(value = "note", required = false) String note,
            @RequestParam(value = "categoryId", required = false) UUID categoryId,
            @RequestParam(value = "recipientScope", required = false) RecipientScope recipientScope,
            @RequestParam(value = "audienceMode", required = false) RecipientScope audienceMode,
            @RequestParam(value = "recipientIds", required = false) List<UUID> recipientIds,
            @RequestParam(value = "takenAt", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime takenAt,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        RecipientScope effectiveScope = recipientScope != null
                ? recipientScope
                : (audienceMode != null ? audienceMode : RecipientScope.ALL_FRIENDS);

        PhotoResponse response = photoService.uploadPhoto(
                file,
                caption,
                amount,
                transactionType,
                note,
                categoryId,
                effectiveScope,
                recipientIds,
                takenAt,
                currentUser.getId()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Lấy danh sách ảnh của tôi", description = "Phân trang danh sách ảnh mà người dùng hiện tại đã đăng tải.")
    @GetMapping({"/my-photos", "/me"})
    public ResponseEntity<Page<PhotoResponse>> getMyPhotos(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<PhotoResponse> myPhotos = photoService.getMyPhotos(currentUser.getId(), pageable);
        return ResponseEntity.status(HttpStatus.OK).body(myPhotos);
    }

    @Operation(summary = "Xem chi tiết một ảnh", description = "Lấy thông tin chi tiết một bức ảnh bao gồm người gửi, danh mục, số tiền, ghi chú.")
    @GetMapping("/{photoId}")
    public ResponseEntity<PhotoResponse> getPhotoDetail(
            @PathVariable UUID photoId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        PhotoResponse photo = photoService.getPhotoDetail(currentUser.getId(), photoId);
        return ResponseEntity.status(HttpStatus.OK).body(photo);
    }

    @Operation(summary = "Lấy feed ảnh bạn bè", description = "Cuộn vô tận (Slice) danh sách ảnh nhận được từ bạn bè hoặc lọc theo từng bạn cụ thể.")
    @GetMapping("/feed")
    public ResponseEntity<Slice<PhotoResponse>> getFeedPhotos(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam(value = "friendId", required = false) UUID friendId,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Slice<PhotoResponse> feedPhotos = photoService.getFeedPhotos(currentUser.getId(), friendId, pageable);
        return ResponseEntity.status(HttpStatus.OK).body(feedPhotos);
    }

    @Operation(summary = "Cập nhật chi phí gắn với ảnh", description = "Chỉnh sửa số tiền, ghi chú, danh mục tài chính của bức ảnh.")
    @PatchMapping("/{photoId}/expense")
    public ResponseEntity<PhotoResponse> updatePhotoExpense(
            @PathVariable UUID photoId,
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody UpdatePhotoExpenseRequest request
    ) {
        PhotoResponse response = photoService.updatePhotoExpense(currentUser.getId(), photoId, request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Thả cảm xúc / reaction vào ảnh", description = "Thêm hoặc đổi reaction (LIKE, LOVE, HAHA, WOW, SAD, ANGRY).")
    @PutMapping("/{photoId}/reactions/me")
    public ResponseEntity<PhotoReactionResponse> upsertMyReaction(
            @PathVariable UUID photoId,
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody UpsertPhotoReactionRequest request
    ) {
        PhotoReactionResponse response = photoService.upsertReaction(currentUser.getId(), photoId, request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Xóa cảm xúc đã thả", description = "Gỡ bỏ reaction của người dùng hiện tại khỏi bức ảnh.")
    @DeleteMapping("/{photoId}/reactions/me")
    public ResponseEntity<Void> removeMyReaction(
            @PathVariable UUID photoId,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        photoService.removeReaction(currentUser.getId(), photoId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Thống kê cảm xúc của ảnh", description = "Tổng hợp số lượng reaction theo từng loại và danh sách người thả cảm xúc.")
    @GetMapping("/{photoId}/reactions/summary")
    public ResponseEntity<PhotoReactionSummaryResponse> getReactionSummary(
            @PathVariable UUID photoId,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        PhotoReactionSummaryResponse response = photoService.getReactionSummary(currentUser.getId(), photoId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Cập nhật toàn bộ thông tin giao dịch ảnh", description = "Chỉnh sửa caption, số tiền, loại giao dịch, danh mục, thời gian của ảnh.")
    @PatchMapping("/{photoId}/transaction")
    public ResponseEntity<PhotoResponse> updatePhotoTransaction(
            @PathVariable UUID photoId,
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody UpdatePhotoTransactionRequest request
    ) {
        PhotoResponse response = photoService.updatePhotoTransaction(currentUser.getId(), photoId, request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Xóa ảnh / giao dịch", description = "Đánh dấu xóa ảnh (DELETED) và tự động xóa file lưu trữ trên Garage S3.")
    @DeleteMapping("/{photoId}")
    public ResponseEntity<Void> deleteTransaction(
            @PathVariable UUID photoId,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        photoService.deleteTransaction(currentUser.getId(), photoId);
        return ResponseEntity.noContent().build();
    }
}
