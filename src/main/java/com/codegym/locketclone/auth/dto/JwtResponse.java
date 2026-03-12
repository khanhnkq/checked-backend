package com.codegym.locketclone.auth.dto;

import java.util.UUID;

public record JwtResponse(
        String token,
        String type,
        UUID id,
        String email,
        String username,
        Boolean isVerified,
        Boolean profileCompleted,
        String displayName,
        String avatarUrl,
        OnboardingStep nextStep
) {
}
