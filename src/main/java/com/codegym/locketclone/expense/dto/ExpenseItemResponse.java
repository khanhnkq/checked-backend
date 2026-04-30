package com.codegym.locketclone.expense.dto;

import com.codegym.locketclone.expense.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record ExpenseItemResponse(
        UUID photoId,
        String imageUrl,
        String thumbnailUrl,
        BigDecimal amount,
        String note,
        UUID categoryId,
        String categoryName,
        TransactionType transactionType,
        LocalDateTime takenAt,
        LocalDateTime createdAt
) {
}

