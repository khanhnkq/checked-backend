package com.codegym.locketclone.photo.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record PhotoReactorResponse(
        UUID userId,
        String displayName,
        String avatarUrl,
        String type,
        LocalDateTime reactedAt
) {
}
