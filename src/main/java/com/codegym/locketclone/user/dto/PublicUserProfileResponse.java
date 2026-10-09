package com.codegym.locketclone.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublicUserProfileResponse {
    private UUID id;
    private String username;
    private String firstName;
    private String lastName;
    private String displayName;
    private String avatarUrl;
    private Boolean isGoldMember;
}
