package com.codegym.locketclone.expense.dto;

import java.math.BigDecimal;
import java.util.List;

public record CashflowSummaryResponse(
        String monthKey,
        BigDecimal totalIncome,
        BigDecimal totalExpense,
        BigDecimal netCashflow,
        BigDecimal budgetLimit,
        BigDecimal budgetRemaining,
        Integer budgetUsedPct,
        Boolean budgetExceeded,
        List<CategorySpendResponse> incomeByCategory,
        List<CategorySpendResponse> expenseByCategory
) {
}

