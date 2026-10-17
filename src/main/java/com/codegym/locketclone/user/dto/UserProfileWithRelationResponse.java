package com.codegym.locketclone.user.dto;

import com.codegym.locketclone.friendship.dto.FriendProfileResponse;
import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder
public record UserProfileWithRelationResponse(
        UUID id,
        String username,
        String firstName,
        String lastName,
        String displayName,
        String avatarUrl,
        Boolean isGoldMember,
        String relationshipStatus, // UNAUTHENTICATED | SELF | NONE | PENDING_SENT | PENDING_RECEIVED | ACCEPTED | BLOCKED
        UUID friendshipId,         // null if NONE, SELF or UNAUTHENTICATED
        Integer mutualFriendsCount,
        List<FriendProfileResponse> mutualFriendsPreview
) {
}
