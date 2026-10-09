package com.codegym.locketclone.security.ratelimit;

import com.codegym.locketclone.common.exception.ErrorCode;
import com.codegym.locketclone.common.exception.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitingFilter extends OncePerRequestFilter {

    private final RateLimiterService rateLimiterService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!rateLimiterService.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        RateLimiterService.AuthEndpointType type = rateLimiterService.resolveEndpointType(
                request.getMethod(), request.getRequestURI()
        );

        if (type == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = resolveClientIp(request);
        ConsumptionProbe probe = rateLimiterService.tryConsume(clientIp, type);

        if (probe.isConsumed()) {
            filterChain.doFilter(request, response);
            return;
        }

        long waitNanos = probe.getNanosToWaitForRefill();
        long retryAfterSeconds = Math.max(1, (waitNanos + 999_999_999L) / 1_000_000_000L);

        log.warn("Rate limit exceeded for ip={}, endpoint={}, retryAfterSeconds={}",
                clientIp, type, retryAfterSeconds);

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                ErrorCode.TOO_MANY_REQUESTS.getMessage()
        );
        errorResponse.setPath(request.getRequestURI());

        objectMapper.writeValue(response.getOutputStream(), errorResponse);
    }

    public String resolveClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xForwardedFor)) {
            String[] ips = xForwardedFor.split(",");
            String candidate = ips[0].trim();
            if (StringUtils.hasText(candidate) && !"unknown".equalsIgnoreCase(candidate)) {
                return normalizeIp(candidate);
            }
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(xRealIp) && !"unknown".equalsIgnoreCase(xRealIp)) {
            return normalizeIp(xRealIp.trim());
        }

        return normalizeIp(request.getRemoteAddr());
    }

    private String normalizeIp(String ip) {
        if (!StringUtils.hasText(ip)) {
            return "unknown";
        }
        if ("0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) {
            return "127.0.0.1";
        }
        return ip;
    }
}
