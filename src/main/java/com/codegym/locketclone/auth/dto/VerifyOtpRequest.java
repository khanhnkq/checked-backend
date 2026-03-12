package com.codegym.locketclone.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyOtpRequest(
        @NotBlank(message = "Email không được để trống")
        @Email(message = "Email không đúng định dạng")
        String email,

        @NotBlank(message = "OTP không được để trống")
        @Pattern(regexp = "^\\d{6}$", message = "OTP phải gồm đúng 6 chữ số")
        String otp
) {
}

