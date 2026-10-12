package com.codegym.locketclone.calorie.dto;

import com.codegym.locketclone.calorie.MealType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CalorieLogItemDTO {
    private UUID id;
    private UUID photoId;
    private Integer calories;
    private MealType mealType;
    private String dishName;
    private String note;
    private String imageUrl;
    private String thumbnailUrl;
    private LocalDateTime loggedAt;
}
