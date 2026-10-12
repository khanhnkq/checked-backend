package com.codegym.locketclone.calorie;

import com.codegym.locketclone.calorie.dto.*;
import com.codegym.locketclone.photo.Photo;

import java.time.LocalDate;
import java.util.UUID;

public interface CalorieService {
    CalorieGoalResponseDTO getGoal(UUID userId);
    CalorieGoalResponseDTO updateGoal(UUID userId, CalorieGoalRequestDTO request);
    DailyCalorieSummaryDTO getDailySummary(UUID userId, LocalDate date);
    MonthlyCalorieSummaryDTO getMonthlySummary(UUID userId, String monthKey);
    CalorieLogItemDTO createManualLog(UUID userId, CreateCalorieLogRequestDTO request);
    void deleteLog(UUID userId, UUID logId);
    void createLogFromPhoto(Photo photo);
}
