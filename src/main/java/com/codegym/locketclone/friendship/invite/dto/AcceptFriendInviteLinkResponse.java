package com.codegym.locketclone.friendship.invite.dto;

import com.codegym.locketclone.user.dto.UserResponse;

import java.time.LocalDateTime;
import java.util.UUID;

public record AcceptFriendInviteLinkResponse(
        UUID friendshipId,
        String status,
        UserResponse friend,
        LocalDateTime acceptedAt
) {
}

