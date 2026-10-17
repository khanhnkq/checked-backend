package com.codegym.locketclone.friendship.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SendFriendRequest(
        @NotNull(message = "targetUserId không được để trống")
        UUID targetUserId
) {
}
