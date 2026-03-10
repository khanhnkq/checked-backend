package com.codegym.locketclone.user.dto;

import lombok.*;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponse {
    private UUID id;
    private String username;
    private String displayName;
}