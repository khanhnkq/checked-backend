package com.codegym.locketclone.friendship.invite.dto;

import jakarta.validation.constraints.NotBlank;

public record AcceptFriendInviteLinkRequest(
        @NotBlank(message = "token không được để trống")
        String token
) {
}

