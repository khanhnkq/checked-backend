package com.codegym.locketclone.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Primary
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.mail.host")
public class SmtpEmailService implements EmailService {
    private final JavaMailSender mailSender;

    @Value("${locket.mail.from:${spring.mail.username}}")
    private String fromEmail;

    @Override
    public void sendOtpEmail(String toEmail, String recipientName, String otpCode) {
        String displayName = StringUtils.hasText(recipientName) ? recipientName.trim() : "bạn";

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(toEmail);
        message.setSubject("Mã xác thực SnapWidget");
        message.setText("Chào " + displayName + ", mã xác thực SnapWidget của bạn là " + otpCode);
        mailSender.send(message);
    }
}
