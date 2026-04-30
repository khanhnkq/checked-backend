package com.codegym.locketclone.expense.dto;

import java.math.BigDecimal;

public record MonthlyCashflowItemResponse(
        String monthKey,
        BigDecimal income,
        BigDecimal expense,
        BigDecimal net
) {
}

