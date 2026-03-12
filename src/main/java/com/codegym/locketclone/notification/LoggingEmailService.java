package com.codegym.locketclone.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Slf4j
public class LoggingEmailService implements EmailService {
    @Override
    public void sendOtpEmail(String toEmail, String recipientName, String otpCode) {
        String displayName = StringUtils.hasText(recipientName) ? recipientName.trim() : "bạn";
        log.info("Sending OTP email to {} with content: Chào {}, mã xác thực SnapWidget của bạn là {}", toEmail, displayName, otpCode);
    }
}
