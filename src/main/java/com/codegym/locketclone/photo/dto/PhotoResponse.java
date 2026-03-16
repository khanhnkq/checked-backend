package com.codegym.locketclone.photo.dto;

import com.codegym.locketclone.photo.PhotoStatus;
import com.codegym.locketclone.photo.RecipientScope;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record PhotoResponse(
        UUID id,
        UUID senderId,
        String senderDisplayName,
        String senderAvatarUrl,
        String imageUrl,
        String thumbnailUrl,
        String caption,
        String note,
        BigDecimal amount,
        UUID categoryId,
        String categoryName,
        RecipientScope recipientScope,
        Integer recipientCount,
        PhotoStatus status,
        String mimeType,
        Long fileSize,
        Integer width,
        Integer height,
        LocalDateTime takenAt,
        LocalDateTime createdAt
) {}