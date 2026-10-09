package com.codegym.locketclone.user;

import com.codegym.locketclone.common.exception.AppException;
import com.codegym.locketclone.common.exception.ErrorCode;
import com.codegym.locketclone.security.service.UserPrincipal;
import com.codegym.locketclone.user.dto.PublicUserProfileResponse;
import com.codegym.locketclone.user.dto.UpdatePersonalInfoRequest;
import com.codegym.locketclone.user.dto.UpdateProfileRequest;
import com.codegym.locketclone.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Tag(name = "Users", description = "Quản lý hồ sơ người dùng, onboarding, thông tin cá nhân và cập nhật ảnh đại diện (Garage S3)")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @Operation(summary = "Lấy thông tin tài khoản hiện tại", description = "Trả về thông tin chi tiết của người dùng đang đăng nhập.")
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        return ResponseEntity.ok(userService.getCurrentUser(requireAuthenticatedUser(userPrincipal).getId()));
    }

    @Operation(summary = "Lấy thông tin cá nhân trong Settings", description = "Lấy thông tin họ tên, username, avatar của người dùng hiện tại.")
    @GetMapping("/me/settings/personal-info")
    public ResponseEntity<UserResponse> getCurrentUserPersonalInfo(
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        return ResponseEntity.ok(userService.getCurrentUser(requireAuthenticatedUser(userPrincipal).getId()));
    }

    @Operation(summary = "Cập nhật hồ sơ Onboarding", description = "Cập nhật họ tên, username trong bước onboarding sau khi đăng ký tài khoản.")
    @PatchMapping("/me/profile")
    public ResponseEntity<UserResponse> updateCurrentUserProfile(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        return ResponseEntity.ok(
                userService.updateCurrentUserProfile(requireAuthenticatedUser(userPrincipal).getId(), request)
        );
    }

    @Operation(summary = "Cập nhật thông tin cá nhân", description = "Cập nhật họ, tên, username hoặc link avatar trong phần Cài đặt.")
    @PatchMapping("/me/settings/personal-info")
    public ResponseEntity<UserResponse> updateCurrentUserPersonalInfo(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody UpdatePersonalInfoRequest request
    ) {
        return ResponseEntity.ok(
                userService.updatePersonalInfo(requireAuthenticatedUser(userPrincipal).getId(), request)
        );
    }

    @Operation(summary = "Tải lên và cập nhật ảnh đại diện mới", description = "Upload file ảnh đại diện (JPEG/PNG/WEBP). Ảnh tự động được nén, tạo thumbnail và lưu trữ an toàn trên Garage S3.")
    @PatchMapping(value = "/me/settings/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UserResponse> updateCurrentUserAvatar(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam("file") MultipartFile file
    ) {
        UserPrincipal currentUser = requireAuthenticatedUser(userPrincipal);
        return ResponseEntity.ok(userService.updateAvatar(currentUser.getId(), file));
    }

    @Operation(summary = "Lấy thông tin người dùng theo ID", description = "Xem thông tin cơ bản công khai của một người dùng theo UUID (ẩn email cá nhân).")
    @GetMapping("/{id}")
    public ResponseEntity<PublicUserProfileResponse> getUserById(@PathVariable UUID id) {
        PublicUserProfileResponse userResponse = userService.getUserById(id);
        return new ResponseEntity<>(userResponse, HttpStatus.OK);
    }


    private UserPrincipal requireAuthenticatedUser(UserPrincipal userPrincipal) {
        if (userPrincipal == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        return userPrincipal;
    }
}
