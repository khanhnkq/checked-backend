package com.codegym.locketclone.auth;

import com.codegym.locketclone.auth.dto.LoginRequest;
import com.codegym.locketclone.auth.dto.RegisterRequest;
import com.codegym.locketclone.auth.dto.JwtResponse;
import com.codegym.locketclone.user.dto.UserResponse;

public interface AuthService {
    // Xử lý đăng nhập và trả về Token
    JwtResponse login(LoginRequest loginRequest);

    // Xử lý đăng ký tài khoản mới
    UserResponse register(RegisterRequest registerRequest);
}
