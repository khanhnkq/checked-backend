package com.codegym.locketclone.user;

import com.codegym.locketclone.common.exception.AppException;
import com.codegym.locketclone.common.exception.ErrorCode;
import com.codegym.locketclone.photo.CloudinaryService;
import com.codegym.locketclone.photo.UploadedImage;
import com.codegym.locketclone.user.dto.UpdatePersonalInfoRequest;
import com.codegym.locketclone.security.service.UserPrincipal;
import com.codegym.locketclone.user.dto.UpdateProfileRequest;
import com.codegym.locketclone.user.dto.UserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final CloudinaryService cloudinaryService;
    private static final Set<String> ALLOWED_AVATAR_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        return ResponseEntity.ok(userService.getCurrentUser(requireAuthenticatedUser(userPrincipal).getId()));
    }

    @GetMapping("/me/settings/personal-info")
    public ResponseEntity<UserResponse> getCurrentUserPersonalInfo(
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        return ResponseEntity.ok(userService.getCurrentUser(requireAuthenticatedUser(userPrincipal).getId()));
    }

    @PatchMapping("/me/profile")
    public ResponseEntity<UserResponse> updateCurrentUserProfile(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        return ResponseEntity.ok(
                userService.updateCurrentUserProfile(requireAuthenticatedUser(userPrincipal).getId(), request)
        );
    }

    @PatchMapping("/me/settings/personal-info")
    public ResponseEntity<UserResponse> updateCurrentUserPersonalInfo(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody UpdatePersonalInfoRequest request
    ) {
        return ResponseEntity.ok(
                userService.updatePersonalInfo(requireAuthenticatedUser(userPrincipal).getId(), request)
        );
    }

    @PatchMapping(value = "/me/settings/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UserResponse> updateCurrentUserAvatar(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam("file") MultipartFile file
    ) {
        UserPrincipal currentUser = requireAuthenticatedUser(userPrincipal);
        validateAvatarFile(file);

        UploadedImage uploadedImage;
        try {
            uploadedImage = cloudinaryService.uploadImage(file);
        } catch (IOException e) {
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }

        UpdatePersonalInfoRequest request = new UpdatePersonalInfoRequest(
                null,
                null,
                null,
                uploadedImage.secureUrl()
        );
        return ResponseEntity.ok(userService.updatePersonalInfo(currentUser.getId(), request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable UUID id) {
        UserResponse userResponse = userService.getUserById(id);
        return new ResponseEntity<>(userResponse, HttpStatus.OK);
    }

    private UserPrincipal requireAuthenticatedUser(UserPrincipal userPrincipal) {
        if (userPrincipal == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        return userPrincipal;
    }

    private void validateAvatarFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_PHOTO_FILE);
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_AVATAR_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new AppException(ErrorCode.INVALID_PHOTO_FILE);
        }
    }
}
