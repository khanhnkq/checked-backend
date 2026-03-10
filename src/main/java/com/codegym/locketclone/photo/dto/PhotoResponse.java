package com.codegym.locketclone.photo.dto;

import com.codegym.locketclone.user.User;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record PhotoResponse(
        UUID id,
        UUID senderId,
        String imageUrl,
        String caption,
        LocalDateTime createdAt
) {}