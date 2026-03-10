package com.codegym.locketclone.photo;

import com.codegym.locketclone.photo.dto.PhotoResponse;
import com.codegym.locketclone.security.service.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/photos")
@RequiredArgsConstructor
public class PhotoController {
    private final PhotoService photoService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PhotoResponse> uploadPhoto(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "caption", required = false) String caption,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        // Gọi service xử lý logic nghiệp vụ
        PhotoResponse response = photoService.uploadPhoto(file, caption, currentUser.getId());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/my-photos")
    public ResponseEntity<Page<PhotoResponse>> getMyPhotos(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<PhotoResponse> myPhotos = photoService.getMyPhotos(currentUser.getId(), pageable);
        return ResponseEntity.status(HttpStatus.OK).body(myPhotos);

    }

    @GetMapping("/feed")
    public ResponseEntity<Page<PhotoResponse>> getFeedPhotos(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<PhotoResponse> myPhotos = photoService.getFeedPhotos(currentUser.getId(), pageable);
        return ResponseEntity.status(HttpStatus.OK).body(myPhotos);

    }
}
