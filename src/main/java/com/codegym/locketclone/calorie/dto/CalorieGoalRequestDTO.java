package com.codegym.locketclone.calorie.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CalorieGoalRequestDTO {
    @NotNull(message = "Mục tiêu calo không được để trống")
    @Min(value = 500, message = "Mục tiêu calo tối thiểu là 500 kcal")
    @Max(value = 10000, message = "Mục tiêu calo tối đa là 10000 kcal")
    private Integer dailyTargetCalories;
}
