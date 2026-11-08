package com.codegym.locketclone.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Yêu cầu gửi lại mã OTP xác minh tài khoản")
public record ResendOtpRequest(
        @Schema(description = "Email đã đăng ký", example = "user@example.com")
        @NotBlank(message = "Email không được để trống")
        @Email(message = "Email không đúng định dạng")
        String email
) {
}
