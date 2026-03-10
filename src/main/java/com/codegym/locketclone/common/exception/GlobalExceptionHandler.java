package com.codegym.locketclone.common.exception;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // 1. Handle Custom AppException
    @ExceptionHandler(value = AppException.class)
    public ResponseEntity<ErrorResponse> handlingAppException(AppException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        return ResponseEntity
                .status(errorCode.getStatusCode())
                .body(new ErrorResponse(errorCode.getStatusCode().value(), errorCode.getMessage()));
    }

    // 2. Handle Validation Exceptions (DTO)
    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handlingValidation(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new HashMap<>();
        exception.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        // Gộp lỗi thành chuỗi hoặc trả về cấu trúc Map tùy bạn
        return ResponseEntity.badRequest().body(new ErrorResponse(400, errors.toString()));
    }

    // 3. Handle Constraint Violation (Validate Params ở Controller)
    @ExceptionHandler(value = ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handlingConstraintViolation(ConstraintViolationException exception) {
        return ResponseEntity.badRequest().body(new ErrorResponse(400, exception.getMessage()));
    }

    // 4. Handle Database Integrity (Ví dụ: Trùng Unique Key do quên check tay)
    @ExceptionHandler(value = DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handlingDataIntegrityViolation(DataIntegrityViolationException exception) {
        log.warn("Lỗi Data Integrity: ", exception); // Log warn vì lỗi này do client gửi data trùng
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(HttpStatus.CONFLICT.value(), "Dữ liệu đã tồn tại hoặc vi phạm ràng buộc cơ sở dữ liệu"));
    }

    // 5. Handle Security: Access Denied (403) do @PreAuthorize
    @ExceptionHandler(value = AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handlingAccessDeniedException(AccessDeniedException exception) {
        log.warn("Access Denied: ", exception);
        ErrorCode errorCode = ErrorCode.FORBIDDEN;
        return ResponseEntity.status(errorCode.getStatusCode())
                .body(new ErrorResponse(errorCode.getStatusCode().value(), errorCode.getMessage()));
    }

    // 6. Handle Security: Authentication Exception (401)
    @ExceptionHandler(value = AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handlingAuthenticationException(AuthenticationException exception) {
        log.warn("Authentication failed: ", exception);
        ErrorCode errorCode = ErrorCode.UNAUTHORIZED;
        return ResponseEntity.status(errorCode.getStatusCode())
                .body(new ErrorResponse(errorCode.getStatusCode().value(), errorCode.getMessage()));
    }

    // 7. CATCH-ALL: Bắt toàn bộ lỗi rác chưa được định nghĩa (Rất quan trọng phải có Log.error)
    @ExceptionHandler(value = Exception.class)
    public ResponseEntity<ErrorResponse> handlingRuntimeException(Exception exception) {
        log.error("Lỗi hệ thống không xác định (Unexpected Error): ", exception);
        ErrorCode errorCode = ErrorCode.UNCATEGORIZED_EXCEPTION;
        return ResponseEntity.internalServerError()
                .body(new ErrorResponse(errorCode.getStatusCode().value(), errorCode.getMessage()));
    }
}