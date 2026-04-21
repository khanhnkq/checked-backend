package com.codegym.locketclone.friendship.invite.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record CreateFriendInviteLinkRequest(
        @Min(value = 10, message = "ttlMinutes tối thiểu là 10")
        @Max(value = 43200, message = "ttlMinutes tối đa là 43200")
        Integer ttlMinutes
) {
}

