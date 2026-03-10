package com.codegym.locketclone.security.jwt;

import com.codegym.locketclone.common.exception.ErrorCode;
import com.codegym.locketclone.common.exception.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException, ServletException {
        log.error("Lỗi xác thực (401 Unauthorized): {}", authException.getMessage());

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        // Sử dụng ErrorCode đã định nghĩa ban nãy
        ErrorCode errorCode = ErrorCode.UNAUTHORIZED;
        ErrorResponse errorResponse = new ErrorResponse(errorCode.getStatusCode().value(), errorCode.getMessage());
        errorResponse.setPath(request.getRequestURI()); // Ghi lại đường dẫn bị lỗi

        // Ghi trực tiếp object JSON vào luồng trả về (response body)
        objectMapper.writeValue(response.getOutputStream(), errorResponse);
    }
}
