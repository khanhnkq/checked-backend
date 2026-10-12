package com.codegym.locketclone.calorie.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyCalorieSummaryDTO {
    private LocalDate date;
    private Integer dailyTargetCalories;
    private Integer consumedCalories;
    private Integer remainingCalories;
    private Boolean isExceeded;
    private Integer percentage;
    private List<CalorieLogItemDTO> meals;
}
