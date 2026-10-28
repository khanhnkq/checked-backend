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
        LOGIN,
        FRIEND_REQUEST,
        PROFILE_VIEW,
        QR_GENERATION,
        SOCIAL_SHARE
    }

    private final RateLimitProperties properties;

    private final Cache<String, Bucket> bucketCache = Caffeine.newBuilder()
            .maximumSize(50_000)
            .expireAfterAccess(Duration.ofMinutes(15))
            .build();

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    public AuthEndpointType resolveEndpointType(String httpMethod, String requestUri) {
        if (httpMethod == null || requestUri == null) {
            return null;
        }

        if ("POST".equalsIgnoreCase(httpMethod)) {
            if (requestUri.endsWith("/api/v1/auth/register")) {
                return AuthEndpointType.REGISTER;
            }
            if (requestUri.endsWith("/api/v1/auth/verify")) {
                return AuthEndpointType.VERIFY;
            }
            if (requestUri.endsWith("/api/v1/auth/login")) {
                return AuthEndpointType.LOGIN;
            }
            if (requestUri.endsWith("/api/v1/friendships/requests")) {
                return AuthEndpointType.FRIEND_REQUEST;
            }
        } else if ("GET".equalsIgnoreCase(httpMethod) || "HEAD".equalsIgnoreCase(httpMethod)) {
            if (requestUri.contains("/api/v1/users/profile/") && requestUri.endsWith("/qr")) {
                return AuthEndpointType.QR_GENERATION;
            }
            if (requestUri.contains("/api/v1/users/profile/")) {
                return AuthEndpointType.PROFILE_VIEW;
            }
            if (requestUri.startsWith("/u/") || requestUri.startsWith("/invite") || requestUri.startsWith("/friend/accept")) {
                return AuthEndpointType.SOCIAL_SHARE;
            }
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
            case FRIEND_REQUEST -> properties.getFriendRequestCapacity();
            case PROFILE_VIEW -> properties.getProfileViewCapacity();
            case QR_GENERATION -> properties.getQrCapacity();
            case SOCIAL_SHARE -> properties.getShareCapacity();
        };

        Duration refillDuration = switch (type) {
            case FRIEND_REQUEST -> Duration.ofMinutes(10);
            default -> Duration.ofMinutes(1);
        };

        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(capacity, refillDuration)
                .build();

        return Bucket.builder()
                .addLimit(limit)
                .build();
    }
}
