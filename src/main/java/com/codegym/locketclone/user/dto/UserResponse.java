package com.codegym.locketclone.user.dto;

import lombok.*;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponse {
    private UUID id;
    private String email;
    private String username;
    private String firstName;
    private String lastName;
    private String displayName;
    private String avatarUrl;
    private Boolean isVerified;
    private Boolean isGoldMember;
    private Boolean profileCompleted;
}