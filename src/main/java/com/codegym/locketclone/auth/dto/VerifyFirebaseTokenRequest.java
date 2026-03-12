package com.codegym.locketclone.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifyFirebaseTokenRequest(
        @NotBlank(message = "Firebase token không được để trống")
        String firebaseToken
) {}
