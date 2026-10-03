package com.codegym.locketclone.auth;

import com.codegym.locketclone.auth.dto.JwtResponse;
import com.codegym.locketclone.auth.dto.LoginRequest;
import com.codegym.locketclone.auth.dto.RegisterRequest;
import com.codegym.locketclone.auth.dto.RegisterResponse;
import com.codegym.locketclone.auth.dto.VerifyOtpRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "Quản lý xác thực người dùng: Đăng ký tài khoản, Xác minh mã OTP, Đăng nhập và cấp phát JWT token")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @Operation(summary = "Đăng ký tài khoản mới", description = "Gửi thông tin đăng ký (email, username, password). Hệ thống gửi mã OTP xác nhận qua email bất đồng bộ.")
    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @Operation(summary = "Xác minh mã OTP", description = "Xác nhận mã OTP 6 chữ số gửi qua email. Nếu thành công trả về JWT token và kích hoạt tài khoản. Cho phép thử tối đa 5 lần.")
    @PostMapping("/verify")
    public ResponseEntity<JwtResponse> verify(@Valid @RequestBody VerifyOtpRequest request) {
        return ResponseEntity.ok(authService.verify(request));
    }

    @Operation(summary = "Đăng nhập", description = "Đăng nhập bằng email/username và mật khẩu, trả về Access Token JWT.")
    @PostMapping("/login")
    public ResponseEntity<JwtResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
}
