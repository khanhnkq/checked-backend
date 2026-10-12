package com.codegym.locketclone.calorie.dto;

import java.time.LocalDate;

public interface DailyCalorieAggregation {
    LocalDate getLogDate();
    Integer getTotalCalories();
}
