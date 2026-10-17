package com.codegym.locketclone.friendship.dto;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record FriendRequestResponse(
        UUID friendshipId,
        FriendProfileResponse requester,
        LocalDateTime createdAt
) {
}
