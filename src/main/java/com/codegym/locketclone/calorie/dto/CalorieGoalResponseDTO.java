package com.codegym.locketclone.calorie.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CalorieGoalResponseDTO {
    private Integer dailyTargetCalories;
}
