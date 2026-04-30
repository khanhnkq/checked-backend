package com.codegym.locketclone.expense.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record SavingsGoalUpsertRequest(
        @NotNull(message = "targetAmount bat buoc")
        @DecimalMin(value = "0.01", message = "targetAmount phai > 0")
        BigDecimal targetAmount
) {
}

