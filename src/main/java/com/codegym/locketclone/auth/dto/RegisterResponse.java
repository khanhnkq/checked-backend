package com.codegym.locketclone.auth.dto;

public record RegisterResponse(
        String message,
        String email,
        String nextStep
) {
}

