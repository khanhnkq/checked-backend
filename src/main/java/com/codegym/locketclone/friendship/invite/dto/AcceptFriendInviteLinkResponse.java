package com.codegym.locketclone.friendship.invite.dto;

import com.codegym.locketclone.friendship.dto.FriendProfileResponse;

import java.time.LocalDateTime;
import java.util.UUID;

public record AcceptFriendInviteLinkResponse(
        UUID friendshipId,
        String status,
        FriendProfileResponse friend,
        LocalDateTime acceptedAt
) {
}

