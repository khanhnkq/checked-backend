package com.codegym.locketclone.expense.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCategoryRequest(
        @NotBlank(message = "Tên danh mục không được để trống")
        @Size(max = 100, message = "Tên danh mục không được vượt quá 100 ký tự")
        String name,

        @Size(max = 50, message = "Icon không được vượt quá 50 ký tự")
        String icon,

        @Size(max = 20, message = "Màu không được vượt quá 20 ký tự")
        String color
) {
}

