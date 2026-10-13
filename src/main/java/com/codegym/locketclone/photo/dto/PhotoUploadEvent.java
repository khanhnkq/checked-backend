package com.codegym.locketclone.photo.dto;

import com.codegym.locketclone.calorie.MealType;
import com.codegym.locketclone.expense.TransactionType;
import com.codegym.locketclone.photo.RecipientScope;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record PhotoUploadEvent(
        UUID photoId,
        UUID senderId,
        String tempKey,
        String caption,
        BigDecimal amount,
        TransactionType transactionType,
        String note,
        UUID categoryId,
        RecipientScope recipientScope,
        List<UUID> recipientIds,
        LocalDateTime takenAt,
        String locationName,
        BigDecimal latitude,
        BigDecimal longitude,
        String dishName,
        Integer calories,
        MealType mealType
) implements Serializable {}
