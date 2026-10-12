package com.codegym.locketclone.user.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

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
        @Size(max = 500, message = "Avatar URL không được vượt quá 500 ký tự")
        String avatarUrl,

        @Past(message = "Ngày sinh phải ở trong quá khứ")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        LocalDate dob
) {
    public UpdateProfileRequest(String username, String firstName, String lastName, String avatarUrl) {
        this(username, firstName, lastName, avatarUrl, null);
    }
}
