package com.codegym.locketclone.calorie.dto;

import com.codegym.locketclone.calorie.MealType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateCalorieLogRequestDTO {
    @NotNull(message = "Lượng calo không được để trống")
    @Min(value = 1, message = "Lượng calo phải lớn hơn 0")
    @Max(value = 10000, message = "Lượng calo không được vượt quá 10000 kcal")
    private Integer calories;

    private MealType mealType;
    private String dishName;
    private String note;
    private LocalDateTime loggedAt;
}
