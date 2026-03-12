package com.codegym.locketclone.user;

import com.codegym.locketclone.user.dto.UpdateProfileRequest;
import com.codegym.locketclone.user.dto.UserResponse;

import java.util.UUID;

public interface UserService {
    // Lấy thông tin chi tiết người dùng qua ID
    UserResponse getUserById(UUID id);

    UserResponse getCurrentUser(UUID userId);

    UserResponse updateCurrentUserProfile(UUID userId, UpdateProfileRequest request);
}