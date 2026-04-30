package com.codegym.locketclone.photo.dto;

import com.codegym.locketclone.expense.TransactionType;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record UpdatePhotoTransactionRequest(
        @Size(max = 1000, message = "Caption khong duoc vuot qua 1000 ky tu")
        String caption,
        BigDecimal amount,
        @Size(max = 255, message = "Note khong duoc vuot qua 255 ky tu")
        String note,
        UUID categoryId,
        Boolean clearCategory,
        TransactionType transactionType,
        LocalDateTime takenAt,
        LocalDateTime occurredAt
) {
}

