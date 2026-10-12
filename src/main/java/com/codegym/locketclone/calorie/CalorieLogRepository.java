package com.codegym.locketclone.calorie;

import com.codegym.locketclone.calorie.dto.DailyCalorieAggregation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CalorieLogRepository extends JpaRepository<CalorieLog, UUID> {

    /**
     * Lấy danh sách CalorieLog trong ngày KÈM PHOTO để tránh N+1 Query.
     * Sử dụng LEFT JOIN FETCH tải đồng thời Photo trong 1 câu SQL duy nhất.
     */
    @Query("SELECT l FROM CalorieLog l " +
           "LEFT JOIN FETCH l.photo " +
           "WHERE l.user.id = :userId " +
           "AND l.loggedAt >= :startOfDay AND l.loggedAt < :endOfDay " +
           "ORDER BY l.loggedAt ASC")
    List<CalorieLog> findDailyLogsWithPhoto(
            @Param("userId") UUID userId,
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("endOfDay") LocalDateTime endOfDay
    );

    /**
     * Tính tổng lượng Calo nạp vào trong khoảng thời gian bằng aggregation tại database.
     */
    @Query("SELECT COALESCE(SUM(l.calories), 0) FROM CalorieLog l " +
           "WHERE l.user.id = :userId AND l.loggedAt >= :start AND l.loggedAt < :end")
    Integer calculateTotalCaloriesBetween(
            @Param("userId") UUID userId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    /**
     * Tổng hợp Calo từng ngày trong tháng trực tiếp từ PostgreSQL thông qua Native Query.
     * Tận dụng tối đa Composite Index `idx_calorie_logs_user_logged_cal` (Index-Only Scan).
     */
    @Query(value = "SELECT CAST(logged_at AS DATE) AS logDate, CAST(COALESCE(SUM(calories), 0) AS INTEGER) AS totalCalories " +
                   "FROM calorie_logs " +
                   "WHERE user_id = :userId AND logged_at >= :startOfMonth AND logged_at < :endOfMonth " +
                   "GROUP BY CAST(logged_at AS DATE) " +
                   "ORDER BY logDate ASC", nativeQuery = true)
    List<DailyCalorieAggregation> aggregateDailyCaloriesOfMonth(
            @Param("userId") UUID userId,
            @Param("startOfMonth") LocalDateTime startOfMonth,
            @Param("endOfMonth") LocalDateTime endOfMonth
    );

    boolean existsByPhotoId(UUID photoId);

    Optional<CalorieLog> findByPhotoId(UUID photoId);
}
