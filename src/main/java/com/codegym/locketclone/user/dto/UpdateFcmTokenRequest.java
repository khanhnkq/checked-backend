package com.codegym.locketclone.user.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateFcmTokenRequest(
        @NotBlank(message = "FCM Token không được để trống")
        String fcmToken) {
}
