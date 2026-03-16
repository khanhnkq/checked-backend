package com.codegym.locketclone.photo.dto;

import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record UpdatePhotoExpenseRequest(
        BigDecimal amount,
        @Size(max = 255, message = "Note không được vượt quá 255 ký tự")
        String note,
        UUID categoryId
) {
}

