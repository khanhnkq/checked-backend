package com.codegym.locketclone.friendship.dto;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record AcceptFriendRequestResponse(
        UUID friendshipId,
        String status,
        FriendProfileResponse friend,
        LocalDateTime acceptedAt
) {
}
