package com.codegym.locketclone.notification.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

public record OtpEmailMessage(
        String toEmail,
        String recipientName,
        String otpCode,
        String subject,
        String messageType,
        LocalDateTime createdAt
) implements Serializable {

    public static OtpEmailMessage of(String toEmail, String recipientName, String otpCode) {
        return new OtpEmailMessage(
                toEmail,
                recipientName,
                otpCode,
                "Mã xác thực Checked",
                "OTP_VERIFICATION",
                LocalDateTime.now()
        );
    }
}
