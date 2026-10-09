package com.codegym.locketclone.friendship.dto;

import lombok.Builder;

import java.util.UUID;

@Builder
public record FriendProfileResponse(
        UUID id,
        String username,
        String firstName,
        String lastName,
        String displayName,
        String avatarUrl,
        Boolean isGoldMember
) {
}
