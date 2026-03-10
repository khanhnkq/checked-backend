package com.codegym.locketclone.common.exception;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {

    // Đảm bảo format ngày giờ trả về API dễ đọc
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;

    private int status;
    private String message;
    private String path;

    // Constructor 2 tham số để "chữa cháy" ngay lỗi đỏ trong GlobalExceptionHandler
    public ErrorResponse(int status, String message) {
        this.timestamp = LocalDateTime.now(); // Tự động lấy giờ hiện tại khi có lỗi
        this.status = status;
        this.message = message;
        this.path = ""; // Có thể update sau nếu cần map URI
    }
}