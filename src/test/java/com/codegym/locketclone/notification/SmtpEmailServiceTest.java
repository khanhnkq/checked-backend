package com.codegym.locketclone.notification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SmtpEmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    private SmtpEmailService emailService;

    @BeforeEach
    void setUp() {
        emailService = new SmtpEmailService(mailSender);
        ReflectionTestUtils.setField(emailService, "fromEmail", "noreply@checked.com");
    }

    @Test
    void sendOtpEmail_success_sendsCorrectEmailMessage() {
        emailService.sendOtpEmail("user@example.com", "John Doe", "123456");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(1)).send(captor.capture());

        SimpleMailMessage sentMessage = captor.getValue();
        assertEquals("noreply@checked.com", sentMessage.getFrom());
        assertNotNull(sentMessage.getTo());
        assertEquals(1, sentMessage.getTo().length);
        assertEquals("user@example.com", sentMessage.getTo()[0]);
        assertEquals("Mã xác thực Checked", sentMessage.getSubject());
        assertTrue(sentMessage.getText().contains("John Doe"));
        assertTrue(sentMessage.getText().contains("123456"));
    }

    @Test
    void sendOtpEmail_blankRecipientName_fallsBackToDefault() {
        emailService.sendOtpEmail("user@example.com", "   ", "654321");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(1)).send(captor.capture());

        SimpleMailMessage sentMessage = captor.getValue();
        assertTrue(sentMessage.getText().contains("Chào bạn"));
        assertTrue(sentMessage.getText().contains("654321"));
    }

    @Test
    void sendOtpEmail_mailException_handlesGracefullyWithoutThrowing() {
        doThrow(new MailSendException("SMTP connection failed")).when(mailSender).send(any(SimpleMailMessage.class));

        assertDoesNotThrow(() -> emailService.sendOtpEmail("user@example.com", "John", "123456"));
        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }
}
