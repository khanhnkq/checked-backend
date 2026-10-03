package com.codegym.locketclone.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Primary
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.mail.host")
@Slf4j
public class SmtpEmailService implements EmailService {
    private final JavaMailSender mailSender;

    @Value("${locket.mail.from:${spring.mail.username}}")
    private String fromEmail;

    @Async
    @Override
    public void sendOtpEmail(String toEmail, String recipientName, String otpCode) {
        try {
            String displayName = StringUtils.hasText(recipientName) ? recipientName.trim() : "bạn";

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("Mã xác thực SnapWidget");
            message.setText("Chào " + displayName + ", mã xác thực SnapWidget của bạn là " + otpCode);
            mailSender.send(message);
            log.info("Đã gửi email OTP thành công tới {}", toEmail);
        } catch (Exception ex) {
            log.error("Lỗi khi gửi email OTP tới {}: {}", toEmail, ex.getMessage(), ex);
        }
    }
}
