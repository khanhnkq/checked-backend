package com.codegym.locketclone.calorie;

import com.codegym.locketclone.calorie.dto.*;
import com.codegym.locketclone.photo.Photo;
import com.codegym.locketclone.user.User;
import com.codegym.locketclone.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CalorieServiceImplTest {

    @Mock
    private CalorieGoalRepository calorieGoalRepository;

    @Mock
    private CalorieLogRepository calorieLogRepository;

    @Mock
    private UserRepository userRepository;

    private CalorieServiceImpl calorieService;

    @BeforeEach
    void setUp() {
        calorieService = new CalorieServiceImpl(calorieGoalRepository, calorieLogRepository, userRepository);
    }

    @Test
    void getGoal_returnsDefaultWhenNotFound() {
        UUID userId = UUID.randomUUID();
        when(calorieGoalRepository.findByUserId(userId)).thenReturn(Optional.empty());

        CalorieGoalResponseDTO response = calorieService.getGoal(userId);

        assertNotNull(response);
        assertEquals(2000, response.getDailyTargetCalories());
    }

    @Test
    void updateGoal_savesAndReturnsNewTarget() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).username("testuser").build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(calorieGoalRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(calorieGoalRepository.save(any(CalorieGoal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CalorieGoalRequestDTO request = new CalorieGoalRequestDTO(1800);
        CalorieGoalResponseDTO response = calorieService.updateGoal(userId, request);

        assertNotNull(response);
        assertEquals(1800, response.getDailyTargetCalories());
        verify(calorieGoalRepository).save(any(CalorieGoal.class));
    }

    @Test
    void getDailySummary_aggregatesCorrectlyUsingEagerFetchedPhotos() {
        UUID userId = UUID.randomUUID();
        LocalDate today = LocalDate.of(2026, 10, 12);
        Photo photo = Photo.builder()
                .id(UUID.randomUUID())
                .imageUrl("https://example.com/pho.jpg")
                .thumbnailUrl("https://example.com/thumb.jpg")
                .build();

        CalorieLog log1 = CalorieLog.builder()
                .id(UUID.randomUUID())
                .photo(photo)
                .calories(650)
                .mealType(MealType.LUNCH)
                .dishName("Phở Bò")
                .loggedAt(today.atTime(12, 0))
                .build();

        CalorieLog log2 = CalorieLog.builder()
                .id(UUID.randomUUID())
                .photo(null)
                .calories(350)
                .mealType(MealType.BREAKFAST)
                .dishName("Bánh mì")
                .loggedAt(today.atTime(7, 30))
                .build();

        when(calorieGoalRepository.findByUserId(userId)).thenReturn(Optional.of(
                CalorieGoal.builder().dailyTargetCalories(2000).build()
        ));
        when(calorieLogRepository.findDailyLogsWithPhoto(eq(userId), eq(today.atStartOfDay()), eq(today.plusDays(1).atStartOfDay())))
                .thenReturn(List.of(log2, log1));

        DailyCalorieSummaryDTO summary = calorieService.getDailySummary(userId, today);

        assertNotNull(summary);
        assertEquals(today, summary.getDate());
        assertEquals(2000, summary.getDailyTargetCalories());
        assertEquals(1000, summary.getConsumedCalories());
        assertEquals(1000, summary.getRemainingCalories());
        assertFalse(summary.getIsExceeded());
        assertEquals(50, summary.getPercentage());
        assertEquals(2, summary.getMeals().size());

        // Verify photo eagerly populated without N+1
        assertEquals("https://example.com/thumb.jpg", summary.getMeals().get(1).getThumbnailUrl());
    }

    @Test
    void createManualLog_savesLogCorrectly() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).username("testuser").build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(calorieLogRepository.save(any(CalorieLog.class))).thenAnswer(invocation -> {
            CalorieLog l = invocation.getArgument(0);
            l.setId(UUID.randomUUID());
            return l;
        });

        CreateCalorieLogRequestDTO request = CreateCalorieLogRequestDTO.builder()
                .calories(450)
                .mealType(MealType.SNACK)
                .dishName("Trà sữa")
                .build();

        CalorieLogItemDTO result = calorieService.createManualLog(userId, request);

        assertNotNull(result);
        assertEquals(450, result.getCalories());
        assertEquals(MealType.SNACK, result.getMealType());
        assertEquals("Trà sữa", result.getDishName());
    }

    @Test
    void createLogFromPhoto_createsLogWhenPhotoHasCalories() {
        UUID photoId = UUID.randomUUID();
        User sender = User.builder().id(UUID.randomUUID()).build();
        Photo photo = Photo.builder()
                .id(photoId)
                .sender(sender)
                .calories(750)
                .dishName("Cơm Tấm")
                .caption("Ăn trưa ngon")
                .occurredAt(LocalDateTime.of(2026, 10, 12, 12, 15))
                .build();

        when(calorieLogRepository.existsByPhotoId(photoId)).thenReturn(false);

        calorieService.createLogFromPhoto(photo);

        ArgumentCaptor<CalorieLog> captor = ArgumentCaptor.forClass(CalorieLog.class);
        verify(calorieLogRepository).save(captor.capture());

        CalorieLog saved = captor.getValue();
        assertEquals(750, saved.getCalories());
        assertEquals(MealType.LUNCH, saved.getMealType());
        assertEquals("Cơm Tấm", saved.getDishName());
        assertEquals(photo, saved.getPhoto());
    }
}
