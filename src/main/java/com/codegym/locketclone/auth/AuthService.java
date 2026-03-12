package com.codegym.locketclone.auth;

import com.codegym.locketclone.auth.dto.JwtResponse;
import com.codegym.locketclone.auth.dto.LoginRequest;
import com.codegym.locketclone.auth.dto.RegisterRequest;
import com.codegym.locketclone.auth.dto.RegisterResponse;
import com.codegym.locketclone.auth.dto.VerifyOtpRequest;

public interface AuthService {
    RegisterResponse register(RegisterRequest request);

    JwtResponse verify(VerifyOtpRequest request);

    JwtResponse login(LoginRequest request);
}
