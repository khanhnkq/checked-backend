package com.codegym.locketclone.user.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdatePersonalInfoRequest(
        @Pattern(regexp = "^(?!\\s*$).+", message = "Username khong duoc de trong neu duoc cung cap")
        @Size(max = 50, message = "Username khong duoc vuot qua 50 ky tu")
        String username,

        @Pattern(regexp = "^(?!\\s*$).+", message = "First name khong duoc de trong neu duoc cung cap")
        @Size(max = 50, message = "First name khong duoc vuot qua 50 ky tu")
        String firstName,

        @Pattern(regexp = "^(?!\\s*$).+", message = "Last name khong duoc de trong neu duoc cung cap")
        @Size(max = 50, message = "Last name khong duoc vuot qua 50 ky tu")
        String lastName,

        @Pattern(regexp = "^(?!\\s*$).+", message = "Avatar URL khong duoc de trong neu duoc cung cap")
        @Size(max = 500, message = "Avatar URL khong duoc vuot qua 500 ky tu")
        String avatarUrl
) {
}

