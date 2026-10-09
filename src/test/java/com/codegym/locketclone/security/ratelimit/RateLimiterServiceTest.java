package com.codegym.locketclone.security.ratelimit;

import io.github.bucket4j.ConsumptionProbe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterServiceTest {

    private RateLimitProperties properties;
    private RateLimiterService service;

    @BeforeEach
    void setUp() {
        properties = new RateLimitProperties();
        properties.setEnabled(true);
        properties.setRegisterCapacity(3);
        properties.setVerifyCapacity(5);
        properties.setLoginCapacity(10);
        service = new RateLimiterService(properties);
    }

    @Test
    void resolveEndpointType_mapsEndpointsCorrectly() {
        assertEquals(RateLimiterService.AuthEndpointType.REGISTER,
                service.resolveEndpointType("POST", "/api/v1/auth/register"));
        assertEquals(RateLimiterService.AuthEndpointType.VERIFY,
                service.resolveEndpointType("POST", "/api/v1/auth/verify"));
        assertEquals(RateLimiterService.AuthEndpointType.LOGIN,
                service.resolveEndpointType("POST", "/api/v1/auth/login"));

        // Non-POST or other paths return null
        assertNull(service.resolveEndpointType("GET", "/api/v1/auth/login"));
        assertNull(service.resolveEndpointType("POST", "/api/v1/users"));
        assertNull(service.resolveEndpointType("POST", null));
    }

    @Test
    void tryConsume_allowsUpToCapacity_andRejectsAfterward() {
        String ip = "192.168.1.100";

        // Register capacity is 3
        for (int i = 0; i < 3; i++) {
            ConsumptionProbe probe = service.tryConsume(ip, RateLimiterService.AuthEndpointType.REGISTER);
            assertTrue(probe.isConsumed(), "Request " + (i + 1) + " should be consumed");
        }

        // 4th request must be rejected
        ConsumptionProbe probe4 = service.tryConsume(ip, RateLimiterService.AuthEndpointType.REGISTER);
        assertFalse(probe4.isConsumed(), "4th request should exceed capacity");
        assertTrue(probe4.getNanosToWaitForRefill() > 0);
    }

    @Test
    void tryConsume_isolatesBucketsByIp() {
        String ip1 = "10.0.0.1";
        String ip2 = "10.0.0.2";

        // Consume all 3 tokens for ip1
        for (int i = 0; i < 3; i++) {
            assertTrue(service.tryConsume(ip1, RateLimiterService.AuthEndpointType.REGISTER).isConsumed());
        }
        assertFalse(service.tryConsume(ip1, RateLimiterService.AuthEndpointType.REGISTER).isConsumed());

        // ip2 should still have fresh quota
        assertTrue(service.tryConsume(ip2, RateLimiterService.AuthEndpointType.REGISTER).isConsumed());
    }

    @Test
    void tryConsume_isolatesBucketsByEndpointType() {
        String ip = "172.16.0.1";

        // Exhaust register (3 tokens)
        for (int i = 0; i < 3; i++) {
            assertTrue(service.tryConsume(ip, RateLimiterService.AuthEndpointType.REGISTER).isConsumed());
        }
        assertFalse(service.tryConsume(ip, RateLimiterService.AuthEndpointType.REGISTER).isConsumed());

        // Login endpoint for same IP should still have its separate 10 tokens
        assertTrue(service.tryConsume(ip, RateLimiterService.AuthEndpointType.LOGIN).isConsumed());
    }
}
