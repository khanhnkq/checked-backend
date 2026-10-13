package com.codegym.locketclone.calorie;

import com.codegym.locketclone.calorie.dto.*;
import com.codegym.locketclone.common.config.CacheConfig;
import com.codegym.locketclone.common.exception.AppException;
import com.codegym.locketclone.common.exception.ErrorCode;
import com.codegym.locketclone.photo.Photo;
import com.codegym.locketclone.user.User;
import com.codegym.locketclone.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class CalorieServiceImpl implements CalorieService {

    private final CalorieGoalRepository calorieGoalRepository;
    private final CalorieLogRepository calorieLogRepository;
    private final UserRepository userRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private CacheManager cacheManager;

    public void setCacheManager(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    private static final int DEFAULT_CALORIE_TARGET = 2000;
    private static final DateTimeFormatter MONTH_KEY_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");

    @Override
    @Transactional(readOnly = true)
    public CalorieGoalResponseDTO getGoal(UUID userId) {
        return calorieGoalRepository.findByUserId(userId)
                .map(goal -> new CalorieGoalResponseDTO(goal.getDailyTargetCalories()))
                .orElse(new CalorieGoalResponseDTO(DEFAULT_CALORIE_TARGET));
    }

    @Override
    @Transactional
    public CalorieGoalResponseDTO updateGoal(UUID userId, CalorieGoalRequestDTO request) {
        User user = ensureUserExists(userId);
        CalorieGoal goal = calorieGoalRepository.findByUserId(userId)
                .orElseGet(() -> CalorieGoal.builder()
                        .user(user)
                        .dailyTargetCalories(DEFAULT_CALORIE_TARGET)
                        .build());

        goal.setDailyTargetCalories(request.getDailyTargetCalories());
        CalorieGoal saved = calorieGoalRepository.save(goal);
        log.info("Cập nhật mục tiêu Calo cho user: {}, mục tiêu: {} kcal", userId, saved.getDailyTargetCalories());
        return new CalorieGoalResponseDTO(saved.getDailyTargetCalories());
    }

    @Override
    @Transactional(readOnly = true)
    public DailyCalorieSummaryDTO getDailySummary(UUID userId, LocalDate date) {
        LocalDate targetDate = (date != null) ? date : LocalDate.now();
        LocalDateTime startOfDay = targetDate.atStartOfDay();
        LocalDateTime endOfDay = targetDate.plusDays(1).atStartOfDay();

        // Tối ưu N+1: Query kèm LEFT JOIN FETCH Photo trong 1 câu SQL duy nhất
        List<CalorieLog> logs = calorieLogRepository.findDailyLogsWithPhoto(userId, startOfDay, endOfDay);

        int consumed = logs.stream().mapToInt(CalorieLog::getCalories).sum();
        int target = getGoal(userId).getDailyTargetCalories();
        int remaining = Math.max(0, target - consumed);
        boolean isExceeded = consumed > target;
        int percentage = target > 0 ? (int) Math.round(((double) consumed / target) * 100) : 0;

        List<CalorieLogItemDTO> meals = new ArrayList<>(logs.stream().map(this::mapToItemDTO).toList());

        return DailyCalorieSummaryDTO.builder()
                .date(targetDate)
                .dailyTargetCalories(target)
                .consumedCalories(consumed)
                .remainingCalories(remaining)
                .isExceeded(isExceeded)
                .percentage(percentage)
                .meals(meals)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public MonthlyCalorieSummaryDTO getMonthlySummary(UUID userId, String monthKey) {
        YearMonth yearMonth = parseMonthKey(monthKey);
        LocalDate startOfMonth = yearMonth.atDay(1);
        LocalDate nextMonthStart = yearMonth.plusMonths(1).atDay(1);
        int daysInMonth = yearMonth.lengthOfMonth();

        // Tối ưu hiệu năng: Aggregate trực tiếp ở PostgreSQL (Index-Only Scan)
        List<DailyCalorieAggregation> aggregations = calorieLogRepository.aggregateDailyCaloriesOfMonth(
                userId,
                startOfMonth.atStartOfDay(),
                nextMonthStart.atStartOfDay()
        );

        Map<Integer, Integer> dayCalorieMap = new HashMap<>();
        for (DailyCalorieAggregation agg : aggregations) {
            if (agg.getLogDate() != null) {
                dayCalorieMap.put(agg.getLogDate().getDayOfMonth(), agg.getTotalCalories() != null ? agg.getTotalCalories() : 0);
            }
        }

        int target = getGoal(userId).getDailyTargetCalories();
        int daysOnTrack = 0;
        int activeDaysCount = 0;
        long totalConsumedInMonth = 0;

        List<MonthlyCaloriePointDTO> dayPoints = new ArrayList<>(daysInMonth);
        for (int d = 1; d <= daysInMonth; d++) {
            int cal = dayCalorieMap.getOrDefault(d, 0);
            dayPoints.add(new MonthlyCaloriePointDTO(d, cal));

            if (cal > 0) {
                activeDaysCount++;
                totalConsumedInMonth += cal;
                if (cal <= target) {
                    daysOnTrack++;
                }
            }
        }

        int averageDaily = activeDaysCount > 0 ? (int) (totalConsumedInMonth / activeDaysCount) : 0;

        return MonthlyCalorieSummaryDTO.builder()
                .monthKey(yearMonth.format(MONTH_KEY_FORMATTER))
                .dailyTargetCalories(target)
                .averageDailyCalories(averageDaily)
                .daysOnTrack(daysOnTrack)
                .days(dayPoints)
                .build();
    }

    @Override
    @Transactional
    public CalorieLogItemDTO createManualLog(UUID userId, CreateCalorieLogRequestDTO request) {
        User user = ensureUserExists(userId);
        LocalDateTime loggedAt = request.getLoggedAt() != null ? request.getLoggedAt() : LocalDateTime.now();
        MealType mealType = request.getMealType() != null ? request.getMealType() : inferMealType(loggedAt);

        CalorieLog logEntity = CalorieLog.builder()
                .user(user)
                .calories(request.getCalories())
                .mealType(mealType)
                .dishName(request.getDishName())
                .note(request.getNote())
                .loggedAt(loggedAt)
                .build();

        CalorieLog saved = calorieLogRepository.save(logEntity);
        evictCalorieCaches(userId, loggedAt.toLocalDate());
        log.info("Tạo thủ công CalorieLog thành công cho user: {}, calo: {}", userId, saved.getCalories());
        return mapToItemDTO(saved);
    }

    @Override
    @Transactional
    public void deleteLog(UUID userId, UUID logId) {
        CalorieLog logEntity = calorieLogRepository.findById(logId)
                .orElseThrow(() -> new AppException(ErrorCode.CALORIE_LOG_NOT_FOUND));

        if (!logEntity.getUser().getId().equals(userId)) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }

        LocalDate loggedDate = logEntity.getLoggedAt() != null ? logEntity.getLoggedAt().toLocalDate() : null;
        calorieLogRepository.delete(logEntity);
        evictCalorieCaches(userId, loggedDate);
        log.info("Xóa CalorieLog ID: {} của user: {}", logId, userId);
    }

    @Override
    @Transactional
    public void createLogFromPhoto(Photo photo, MealType preferredMealType) {
        if (photo == null || photo.getCalories() == null || photo.getCalories() <= 0) {
            return;
        }

        if (photo.getId() != null && calorieLogRepository.existsByPhotoId(photo.getId())) {
            log.debug("Photo ID: {} đã có CalorieLog liên kết, bỏ qua tạo mới.", photo.getId());
            return;
        }

        LocalDateTime loggedAt = photo.getOccurredAt() != null ? photo.getOccurredAt() : LocalDateTime.now();
        MealType mealType = preferredMealType != null ? preferredMealType : inferMealType(loggedAt);

        CalorieLog logEntity = CalorieLog.builder()
                .user(photo.getSender())
                .photo(photo)
                .calories(photo.getCalories())
                .mealType(mealType)
                .dishName(photo.getDishName())
                .note(photo.getCaption())
                .loggedAt(loggedAt)
                .build();

        calorieLogRepository.save(logEntity);
        evictCalorieCaches(photo.getSender().getId(), loggedAt.toLocalDate());
        log.info("Tự động tạo CalorieLog từ Photo ID: {}, calo: {}, bữa: {}", photo.getId(), photo.getCalories(), mealType);
    }

    // MARK: - Private Helpers
    private User ensureUserExists(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    private MealType inferMealType(LocalDateTime time) {
        LocalTime lt = time.toLocalTime();
        if (!lt.isBefore(LocalTime.of(5, 0)) && lt.isBefore(LocalTime.of(10, 30))) {
            return MealType.BREAKFAST;
        } else if (!lt.isBefore(LocalTime.of(10, 30)) && lt.isBefore(LocalTime.of(14, 30))) {
            return MealType.LUNCH;
        } else if (!lt.isBefore(LocalTime.of(14, 30)) && lt.isBefore(LocalTime.of(17, 0))) {
            return MealType.SNACK;
        } else if (!lt.isBefore(LocalTime.of(17, 0)) && lt.isBefore(LocalTime.of(21, 30))) {
            return MealType.DINNER;
        } else {
            return MealType.SNACK;
        }
    }

    private YearMonth parseMonthKey(String monthKey) {
        if (monthKey != null && monthKey.length() == 6) {
            try {
                return YearMonth.parse(monthKey, MONTH_KEY_FORMATTER);
            } catch (DateTimeParseException ignored) {
            }
        }
        return YearMonth.now();
    }

    private CalorieLogItemDTO mapToItemDTO(CalorieLog log) {
        Photo photo = log.getPhoto();
        String imageUrl = photo != null ? photo.getImageUrl() : null;
        String thumbUrl = photo != null ? (photo.getThumbnailUrl() != null ? photo.getThumbnailUrl() : photo.getImageUrl()) : null;

        return CalorieLogItemDTO.builder()
                .id(log.getId())
                .photoId(photo != null ? photo.getId() : null)
                .calories(log.getCalories())
                .mealType(log.getMealType())
                .dishName(log.getDishName())
                .note(log.getNote())
                .imageUrl(imageUrl)
                .thumbnailUrl(thumbUrl)
                .loggedAt(log.getLoggedAt())
                .build();
    }

    @Override
    public void evictCalorieCaches(UUID userId, LocalDate date) {
        if (cacheManager == null || userId == null) {
            return;
        }
        LocalDate targetDate = (date != null) ? date : LocalDate.now();
        String dateStr = targetDate.toString();
        String monthKey = targetDate.format(MONTH_KEY_FORMATTER);

        Cache dailyCache = cacheManager.getCache(CacheConfig.CALORIE_DAILY_CACHE);
        if (dailyCache != null) {
            dailyCache.evict(userId + ":" + dateStr);
        }

        Cache summaryCache = cacheManager.getCache(CacheConfig.CALORIE_SUMMARY_CACHE);
        if (summaryCache != null) {
            summaryCache.evict(userId + ":" + monthKey);
            summaryCache.evict(userId + ":");
        }
    }
}
