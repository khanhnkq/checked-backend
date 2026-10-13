package com.codegym.locketclone.notification;

import com.codegym.locketclone.common.config.RabbitMQConfig;
import com.codegym.locketclone.notification.dto.OtpEmailMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service("rabbitMqEmailService")
@Primary
@Slf4j
public class RabbitMqEmailProducer implements EmailService {

    private final RabbitTemplate rabbitTemplate;
    private final EmailService fallbackEmailService;

    @Autowired
    public RabbitMqEmailProducer(
            RabbitTemplate rabbitTemplate,
            @Autowired(required = false) @Qualifier("smtpEmailService") EmailService smtpEmailService,
            @Qualifier("loggingEmailService") EmailService loggingEmailService
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.fallbackEmailService = smtpEmailService != null ? smtpEmailService : loggingEmailService;
    }

    @Override
    public void sendOtpEmail(String toEmail, String recipientName, String otpCode) {
        OtpEmailMessage message = OtpEmailMessage.of(toEmail, recipientName, otpCode);

        try {
            log.info("Publishing OTP email message to RabbitMQ exchange [{}] with routing key [{}] for recipient [{}]",
                    RabbitMQConfig.CHECKED_DIRECT_EXCHANGE,
                    RabbitMQConfig.MAIL_NOTIFICATION_ROUTING_KEY,
                    toEmail);

            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.CHECKED_DIRECT_EXCHANGE,
                    RabbitMQConfig.MAIL_NOTIFICATION_ROUTING_KEY,
                    message
            );
            log.info("Published OTP email message successfully to RabbitMQ for [{}]", toEmail);
        } catch (AmqpException ex) {
            log.warn("Không thể publish email message tới RabbitMQ ({}). Kích hoạt Graceful Fallback gửi trực tiếp...", ex.getMessage());
            try {
                fallbackEmailService.sendOtpEmail(toEmail, recipientName, otpCode);
            } catch (Exception fallbackEx) {
                log.error("Graceful Fallback gửi email thất bại cho [{}]: {}", toEmail, fallbackEx.getMessage(), fallbackEx);
            }
        }
    }
}
