package com.codegym.locketclone.common.exception;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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
        return ResponseEntity.badRequest().body(new ErrorResponse(400, "Dữ liệu yêu cầu không hợp lệ", errors));
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

    // 7. Handle Standard Spring MVC Exceptions (404, 405, 415, 400)
    @ExceptionHandler(value = NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handlingNoResourceFoundException(NoResourceFoundException exception) {
        log.warn("Tài nguyên không tìm thấy (404): {}", exception.getResourcePath());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(HttpStatus.NOT_FOUND.value(), "Tài nguyên không tồn tại: " + exception.getResourcePath()));
    }

    @ExceptionHandler(value = HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handlingMethodNotSupported(HttpRequestMethodNotSupportedException exception) {
        log.warn("Phương thức HTTP không hỗ trợ (405): {}", exception.getMethod());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(new ErrorResponse(HttpStatus.METHOD_NOT_ALLOWED.value(), "Phương thức HTTP không được hỗ trợ: " + exception.getMethod()));
    }

    @ExceptionHandler(value = HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handlingMediaTypeNotSupported(HttpMediaTypeNotSupportedException exception) {
        log.warn("Media Type không hỗ trợ (415): {}", exception.getContentType());
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(new ErrorResponse(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(), "Định dạng dữ liệu không được hỗ trợ"));
    }

    @ExceptionHandler(value = MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handlingMissingServletRequestParameter(MissingServletRequestParameterException exception) {
        log.warn("Thiếu tham số yêu cầu (400): {}", exception.getParameterName());
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "Thiếu tham số yêu cầu: " + exception.getParameterName()));
    }

    @ExceptionHandler(value = MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handlingMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException exception) {
        log.warn("Sai kiểu tham số (400): {}", exception.getName());
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "Tham số không hợp lệ: " + exception.getName()));
    }

    @ExceptionHandler(value = HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handlingHttpMessageNotReadable(HttpMessageNotReadableException exception) {
        log.warn("Không đọc được nội dung body (400): {}", exception.getMessage());
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "Dữ liệu yêu cầu không hợp lệ hoặc sai định dạng JSON"));
    }

    // 8. CATCH-ALL: Bắt toàn bộ lỗi rác chưa được định nghĩa (Rất quan trọng phải có Log.error)
    @ExceptionHandler(value = Exception.class)
    public ResponseEntity<ErrorResponse> handlingRuntimeException(Exception exception) {
        log.error("Lỗi hệ thống không xác định (Unexpected Error): ", exception);
        ErrorCode errorCode = ErrorCode.UNCATEGORIZED_EXCEPTION;
        return ResponseEntity.internalServerError()
                .body(new ErrorResponse(errorCode.getStatusCode().value(), errorCode.getMessage()));
    }
}