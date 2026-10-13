package com.codegym.locketclone.calorie;

import com.codegym.locketclone.calorie.dto.*;
import com.codegym.locketclone.common.config.CacheConfig;
import com.codegym.locketclone.photo.Photo;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;

import java.time.LocalDate;
import java.util.UUID;

public interface CalorieService {
    @Cacheable(value = CacheConfig.CALORIE_GOAL_CACHE, key = "#userId")
    CalorieGoalResponseDTO getGoal(UUID userId);

    @Caching(evict = {
            @CacheEvict(value = CacheConfig.CALORIE_GOAL_CACHE, key = "#userId"),
            @CacheEvict(value = CacheConfig.CALORIE_DAILY_CACHE, allEntries = true),
            @CacheEvict(value = CacheConfig.CALORIE_SUMMARY_CACHE, allEntries = true)
    })
    CalorieGoalResponseDTO updateGoal(UUID userId, CalorieGoalRequestDTO request);

    @Cacheable(value = CacheConfig.CALORIE_DAILY_CACHE, key = "#userId + ':' + (#date != null ? #date.toString() : T(java.time.LocalDate).now().toString())")
    DailyCalorieSummaryDTO getDailySummary(UUID userId, LocalDate date);

    @Cacheable(value = CacheConfig.CALORIE_SUMMARY_CACHE, key = "#userId + ':' + (#monthKey != null ? #monthKey : '')")
    MonthlyCalorieSummaryDTO getMonthlySummary(UUID userId, String monthKey);

    CalorieLogItemDTO createManualLog(UUID userId, CreateCalorieLogRequestDTO request);
    void deleteLog(UUID userId, UUID logId);
    void createLogFromPhoto(Photo photo, MealType preferredMealType);
    default void createLogFromPhoto(Photo photo) {
        createLogFromPhoto(photo, null);
    }

    void evictCalorieCaches(UUID userId, LocalDate date);
}
