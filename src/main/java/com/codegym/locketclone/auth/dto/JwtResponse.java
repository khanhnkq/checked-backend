package com.codegym.locketclone.auth.dto;

import java.util.UUID;

public record JwtResponse(String token, String type, UUID id, String phoneNumber) {
}
