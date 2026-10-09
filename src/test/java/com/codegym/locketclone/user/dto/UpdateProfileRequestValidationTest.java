package com.codegym.locketclone.user.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateProfileRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    void updateProfileRequest_avatarUrlUpTo500Chars_passesValidation() {
        String longUrl = "https://cdn.example.com/avatar/" + "a".repeat(450);
        assertTrue(longUrl.length() <= 500 && longUrl.length() > 255);

        UpdateProfileRequest request = new UpdateProfileRequest("john_doe", "John", "Doe", longUrl);
        Set<ConstraintViolation<UpdateProfileRequest>> violations = validator.validate(request);

        assertTrue(violations.isEmpty());
    }

    @Test
    void updateProfileRequest_avatarUrlExactly500Chars_passesValidation() {
        String prefix = "https://cdn.example.com/";
        String exact500 = prefix + "a".repeat(500 - prefix.length());

        UpdateProfileRequest request = new UpdateProfileRequest("john_doe", "John", "Doe", exact500);
        Set<ConstraintViolation<UpdateProfileRequest>> violations = validator.validate(request);

        assertTrue(violations.isEmpty());
    }

    @Test
    void updateProfileRequest_avatarUrlExceeds500Chars_failsValidation() {
        String prefix = "https://cdn.example.com/";
        String exact501 = prefix + "a".repeat(501 - prefix.length());

        UpdateProfileRequest request = new UpdateProfileRequest("john_doe", "John", "Doe", exact501);
        Set<ConstraintViolation<UpdateProfileRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
        boolean hasAvatarError = violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("avatarUrl"));
        assertTrue(hasAvatarError);
    }

    @Test
    void updatePersonalInfoRequest_avatarUrlUpTo500Chars_passesValidation() {
        String longUrl = "https://cdn.example.com/avatar/" + "b".repeat(450);
        assertTrue(longUrl.length() <= 500 && longUrl.length() > 255);

        UpdatePersonalInfoRequest request = new UpdatePersonalInfoRequest("john_doe", "John", "Doe", longUrl);
        Set<ConstraintViolation<UpdatePersonalInfoRequest>> violations = validator.validate(request);

        assertTrue(violations.isEmpty());
    }

    @Test
    void updatePersonalInfoRequest_avatarUrlExceeds500Chars_failsValidation() {
        String prefix = "https://cdn.example.com/";
        String exact501 = prefix + "b".repeat(501 - prefix.length());

        UpdatePersonalInfoRequest request = new UpdatePersonalInfoRequest("john_doe", "John", "Doe", exact501);
        Set<ConstraintViolation<UpdatePersonalInfoRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
        boolean hasAvatarError = violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("avatarUrl"));
        assertTrue(hasAvatarError);
    }
}
