package com.codegym.locketclone.user;

import com.codegym.locketclone.auth.dto.RegisterRequest;
import com.codegym.locketclone.user.dto.UserResponse;
import java.util.UUID;

public interface UserService {
    // Tạo mới người dùng (Đăng ký)
    UserResponse createUser(RegisterRequest request);

    // Lấy thông tin chi tiết người dùng qua ID
    UserResponse getUserById(UUID id);

    UserResponse getUserByPhoneNumber(String phoneNumber);

    // Cập nhật FCM Token để gửi thông báo Firebase
    void updateFcmToken(UUID userId, String fcmToken);
}