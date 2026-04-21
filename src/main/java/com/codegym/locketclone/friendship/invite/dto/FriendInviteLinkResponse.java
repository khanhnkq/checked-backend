package com.codegym.locketclone.friendship.invite.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record FriendInviteLinkResponse(
        UUID id,
        String inviteUrl,
        String tokenPreview,
        Integer maxUses,
        Integer usedCount,
        LocalDateTime expiresAt,
        LocalDateTime revokedAt,
        String status
) {
}

