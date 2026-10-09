package com.codegym.locketclone.user;

import com.codegym.locketclone.user.dto.PublicUserProfileResponse;
import com.codegym.locketclone.user.dto.UpdateProfileRequest;
import com.codegym.locketclone.user.dto.UpdatePersonalInfoRequest;
import com.codegym.locketclone.user.dto.UserResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface UserService {
    // Lấy thông tin công khai người dùng qua ID (không kèm email cá nhân)
    PublicUserProfileResponse getUserById(UUID id);


    UserResponse getCurrentUser(UUID userId);

    UserResponse updateCurrentUserProfile(UUID userId, UpdateProfileRequest request);

    UserResponse updatePersonalInfo(UUID userId, UpdatePersonalInfoRequest request);

    UserResponse updateAvatar(UUID userId, MultipartFile file);
}