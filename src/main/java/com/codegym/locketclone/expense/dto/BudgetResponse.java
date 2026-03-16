package com.codegym.locketclone.expense.dto;

import java.math.BigDecimal;

public record BudgetResponse(
        String monthKey,
        BigDecimal amountLimit,
        Integer alertThresholdPct,
        BigDecimal spent,
        BigDecimal remaining,
        Boolean exceeded
) {
}

