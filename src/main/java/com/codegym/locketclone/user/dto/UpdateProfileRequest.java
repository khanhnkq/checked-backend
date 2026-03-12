package com.codegym.locketclone.user.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Pattern(regexp = "^(?!\\s*$).+", message = "Username không được để trống nếu được cung cấp")
        @Size(max = 50, message = "Username không được vượt quá 50 ký tự")
        String username,

        @Pattern(regexp = "^(?!\\s*$).+", message = "First name không được để trống nếu được cung cấp")
        @Size(max = 50, message = "First name không được vượt quá 50 ký tự")
        String firstName,

        @Pattern(regexp = "^(?!\\s*$).+", message = "Last name không được để trống nếu được cung cấp")
        @Size(max = 50, message = "Last name không được vượt quá 50 ký tự")
        String lastName,

        @Pattern(regexp = "^(?!\\s*$).+", message = "Avatar URL không được để trống nếu được cung cấp")
        @Size(max = 255, message = "Avatar URL không được vượt quá 255 ký tự")
        String avatarUrl
) {
}
