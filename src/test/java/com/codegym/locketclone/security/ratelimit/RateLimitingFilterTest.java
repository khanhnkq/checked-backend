package com.codegym.locketclone.security.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitingFilterTest {

    @Mock
    private RateLimiterService rateLimiterService;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private RateLimitingFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitingFilter(rateLimiterService, objectMapper);
    }

    @Test
    void doFilter_skipsWhenRateLimitingDisabled() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        when(rateLimiterService.isEnabled()).thenReturn(false);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(rateLimiterService, never()).tryConsume(any(), any());
    }

    @Test
    void doFilter_skipsWhenNotRateLimitedEndpoint() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/photos");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        when(rateLimiterService.isEnabled()).thenReturn(true);
        when(rateLimiterService.resolveEndpointType("GET", "/api/v1/photos")).thenReturn(null);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(rateLimiterService, never()).tryConsume(any(), any());
    }

    @Test
    void doFilter_allowsRequestWhenTokenConsumed() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr("1.2.3.4");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        when(rateLimiterService.isEnabled()).thenReturn(true);
        when(rateLimiterService.resolveEndpointType("POST", "/api/v1/auth/login"))
                .thenReturn(RateLimiterService.AuthEndpointType.LOGIN);
        when(rateLimiterService.tryConsume("1.2.3.4", RateLimiterService.AuthEndpointType.LOGIN))
                .thenReturn(ConsumptionProbe.consumed(9, 0));

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    void doFilter_returns429WhenLimitExceeded() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr("1.2.3.4");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        when(rateLimiterService.isEnabled()).thenReturn(true);
        when(rateLimiterService.resolveEndpointType("POST", "/api/v1/auth/login"))
                .thenReturn(RateLimiterService.AuthEndpointType.LOGIN);
        when(rateLimiterService.tryConsume("1.2.3.4", RateLimiterService.AuthEndpointType.LOGIN))
                .thenReturn(ConsumptionProbe.rejected(0, 15_000_000_000L, 0)); // 15 seconds wait

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(any(), any());
        assertEquals(429, response.getStatus());
        assertEquals("15", response.getHeader("Retry-After"));
        assertTrue(response.getContentAsString().contains("\"status\":429"));
        assertTrue(response.getContentAsString().contains("Bạn đã gửi quá nhiều yêu cầu"));
    }

    @Test
    void resolveClientIp_extractsFromXForwardedFor_andNormalizes() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.195, 70.41.3.18");

        String ip = filter.resolveClientIp(request);
        assertEquals("203.0.113.195", ip);

        MockHttpServletRequest ipv6Request = new MockHttpServletRequest();
        ipv6Request.setRemoteAddr("0:0:0:0:0:0:0:1");
        assertEquals("127.0.0.1", filter.resolveClientIp(ipv6Request));

        MockHttpServletRequest xRealIpRequest = new MockHttpServletRequest();
        xRealIpRequest.addHeader("X-Real-IP", "198.51.100.1");
        assertEquals("198.51.100.1", filter.resolveClientIp(xRealIpRequest));
    }
}
