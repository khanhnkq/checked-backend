package com.codegym.locketclone.security.jwt;

import com.codegym.locketclone.security.service.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilsTest {

    private static final String VALID_SECRET = "dGVzdC1qd3Qtc2VjcmV0LXRlc3Qtand0LXNlY3JldC10ZXN0LWp3dC1zZWNyZXQ=";
    private JwtUtils jwtUtils;

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "jwtSecret", VALID_SECRET);
        ReflectionTestUtils.setField(jwtUtils, "jwtExpirationMs", 3600000);
        jwtUtils.init();
    }

    @Test
    void generateAndValidateToken_success() {
        UUID userId = UUID.randomUUID();
        UserPrincipal principal = new UserPrincipal(
                userId,
                "khanh",
                "khanh@example.com",
                "pwd",
                AuthorityUtils.createAuthorityList("ROLE_USER"),
                true
        );

        String token = jwtUtils.generateToken(principal);
        assertNotNull(token);
        assertTrue(jwtUtils.validateToken(token));
        assertEquals(userId, jwtUtils.getUserIdFromJWT(token));
    }

    @Test
    void generateTokenFromUserId_success() {
        UUID userId = UUID.randomUUID();
        String token = jwtUtils.generateTokenFromUserId(userId);
        assertNotNull(token);
        assertTrue(jwtUtils.validateToken(token));
        assertEquals(userId, jwtUtils.getUserIdFromJWT(token));
    }

    @Test
    void validateToken_returnsFalseForInvalidToken() {
        assertFalse(jwtUtils.validateToken("invalid.token.here"));
    }

    @Test
    void init_throwsIllegalStateExceptionWhenSecretIsEmpty() {
        JwtUtils invalidJwt = new JwtUtils();
        ReflectionTestUtils.setField(invalidJwt, "jwtSecret", "");
        assertThrows(IllegalStateException.class, invalidJwt::init);
    }

    @Test
    void init_throwsIllegalStateExceptionWhenSecretTooShort() {
        JwtUtils shortKeyJwt = new JwtUtils();
        // Base64 decode của "c2hvcnQ=" là "short" (5 bytes < 32 bytes)
        ReflectionTestUtils.setField(shortKeyJwt, "jwtSecret", "c2hvcnQ=");
        assertThrows(IllegalStateException.class, shortKeyJwt::init);
    }

    @Test
    void init_throwsIllegalStateExceptionWhenSecretNotBase64() {
        JwtUtils nonBase64Jwt = new JwtUtils();
        ReflectionTestUtils.setField(nonBase64Jwt, "jwtSecret", "not_valid_base64_!@#$");
        assertThrows(IllegalStateException.class, nonBase64Jwt::init);
    }
}
