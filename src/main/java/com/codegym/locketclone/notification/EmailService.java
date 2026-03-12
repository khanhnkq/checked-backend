package com.codegym.locketclone.notification;

public interface EmailService {
    void sendOtpEmail(String toEmail, String recipientName, String otpCode);
}

