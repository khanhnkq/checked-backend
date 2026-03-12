package com.codegym.locketclone.auth;

import com.codegym.locketclone.auth.dto.LoginRequest;
import com.codegym.locketclone.auth.dto.RegisterRequest;
import com.codegym.locketclone.auth.dto.RegisterResponse;
import com.codegym.locketclone.auth.dto.VerifyOtpRequest;
import com.codegym.locketclone.common.exception.AppException;
import com.codegym.locketclone.common.exception.ErrorCode;
import com.codegym.locketclone.notification.EmailService;
import com.codegym.locketclone.security.jwt.JwtUtils;
import com.codegym.locketclone.user.User;
import com.codegym.locketclone.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private JwtUtils jwtUtils;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private EmailService emailService;

    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    void register_createsPendingUserAndSendsOtpEmail() {
        RegisterRequest request = new RegisterRequest("Khanh@Example.com", "khanh_dev", "123456");
        when(userRepository.findByEmailIgnoreCase("khanh@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByUsernameIgnoreCase("khanh_dev")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("123456")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RegisterResponse result = authService.register(request);

        assertEquals("Đăng ký thành công, vui lòng kiểm tra email để lấy mã OTP", result.message());
        assertEquals("khanh@example.com", result.email());
        assertEquals("VERIFY_OTP", result.nextStep());
        verify(userRepository).save(any(User.class));
        verify(emailService).sendOtpEmail(eq("khanh@example.com"), eq("khanh_dev"), matches("\\d{6}"));
    }

    @Test
    void verify_marksUserVerifiedAndReturnsJwt() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("khanh@example.com")
                .username("khanh_dev")
                .password("encoded-password")
                .otpCode("482910")
                .otpExpiresAt(LocalDateTime.now().plusMinutes(5))
                .isVerified(false)
                .build();
        when(userRepository.findByEmailIgnoreCase("khanh@example.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtUtils.generateTokenFromUserId(userId)).thenReturn("jwt-token");

        var response = authService.verify(new VerifyOtpRequest("khanh@example.com", "482910"));

        assertEquals("jwt-token", response.token());
        assertTrue(user.getIsVerified());
        assertNull(user.getOtpCode());
        assertNull(user.getOtpExpiresAt());
    }

    @Test
    void verify_throwsWhenOtpExpired() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("khanh@example.com")
                .username("khanh_dev")
                .password("encoded-password")
                .otpCode("482910")
                .otpExpiresAt(LocalDateTime.now().minusMinutes(1))
                .isVerified(false)
                .build();
        when(userRepository.findByEmailIgnoreCase("khanh@example.com")).thenReturn(Optional.of(user));

        AppException exception = assertThrows(AppException.class,
                () -> authService.verify(new VerifyOtpRequest("khanh@example.com", "482910")));

        assertEquals(ErrorCode.OTP_EXPIRED, exception.getErrorCode());
    }

    @Test
    void login_throwsForbiddenWhenUserNotVerified() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("khanh@example.com")
                .username("khanh_dev")
                .password("encoded-password")
                .isVerified(false)
                .build();
        when(userRepository.findByEmailIgnoreCaseOrUsernameIgnoreCase("khanh@example.com", "khanh@example.com"))
                .thenReturn(Optional.of(user));

        AppException exception = assertThrows(AppException.class,
                () -> authService.login(new LoginRequest("khanh@example.com", "123456")));

        assertEquals(ErrorCode.USER_NOT_VERIFIED, exception.getErrorCode());
        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    void login_authenticatesVerifiedUserAndReturnsJwt() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("khanh@example.com")
                .username("khanh_dev")
                .password("encoded-password")
                .isVerified(true)
                .firstName("Khánh")
                .lastName("Nguyễn")
                .build();
        when(userRepository.findByEmailIgnoreCaseOrUsernameIgnoreCase("khanh@example.com", "khanh@example.com"))
                .thenReturn(Optional.of(user));
        when(jwtUtils.generateTokenFromUserId(userId)).thenReturn("jwt-token");

        var response = authService.login(new LoginRequest("khanh@example.com", "123456"));

        assertEquals("jwt-token", response.token());
        verify(authenticationManager).authenticate(new UsernamePasswordAuthenticationToken("khanh@example.com", "123456"));
    }
}
