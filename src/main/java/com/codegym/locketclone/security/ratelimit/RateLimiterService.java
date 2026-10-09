package com.codegym.locketclone.security.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RateLimiterService {

    public enum AuthEndpointType {
        REGISTER,
        VERIFY,
        LOGIN
    }

    private final RateLimitProperties properties;

    private final Cache<String, Bucket> bucketCache = Caffeine.newBuilder()
            .maximumSize(50_000)
            .expireAfterAccess(Duration.ofMinutes(10))
            .build();

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    public AuthEndpointType resolveEndpointType(String httpMethod, String requestUri) {
        if (!"POST".equalsIgnoreCase(httpMethod) || requestUri == null) {
            return null;
        }

        if (requestUri.endsWith("/api/v1/auth/register")) {
            return AuthEndpointType.REGISTER;
        }
        if (requestUri.endsWith("/api/v1/auth/verify")) {
            return AuthEndpointType.VERIFY;
        }
        if (requestUri.endsWith("/api/v1/auth/login")) {
            return AuthEndpointType.LOGIN;
        }
        return null;
    }

    public ConsumptionProbe tryConsume(String clientIp, AuthEndpointType type) {
        String cacheKey = clientIp + ":" + type.name();
        Bucket bucket = bucketCache.get(cacheKey, k -> createBucket(type));
        return bucket.tryConsumeAndReturnRemaining(1);
    }

    private Bucket createBucket(AuthEndpointType type) {
        int capacity = switch (type) {
            case REGISTER -> properties.getRegisterCapacity();
            case VERIFY -> properties.getVerifyCapacity();
            case LOGIN -> properties.getLoginCapacity();
        };

        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(capacity, Duration.ofMinutes(1))
                .build();

        return Bucket.builder()
                .addLimit(limit)
                .build();
    }
}
