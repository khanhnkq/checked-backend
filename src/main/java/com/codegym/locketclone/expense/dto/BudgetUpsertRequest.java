package com.codegym.locketclone.expense.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record BudgetUpsertRequest(
        @NotNull(message = "amountLimit là bắt buộc")
        BigDecimal amountLimit,

        @Min(value = 1, message = "alertThresholdPct phải từ 1 đến 100")
        @Max(value = 100, message = "alertThresholdPct phải từ 1 đến 100")
        Integer alertThresholdPct
) {
}

