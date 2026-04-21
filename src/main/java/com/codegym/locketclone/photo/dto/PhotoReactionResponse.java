package com.codegym.locketclone.photo.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record PhotoReactionResponse(
        UUID photoId,
        UUID userId,
        String type,
        LocalDateTime createdAt
) {
}

