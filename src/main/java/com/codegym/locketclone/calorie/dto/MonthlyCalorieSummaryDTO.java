package com.codegym.locketclone.calorie.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonthlyCalorieSummaryDTO {
    private String monthKey; // Định dạng YYYYMM
    private Integer dailyTargetCalories;
    private Integer averageDailyCalories;
    private Integer daysOnTrack;
    private List<MonthlyCaloriePointDTO> days;
}
