package com.codegym.locketclone.photo.dto;

import com.codegym.locketclone.expense.dto.CategoryResponse;
import com.codegym.locketclone.user.User;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record PhotoResponse(
        UUID id,
        UUID senderId,
        String imageUrl,
        String caption,
        LocalDateTime createdAt,

        BigDecimal amount,
        Boolean isPrivate,
        CategoryResponse category
) {}