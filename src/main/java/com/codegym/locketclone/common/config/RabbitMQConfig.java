package com.codegym.locketclone.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // Exchange names
    public static final String CHECKED_DIRECT_EXCHANGE = "checked.direct.exchange";
    public static final String CHECKED_DLX_EXCHANGE = "checked.dlx.exchange";

    // Queue names
    public static final String PHOTO_UPLOAD_QUEUE = "photo.upload.queue";
    public static final String MAIL_NOTIFICATION_QUEUE = "mail.notification.queue";
    public static final String CHAT_FANOUT_QUEUE = "chat.fanout.queue";
    public static final String DEAD_LETTER_QUEUE = "checked.dead-letter.queue";

    // Routing keys
    public static final String PHOTO_UPLOAD_ROUTING_KEY = "photo.upload";
    public static final String MAIL_NOTIFICATION_ROUTING_KEY = "mail.send";
    public static final String CHAT_FANOUT_ROUTING_KEY = "chat.event";

    @Bean
    public DirectExchange checkedDirectExchange() {
        return new DirectExchange(CHECKED_DIRECT_EXCHANGE, true, false);
    }

    @Bean
    public FanoutExchange checkedDeadLetterExchange() {
        return new FanoutExchange(CHECKED_DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Binding deadLetterBinding(Queue deadLetterQueue, FanoutExchange checkedDeadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(checkedDeadLetterExchange);
    }

    @Bean
    public Queue photoUploadQueue() {
        return QueueBuilder.durable(PHOTO_UPLOAD_QUEUE)
                .withArgument("x-dead-letter-exchange", CHECKED_DLX_EXCHANGE)
                .build();
    }

    @Bean
    public Binding photoUploadBinding(Queue photoUploadQueue, DirectExchange checkedDirectExchange) {
        return BindingBuilder.bind(photoUploadQueue).to(checkedDirectExchange).with(PHOTO_UPLOAD_ROUTING_KEY);
    }

    @Bean
    public Queue mailNotificationQueue() {
        return QueueBuilder.durable(MAIL_NOTIFICATION_QUEUE)
                .withArgument("x-dead-letter-exchange", CHECKED_DLX_EXCHANGE)
                .build();
    }

    @Bean
    public Binding mailNotificationBinding(Queue mailNotificationQueue, DirectExchange checkedDirectExchange) {
        return BindingBuilder.bind(mailNotificationQueue).to(checkedDirectExchange).with(MAIL_NOTIFICATION_ROUTING_KEY);
    }

    @Bean
    public Queue chatFanoutQueue() {
        return QueueBuilder.durable(CHAT_FANOUT_QUEUE)
                .withArgument("x-dead-letter-exchange", CHECKED_DLX_EXCHANGE)
                .build();
    }

    @Bean
    public Binding chatFanoutBinding(Queue chatFanoutQueue, DirectExchange checkedDirectExchange) {
        return BindingBuilder.bind(chatFanoutQueue).to(checkedDirectExchange).with(CHAT_FANOUT_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }

    @Bean
    public org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter jsonMessageConverter
    ) {
        org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory factory =
                new org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
