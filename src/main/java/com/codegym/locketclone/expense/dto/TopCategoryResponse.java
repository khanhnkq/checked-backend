package com.codegym.locketclone.expense.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record TopCategoryResponse(
        UUID categoryId,
        String categoryName,
        BigDecimal totalAmount,
        Integer percentage
) {
}

