package com.codegym.locketclone.photo;

import com.codegym.locketclone.photo.dto.PhotoResponse;
import com.codegym.locketclone.photo.dto.UpdatePhotoExpenseRequest;
import com.codegym.locketclone.security.service.UserPrincipal;
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

@RestController
@RequestMapping("/api/v1/photos")
@RequiredArgsConstructor
public class PhotoController {
    private final PhotoService photoService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PhotoResponse> uploadPhoto(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "caption", required = false) String caption,
            @RequestParam(value = "amount", required = false) BigDecimal amount,
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
                note,
                categoryId,
                effectiveScope,
                recipientIds,
                takenAt,
                currentUser.getId()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping({"/my-photos", "/me"})
    public ResponseEntity<Page<PhotoResponse>> getMyPhotos(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<PhotoResponse> myPhotos = photoService.getMyPhotos(currentUser.getId(), pageable);
        return ResponseEntity.status(HttpStatus.OK).body(myPhotos);
    }

    @GetMapping("/{photoId}")
    public ResponseEntity<PhotoResponse> getPhotoDetail(
            @PathVariable UUID photoId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        PhotoResponse photo = photoService.getPhotoDetail(currentUser.getId(), photoId);
        return ResponseEntity.status(HttpStatus.OK).body(photo);
    }

    @GetMapping("/feed")
    public ResponseEntity<Slice<PhotoResponse>> getFeedPhotos(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Slice<PhotoResponse> feedPhotos = photoService.getFeedPhotos(currentUser.getId(), pageable);
        return ResponseEntity.status(HttpStatus.OK).body(feedPhotos);
    }

    @PatchMapping("/{photoId}/expense")
    public ResponseEntity<PhotoResponse> updatePhotoExpense(
            @PathVariable UUID photoId,
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody UpdatePhotoExpenseRequest request
    ) {
        PhotoResponse response = photoService.updatePhotoExpense(currentUser.getId(), photoId, request);
        return ResponseEntity.ok(response);
    }
}
