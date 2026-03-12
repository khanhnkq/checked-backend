package com.codegym.locketclone.auth;

import com.codegym.locketclone.auth.dto.JwtResponse;
import com.codegym.locketclone.auth.dto.LoginRequest;
import com.codegym.locketclone.auth.dto.OnboardingStep;
import com.codegym.locketclone.auth.dto.RegisterRequest;
import com.codegym.locketclone.auth.dto.RegisterResponse;
import com.codegym.locketclone.auth.dto.VerifyOtpRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    @Test
    void register_returnsCreatedMessage() {
        RegisterRequest request = new RegisterRequest("khanh@example.com", "khanh_dev", "123456");
        RegisterResponse registerResponse = new RegisterResponse(
                "Đăng ký thành công, vui lòng kiểm tra email để lấy mã OTP",
                "khanh@example.com",
                "VERIFY_OTP"
        );
        when(authService.register(request)).thenReturn(registerResponse);

        var response = authController.register(request);

        assertEquals(201, response.getStatusCode().value());
        assertEquals(registerResponse, response.getBody());
        verify(authService).register(request);
    }

    @Test
    void verify_returnsJwtResponse() {
        JwtResponse jwtResponse = new JwtResponse(
                "token",
                "Bearer",
                UUID.randomUUID(),
                "khanh@example.com",
                "khanh_dev",
                true,
                false,
                "khanh_dev",
                null,
                OnboardingStep.COMPLETE_PROFILE
        );
        VerifyOtpRequest request = new VerifyOtpRequest("khanh@example.com", "482910");
        when(authService.verify(request)).thenReturn(jwtResponse);

        var response = authController.verify(request);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(jwtResponse, response.getBody());
        verify(authService).verify(request);
    }

    @Test
    void login_returnsJwtResponse() {
        JwtResponse jwtResponse = new JwtResponse(
                "token",
                "Bearer",
                UUID.randomUUID(),
                "khanh@example.com",
                "khanh_dev",
                true,
                true,
                "Khánh Nguyễn",
                "https://example.com/avatar.jpg",
                OnboardingStep.HOME
        );
        LoginRequest request = new LoginRequest("khanh@example.com", "123456");
        when(authService.login(request)).thenReturn(jwtResponse);

        var response = authController.login(request);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(jwtResponse, response.getBody());
        verify(authService).login(request);
    }
}
