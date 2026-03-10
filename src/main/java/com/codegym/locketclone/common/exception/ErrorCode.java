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
    PHONE_NUMBER_ALREADY_EXISTS(HttpStatus.BAD_REQUEST, "Số điện thoại đã tồn tại"),
    USERNAME_ALREADY_EXISTS(HttpStatus.BAD_REQUEST, "Tên đăng nhập đã tồn tại"),

    // 401 Unauthorized
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Bạn chưa đăng nhập hoặc token không hợp lệ"),

    // 403 Forbidden
    FORBIDDEN(HttpStatus.FORBIDDEN, "Bạn không có quyền truy cập tài nguyên này"),

    // 404 Not Found
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng"),

    // 500 Internal Server Error
    UNCATEGORIZED_EXCEPTION(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi hệ thống không xác định");

    private final HttpStatus statusCode;
    private final String message;
}
