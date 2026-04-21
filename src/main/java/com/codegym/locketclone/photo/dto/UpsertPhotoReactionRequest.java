package com.codegym.locketclone.photo.dto;

import jakarta.validation.constraints.NotBlank;

public record UpsertPhotoReactionRequest(
        @NotBlank(message = "reaction type không được để trống")
        String type
) {
}

