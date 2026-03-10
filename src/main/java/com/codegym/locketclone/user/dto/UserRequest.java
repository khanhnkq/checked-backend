package com.codegym.locketclone.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Builder;


@Builder
public record UserRequest(
        @NotBlank(message = "Số điện thoại không được để trống")
        @Pattern(regexp = "^\\d{10}$", message = "Số điện thoại phải có 10 chữ số")
        String phoneNumber,

        @NotBlank(message = "Tên đăng nhập không được để trống")
        String username,

        @NotBlank(message = "Mật khẩu không được để trống")
        String password,

        // Ép buộc người dùng phải gửi displayName hợp lệ
        @NotBlank(message = "Tên hiển thị không được để trống")
        String displayName,

        String fcmToken // Có thể optional
) {
}