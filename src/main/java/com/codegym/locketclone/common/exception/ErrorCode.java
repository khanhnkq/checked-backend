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
    INVALID_PHOTO_FILE(HttpStatus.BAD_REQUEST, "Ảnh tải lên không hợp lệ"),
    INVALID_PHOTO_AMOUNT(HttpStatus.BAD_REQUEST, "Số tiền phải lớn hơn hoặc bằng 0"),
    INVALID_MONTH_KEY(HttpStatus.BAD_REQUEST, "monthKey phải đúng định dạng yyyyMM"),
    INVALID_BUDGET_LIMIT(HttpStatus.BAD_REQUEST, "Ngân sách tháng phải lớn hơn 0"),
    INVALID_ALERT_THRESHOLD(HttpStatus.BAD_REQUEST, "Ngưỡng cảnh báo phải trong khoảng 1-100"),
    CATEGORY_NAME_ALREADY_EXISTS(HttpStatus.BAD_REQUEST, "Tên danh mục đã tồn tại"),
    RECIPIENTS_REQUIRED(HttpStatus.BAD_REQUEST, "Vui lòng chọn ít nhất một người nhận"),
    INVALID_RECIPIENT_SELECTION(HttpStatus.BAD_REQUEST, "Danh sách người nhận không hợp lệ hoặc chưa là bạn bè"),

    // 401 Unauthorized
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Bạn chưa đăng nhập hoặc token không hợp lệ"),

    // 403 Forbidden
    FORBIDDEN(HttpStatus.FORBIDDEN, "Bạn không có quyền truy cập tài nguyên này"),
    USER_NOT_VERIFIED(HttpStatus.FORBIDDEN, "Tài khoản chưa được xác thực email"),

    // 404 Not Found
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng"),
    PHOTO_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy ảnh"),
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy danh mục"),

    // 500 Internal Server Error
    UNCATEGORIZED_EXCEPTION(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi hệ thống không xác định");

    private final HttpStatus statusCode;
    private final String message;
}
