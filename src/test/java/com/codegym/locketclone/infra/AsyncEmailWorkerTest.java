package com.codegym.locketclone.infra;

import com.codegym.locketclone.LocketCloneApplication;
import com.codegym.locketclone.common.config.RabbitMQConfig;
import com.codegym.locketclone.notification.EmailService;
import com.codegym.locketclone.notification.RabbitMqEmailProducer;
import com.codegym.locketclone.notification.consumer.EmailNotificationConsumer;
import com.codegym.locketclone.notification.dto.OtpEmailMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = LocketCloneApplication.class)
class AsyncEmailWorkerTest {

    @Autowired
    private EmailService emailService;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private EmailNotificationConsumer emailNotificationConsumer;

    @Autowired
    @Qualifier("loggingEmailService")
    private EmailService loggingEmailService;

    @Test
    @DisplayName("Verify Producer Bean: EmailService phải được inject bởi RabbitMqEmailProducer (@Primary)")
    void testEmailServiceIsRabbitMqProducer() {
        assertNotNull(emailService);
        assertInstanceOf(RabbitMqEmailProducer.class, emailService,
                "EmailService trong Spring context phải là RabbitMqEmailProducer");
    }

    @Test
    @DisplayName("Verify Async Flow: Gửi email OTP qua RabbitMQ Producer thành công không ném ngoại lệ")
    void testSendOtpEmailAsynchronously() {
        String testEmail = "async-test-" + System.currentTimeMillis() + "@example.com";
        String recipientName = "Quốc Khánh";
        String otpCode = "123456";

        // Thực thi gửi email qua Primary EmailService (RabbitMqEmailProducer)
        assertDoesNotThrow(() -> emailService.sendOtpEmail(testEmail, recipientName, otpCode),
                "Gửi email OTP bất đồng bộ qua RabbitMQ không được ném ngoại lệ");
    }

    @Test
    @DisplayName("Verify Message Roundtrip: Publish OtpEmailMessage và Consumer nhận xử lý an toàn")
    void testDirectMessagePublishAndConsumerProcessing() {
        String testEmail = "roundtrip-" + System.currentTimeMillis() + "@example.com";
        OtpEmailMessage message = OtpEmailMessage.of(testEmail, "Khanh Nguyen", "654321");

        // Gửi trực tiếp qua RabbitTemplate tới exchange và routing key
        assertDoesNotThrow(() -> rabbitTemplate.convertAndSend(
                RabbitMQConfig.CHECKED_DIRECT_EXCHANGE,
                RabbitMQConfig.MAIL_NOTIFICATION_ROUTING_KEY,
                message
        ));

        // Consumer trực tiếp xử lý message không bị lỗi
        assertDoesNotThrow(() -> emailNotificationConsumer.handleEmailNotification(message),
                "EmailNotificationConsumer phải xử lý OtpEmailMessage thành công");
    }

    @Test
    @DisplayName("Verify Dead Letter Queue (DLQ) Topology: DLQ và DLX được đăng ký chính xác trên RabbitMQ Broker")
    void testDeadLetterQueueTopology() {
        // Kiểm tra Queue mail.notification.queue tồn tại
        QueueInformation mailQueueInfo = amqpAdmin.getQueueInfo(RabbitMQConfig.MAIL_NOTIFICATION_QUEUE);
        assertNotNull(mailQueueInfo, "Queue 'mail.notification.queue' phải tồn tại trên RabbitMQ broker");

        // Kiểm tra Queue checked.dead-letter.queue (DLQ) tồn tại
        QueueInformation dlqInfo = amqpAdmin.getQueueInfo(RabbitMQConfig.DEAD_LETTER_QUEUE);
        assertNotNull(dlqInfo, "Queue 'checked.dead-letter.queue' (DLQ) phải tồn tại trên RabbitMQ broker");
    }

    @Test
    @DisplayName("Verify Graceful Fallback: Khi RabbitMQ broker ném ngoại lệ, Producer tự động fallback gửi trực tiếp")
    void testProducerGracefulFallbackWhenRabbitThrowsException() {
        RabbitTemplate mockRabbitTemplate = Mockito.mock(RabbitTemplate.class);
        EmailService mockFallbackService = Mockito.mock(EmailService.class);

        // Giả lập RabbitTemplate bị timeout hoặc mất kết nối mạng
        Mockito.doThrow(new AmqpException("Simulated CloudAMQP Network Failure"))
                .when(mockRabbitTemplate).convertAndSend(
                        Mockito.anyString(),
                        Mockito.anyString(),
                        Mockito.any(Object.class)
                );

        RabbitMqEmailProducer fallbackProducer = new RabbitMqEmailProducer(
                mockRabbitTemplate,
                null,
                mockFallbackService
        );

        String testEmail = "fallback@example.com";
        assertDoesNotThrow(() -> fallbackProducer.sendOtpEmail(testEmail, "User", "999888"),
                "Producer phải nuốt lỗi AmqpException và kích hoạt fallback mà không ném 500");

        // Xác nhận fallbackService được gọi
        Mockito.verify(mockFallbackService, Mockito.times(1))
                .sendOtpEmail(testEmail, "User", "999888");
    }
}
