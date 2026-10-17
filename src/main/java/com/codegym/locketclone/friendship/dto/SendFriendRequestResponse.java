package com.codegym.locketclone.friendship.dto;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record SendFriendRequestResponse(
        UUID friendshipId,
        String status,
        String relationshipStatus,
        FriendProfileResponse targetUser,
        LocalDateTime createdAt
) {
}
