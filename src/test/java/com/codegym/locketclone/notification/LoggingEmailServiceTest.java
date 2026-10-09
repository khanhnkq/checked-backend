package com.codegym.locketclone.notification;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class LoggingEmailServiceTest {

    @Test
    void sendOtpEmail_doesNotThrowException() {
        LoggingEmailService service = new LoggingEmailService();
        assertDoesNotThrow(() -> service.sendOtpEmail("test@example.com", "Alice", "123456"));
        assertDoesNotThrow(() -> service.sendOtpEmail("test@example.com", null, "654321"));
        assertDoesNotThrow(() -> service.sendOtpEmail("test@example.com", "   ", "999999"));
    }
}
