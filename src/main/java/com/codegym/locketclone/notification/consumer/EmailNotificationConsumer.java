package com.codegym.locketclone.notification.consumer;

import com.codegym.locketclone.common.config.RabbitMQConfig;
import com.codegym.locketclone.notification.LoggingEmailService;
import com.codegym.locketclone.notification.SmtpEmailService;
import com.codegym.locketclone.notification.dto.OtpEmailMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@Lazy(false)
@Slf4j
public class EmailNotificationConsumer {

    private final SmtpEmailService smtpEmailService;
    private final LoggingEmailService loggingEmailService;

    @Autowired
    public EmailNotificationConsumer(
            @Autowired(required = false) @Qualifier("smtpEmailService") SmtpEmailService smtpEmailService,
            @Qualifier("loggingEmailService") LoggingEmailService loggingEmailService
    ) {
        this.smtpEmailService = smtpEmailService;
        this.loggingEmailService = loggingEmailService;
    }

    @RabbitListener(queues = RabbitMQConfig.MAIL_NOTIFICATION_QUEUE)
    public void handleEmailNotification(OtpEmailMessage message) {
        if (message == null || message.toEmail() == null) {
            log.warn("Bỏ qua email notification message rỗng hoặc không có toEmail");
            return;
        }

        log.info("Consumer đã nhận message từ RabbitMQ [{}] cho [{}] (Type: {}, OTP: {})",
                RabbitMQConfig.MAIL_NOTIFICATION_QUEUE,
                message.toEmail(),
                message.messageType(),
                message.otpCode());

        if (smtpEmailService != null) {
            // Gửi qua SMTP thực tế (sẽ ném MailException nếu thất bại để kích hoạt Retry / DLQ)
            smtpEmailService.sendOtpEmailDirect(message.toEmail(), message.recipientName(), message.otpCode());
        } else {
            // Môi trường dev/test không cấu hình SMTP host -> Fallback ghi log
            loggingEmailService.sendOtpEmail(message.toEmail(), message.recipientName(), message.otpCode());
        }

        log.info("Consumer xử lý gửi email thành công cho [{}]", message.toEmail());
    }
}
