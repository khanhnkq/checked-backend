package com.codegym.locketclone.auth;

import com.codegym.locketclone.auth.dto.JwtResponse;
import com.codegym.locketclone.auth.dto.LoginRequest;
import com.codegym.locketclone.auth.dto.OnboardingStep;
import com.codegym.locketclone.auth.dto.RegisterRequest;
import com.codegym.locketclone.auth.dto.RegisterResponse;
import com.codegym.locketclone.auth.dto.VerifyOtpRequest;
import com.codegym.locketclone.common.exception.AppException;
import com.codegym.locketclone.common.exception.ErrorCode;
import com.codegym.locketclone.notification.EmailService;
import com.codegym.locketclone.security.jwt.JwtUtils;
import com.codegym.locketclone.user.User;
import com.codegym.locketclone.user.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int OTP_EXPIRY_MINUTES = 5;

    private final JwtUtils jwtUtils;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;

    @Override
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        String username = normalizeUsername(request.username());
        String otpCode = generateOtpCode();
        LocalDateTime otpExpiresAt = LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES);

        User user = userRepository.findByEmailIgnoreCase(email)
                .map(existingUser -> refreshPendingRegistration(existingUser, username, request.password(), otpCode, otpExpiresAt))
                .orElseGet(() -> createPendingUser(email, username, request.password(), otpCode, otpExpiresAt));

        userRepository.save(user);
        emailService.sendOtpEmail(user.getEmail(), user.getDisplayName(), otpCode);

        log.info("OTP registration created for email={}", user.getEmail());
        return new RegisterResponse(
                "Đăng ký thành công, vui lòng kiểm tra email để lấy mã OTP",
                user.getEmail(),
                "VERIFY_OTP"
        );
    }

    @Override
    @Transactional
    public JwtResponse verify(VerifyOtpRequest request) {
        String email = normalizeEmail(request.email());
        String otp = normalizeOtp(request.otp());

        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (!StringUtils.hasText(user.getOtpCode()) || !user.getOtpCode().equals(otp)) {
            throw new AppException(ErrorCode.INVALID_OTP);
        }

        if (user.getOtpExpiresAt() == null || user.getOtpExpiresAt().isBefore(LocalDateTime.now())) {
            throw new AppException(ErrorCode.OTP_EXPIRED);
        }

        user.setIsVerified(true);
        user.setOtpCode(null);
        user.setOtpExpiresAt(null);
        userRepository.save(user);

        String jwt = jwtUtils.generateTokenFromUserId(user.getId());
        return buildJwtResponse(user, jwt);
    }

    @Override
    public JwtResponse login(LoginRequest request) {
        String identifier = normalizeIdentifier(request.identifier());
        User user = userRepository.findByEmailIgnoreCaseOrUsernameIgnoreCase(identifier, identifier)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (!Boolean.TRUE.equals(user.getIsVerified())) {
            throw new AppException(ErrorCode.USER_NOT_VERIFIED);
        }

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(identifier, request.password())
        );

        String jwt = jwtUtils.generateTokenFromUserId(user.getId());
        return buildJwtResponse(user, jwt);
    }

    private User refreshPendingRegistration(User existingUser, String username, String rawPassword, String otpCode, LocalDateTime otpExpiresAt) {
        if (Boolean.TRUE.equals(existingUser.getIsVerified())) {
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        validateUsernameAvailability(username, existingUser.getId());
        existingUser.setUsername(username);
        existingUser.setPassword(passwordEncoder.encode(rawPassword));
        existingUser.setIsVerified(false);
        existingUser.setOtpCode(otpCode);
        existingUser.setOtpExpiresAt(otpExpiresAt);
        return existingUser;
    }

    private User createPendingUser(String email, String username, String rawPassword, String otpCode, LocalDateTime otpExpiresAt) {
        validateUsernameAvailability(username, null);
        return User.builder()
                .email(email)
                .username(username)
                .password(passwordEncoder.encode(rawPassword))
                .isVerified(false)
                .otpCode(otpCode)
                .otpExpiresAt(otpExpiresAt)
                .isGoldMember(false)
                .build();
    }

    private void validateUsernameAvailability(String username, UUID currentUserId) {
        userRepository.findByUsernameIgnoreCase(username)
                .filter(user -> currentUserId == null || !user.getId().equals(currentUserId))
                .ifPresent(user -> {
                    throw new AppException(ErrorCode.USERNAME_ALREADY_EXISTS);
                });
    }

    private JwtResponse buildJwtResponse(User user, String jwt) {
        boolean profileCompleted = Boolean.TRUE.equals(user.getProfileCompleted());
        return new JwtResponse(
                jwt,
                "Bearer",
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                user.getIsVerified(),
                profileCompleted,
                user.getDisplayName(),
                user.getAvatarUrl(),
                profileCompleted ? OnboardingStep.HOME : OnboardingStep.COMPLETE_PROFILE
        );
    }

    private String generateOtpCode() {
        return String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
    }

    private String normalizeEmail(String email) {
        return normalizeIdentifier(email);
    }

    private String normalizeUsername(String username) {
        if (!StringUtils.hasText(username)) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeIdentifier(String identifier) {
        if (!StringUtils.hasText(identifier)) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        return identifier.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeOtp(String otp) {
        if (!StringUtils.hasText(otp)) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        return otp.trim();
    }
}
