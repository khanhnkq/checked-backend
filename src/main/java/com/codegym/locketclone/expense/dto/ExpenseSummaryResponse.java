package com.codegym.locketclone.expense.dto;

import java.math.BigDecimal;
import java.util.List;

public record ExpenseSummaryResponse(
        String monthKey,
        BigDecimal totalSpent,
        BigDecimal budgetLimit,
        BigDecimal remaining,
        Boolean budgetExceeded,
        Integer percentUsed,
        List<CategorySpendResponse> byCategory
) {
}

