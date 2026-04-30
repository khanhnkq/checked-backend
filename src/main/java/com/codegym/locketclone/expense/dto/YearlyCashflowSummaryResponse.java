package com.codegym.locketclone.expense.dto;

import java.math.BigDecimal;
import java.util.List;

public record YearlyCashflowSummaryResponse(
        Integer year,
        BigDecimal totalIncome,
        BigDecimal totalExpense,
        BigDecimal netCashflow,
        List<MonthlyCashflowItemResponse> months
) {
}

