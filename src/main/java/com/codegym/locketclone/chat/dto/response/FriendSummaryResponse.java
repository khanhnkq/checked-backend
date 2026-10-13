package com.codegym.locketclone.chat.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FriendSummaryResponse {
    private UUID id;
    private String fullName;
    private String username;
    private String avatarUrl;
}
