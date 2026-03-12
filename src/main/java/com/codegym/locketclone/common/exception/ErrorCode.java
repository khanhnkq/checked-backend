package com.codegym.locketclone.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;
@Getter
@AllArgsConstructor
public enum ErrorCode {
    // 400 Bad Request
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "Dữ liệu yêu cầu không hợp lệ"),
    USER_ALREADY_EXISTS(HttpStatus.BAD_REQUEST, "Người dùng đã tồn tại"),
    EMAIL_ALREADY_EXISTS(HttpStatus.BAD_REQUEST, "Email đã được sử dụng"),
    USERNAME_ALREADY_EXISTS(HttpStatus.BAD_REQUEST, "Username đã được sử dụng"),
    PASSWORD_NOT_SET(HttpStatus.BAD_REQUEST, "Bạn chưa thiết lập mật khẩu"),
    INVALID_OTP(HttpStatus.BAD_REQUEST, "Mã OTP không chính xác"),
    OTP_EXPIRED(HttpStatus.BAD_REQUEST, "Mã OTP đã hết hạn"),

    // 401 Unauthorized
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Bạn chưa đăng nhập hoặc token không hợp lệ"),

    // 403 Forbidden
    FORBIDDEN(HttpStatus.FORBIDDEN, "Bạn không có quyền truy cập tài nguyên này"),
    USER_NOT_VERIFIED(HttpStatus.FORBIDDEN, "Tài khoản chưa được xác thực email"),

    // 404 Not Found
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng"),

    // 500 Internal Server Error
    UNCATEGORIZED_EXCEPTION(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi hệ thống không xác định");

    private final HttpStatus statusCode;
    private final String message;
}
