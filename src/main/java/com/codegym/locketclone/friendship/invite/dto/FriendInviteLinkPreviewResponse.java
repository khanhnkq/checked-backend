package com.codegym.locketclone.friendship.invite.dto;

import com.codegym.locketclone.friendship.dto.FriendProfileResponse;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record FriendInviteLinkPreviewResponse(
        FriendProfileResponse inviter,
        String status,
        LocalDateTime expiresAt
) {
}
