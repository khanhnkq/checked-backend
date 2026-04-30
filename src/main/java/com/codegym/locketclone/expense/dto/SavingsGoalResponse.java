package com.codegym.locketclone.expense.dto;

import java.math.BigDecimal;

public record SavingsGoalResponse(
        String monthKey,
        BigDecimal targetAmount,
        BigDecimal currentSaved,
        BigDecimal remaining,
        Integer progressPct,
        Boolean achieved
) {
}

