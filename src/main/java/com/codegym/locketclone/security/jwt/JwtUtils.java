package com.codegym.locketclone.security.jwt;


import com.codegym.locketclone.security.service.UserPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

@Component
@Slf4j
public class JwtUtils {
    @Value("${locket.app.jwtSecret:}")
    private String jwtSecret;

    @Value("${locket.app.jwtExpirationMs:86400000}")
    private int jwtExpirationMs;

    private SecretKey signingKey;

    @PostConstruct
    public void init() {
        this.signingKey = validateAndBuildSigningKey(this.jwtSecret);
    }

    public SecretKey validateAndBuildSigningKey(String secret) {
        if (!StringUtils.hasText(secret)) {
            throw new IllegalStateException("Cấu hình locket.app.jwtSecret không được để trống!");
        }
        try {
            byte[] keyBytes = Decoders.BASE64.decode(secret.trim());
            if (keyBytes.length < 32) {
                throw new IllegalStateException("locket.app.jwtSecret phải có độ dài tối thiểu 256 bits (32 bytes sau khi decode Base64) để đảm bảo an toàn HMAC-SHA!");
            }
            return Keys.hmacShaKeyFor(keyBytes);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("locket.app.jwtSecret không đúng định dạng Base64 hợp lệ: " + ex.getMessage(), ex);
        }
    }

    private SecretKey getSigningKey() {
        if (this.signingKey == null) {
            this.signingKey = validateAndBuildSigningKey(this.jwtSecret);
        }
        return this.signingKey;
    }

    public String generateToken(UserPrincipal userPrincipal) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationMs);
        return Jwts.builder()
                .subject(userPrincipal.getId().toString()) // Chuyển UUID sang String
                .claim("username", userPrincipal.getUsername())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    public String generateTokenFromUserId(UUID userId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationMs);
        return Jwts.builder()
                .subject(userId.toString())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    public UUID getUserIdFromJWT(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return UUID.fromString(claims.getSubject());
    }

    public boolean validateToken(String authToken) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(authToken);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            log.error("Lỗi xác thực JWT: {}", ex.getMessage());
        }
        return false;
    }
}
