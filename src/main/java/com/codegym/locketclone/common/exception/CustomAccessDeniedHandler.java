package com.codegym.locketclone.common.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Slf4j
@RequiredArgsConstructor
public class CustomAccessDeniedHandler implements AccessDeniedHandler {
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) throws IOException, ServletException {
        log.error("Lỗi phân quyền (403 Forbidden): {}", accessDeniedException.getMessage());

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);

        ErrorCode errorCode = ErrorCode.FORBIDDEN;
        ErrorResponse errorResponse = new ErrorResponse(errorCode.getStatusCode().value(), errorCode.getMessage());
        errorResponse.setPath(request.getRequestURI());

        objectMapper.writeValue(response.getOutputStream(), errorResponse);

    }
}
