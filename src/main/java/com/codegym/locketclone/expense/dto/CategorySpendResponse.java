package com.codegym.locketclone.expense.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CategorySpendResponse(
        UUID categoryId,
        String categoryName,
        BigDecimal totalAmount
) {
}

