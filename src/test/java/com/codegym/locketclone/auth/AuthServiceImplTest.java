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

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import com.codegym.locketclone.security.service.UserPrincipal;

import java.time.LocalDateTime;
import java.util.List;
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
        RegisterRequest request = new RegisterRequest("Khanh@Example.com", "khanh_dev", "password123");
        when(userRepository.findByEmailIgnoreCase("khanh@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByUsernameIgnoreCase("khanh_dev")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
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
    void verify_incrementsFailedAttemptsOnWrongOtp() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("khanh@example.com")
                .username("khanh_dev")
                .password("encoded-password")
                .otpCode("482910")
                .otpExpiresAt(LocalDateTime.now().plusMinutes(5))
                .otpFailedAttempts(2)
                .isVerified(false)
                .build();
        when(userRepository.findByEmailIgnoreCase("khanh@example.com")).thenReturn(Optional.of(user));

        AppException exception = assertThrows(AppException.class,
                () -> authService.verify(new VerifyOtpRequest("khanh@example.com", "000000")));

        assertEquals(ErrorCode.INVALID_OTP, exception.getErrorCode());
        assertEquals(3, user.getOtpFailedAttempts());
        verify(userRepository).save(user);
    }

    @Test
    void verify_locksOtpWhenFailedAttemptsReach5() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("khanh@example.com")
                .username("khanh_dev")
                .password("encoded-password")
                .otpCode("482910")
                .otpExpiresAt(LocalDateTime.now().plusMinutes(5))
                .otpFailedAttempts(4)
                .isVerified(false)
                .build();
        when(userRepository.findByEmailIgnoreCase("khanh@example.com")).thenReturn(Optional.of(user));

        AppException exception = assertThrows(AppException.class,
                () -> authService.verify(new VerifyOtpRequest("khanh@example.com", "000000")));

        assertEquals(ErrorCode.OTP_MAX_ATTEMPTS_EXCEEDED, exception.getErrorCode());
        assertNull(user.getOtpCode());
        assertNull(user.getOtpExpiresAt());
        assertEquals(0, user.getOtpFailedAttempts());
        verify(userRepository).save(user);
    }

    @Test
    void login_throwsInvalidCredentialsWhenUserNotFoundOrBadCredentials() {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        AppException exception = assertThrows(AppException.class,
                () -> authService.login(new LoginRequest("unknown@example.com", "password123")));

        assertEquals(ErrorCode.INVALID_CREDENTIALS, exception.getErrorCode());
        verify(userRepository, never()).findByEmailIgnoreCaseOrUsernameIgnoreCase(anyString(), anyString());
    }

    @Test
    void login_throwsForbiddenWhenUserNotVerified() {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new DisabledException("User is disabled"));

        AppException exception = assertThrows(AppException.class,
                () -> authService.login(new LoginRequest("khanh@example.com", "password123")));

        assertEquals(ErrorCode.USER_NOT_VERIFIED, exception.getErrorCode());
        verify(userRepository, never()).findByEmailIgnoreCaseOrUsernameIgnoreCase(anyString(), anyString());
    }

    @Test
    void login_authenticatesVerifiedUserAndReturnsJwt() {
        UUID userId = UUID.randomUUID();
        UserPrincipal principal = new UserPrincipal(
                userId,
                "khanh_dev",
                "khanh@example.com",
                "encoded-password",
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                true,
                "Khánh Nguyễn",
                "https://avatar.url/avatar.png",
                true
        );
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(jwtUtils.generateTokenFromUserId(userId)).thenReturn("jwt-token");

        var response = authService.login(new LoginRequest("khanh@example.com", "password123"));

        assertEquals("jwt-token", response.token());
        assertEquals(userId, response.id());
        assertEquals("khanh@example.com", response.email());
        assertEquals("khanh_dev", response.username());
        assertEquals("Khánh Nguyễn", response.displayName());
        assertEquals("https://avatar.url/avatar.png", response.avatarUrl());
        assertEquals(true, response.profileCompleted());
        verify(authenticationManager).authenticate(new UsernamePasswordAuthenticationToken("khanh@example.com", "password123"));
        verify(userRepository, never()).findByEmailIgnoreCaseOrUsernameIgnoreCase(anyString(), anyString());
    }

    @Test
    void register_resendsOtp_whenPendingUserAndCooldownPassed() {
        RegisterRequest request = new RegisterRequest("khanh@example.com", "khanh_dev", "newpassword123");
        User pendingUser = User.builder()
                .id(UUID.randomUUID())
                .email("khanh@example.com")
                .username("khanh_dev")
                .password("old-encoded-password")
                .isVerified(false)
                .otpExpiresAt(LocalDateTime.now().plusMinutes(3)) // 3 mins remaining < 4 mins -> > 60s since creation
                .build();

        when(userRepository.findByEmailIgnoreCase("khanh@example.com")).thenReturn(Optional.of(pendingUser));
        when(passwordEncoder.encode("newpassword123")).thenReturn("new-encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RegisterResponse result = authService.register(request);

        assertEquals("VERIFY_OTP", result.nextStep());
        verify(emailService).sendOtpEmail(eq("khanh@example.com"), eq("khanh_dev"), matches("\\d{6}"));
        verify(userRepository).save(pendingUser);
    }

    @Test
    void register_throwsCooldownException_whenPendingUserResendsTooQuickly() {
        RegisterRequest request = new RegisterRequest("khanh@example.com", "khanh_dev", "newpassword123");
        User pendingUser = User.builder()
                .id(UUID.randomUUID())
                .email("khanh@example.com")
                .username("khanh_dev")
                .password("old-encoded-password")
                .isVerified(false)
                .otpExpiresAt(LocalDateTime.now().plusSeconds(290)) // Created 10s ago, 290s remaining (> 4 mins remaining)
                .build();

        when(userRepository.findByEmailIgnoreCase("khanh@example.com")).thenReturn(Optional.of(pendingUser));

        AppException exception = assertThrows(AppException.class, () -> authService.register(request));

        assertEquals(ErrorCode.OTP_RESEND_COOLDOWN, exception.getErrorCode());
        verify(emailService, never()).sendOtpEmail(any(), any(), any());
    }

    @Test
    void register_throwsEmailAlreadyExists_whenPendingUserRegisteredWithDifferentUsername() {
        RegisterRequest request = new RegisterRequest("khanh@example.com", "attacker_user", "password123");
        User pendingUser = User.builder()
                .id(UUID.randomUUID())
                .email("khanh@example.com")
                .username("khanh_dev")
                .isVerified(false)
                .build();

        when(userRepository.findByEmailIgnoreCase("khanh@example.com")).thenReturn(Optional.of(pendingUser));

        AppException exception = assertThrows(AppException.class, () -> authService.register(request));

        assertEquals(ErrorCode.EMAIL_ALREADY_EXISTS, exception.getErrorCode());
        verify(emailService, never()).sendOtpEmail(any(), any(), any());
    }

    @Test
    void register_throwsEmailAlreadyExists_whenUserAlreadyVerified() {
        RegisterRequest request = new RegisterRequest("khanh@example.com", "khanh_dev", "password123");
        User verifiedUser = User.builder()
                .id(UUID.randomUUID())
                .email("khanh@example.com")
                .username("khanh_dev")
                .isVerified(true)
                .build();

        when(userRepository.findByEmailIgnoreCase("khanh@example.com")).thenReturn(Optional.of(verifiedUser));

        AppException exception = assertThrows(AppException.class, () -> authService.register(request));

        assertEquals(ErrorCode.EMAIL_ALREADY_EXISTS, exception.getErrorCode());
    }
}
