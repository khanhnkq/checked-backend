package com.codegym.locketclone.admin;

import com.codegym.locketclone.admin.dto.DlqReplayResponse;
import com.codegym.locketclone.admin.dto.DlqStatsResponse;
import com.codegym.locketclone.common.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class DlqAdminService {

    private final AmqpAdmin amqpAdmin;
    private final RabbitTemplate rabbitTemplate;

    public DlqStatsResponse getDlqStats() {
        try {
            QueueInformation queueInfo = amqpAdmin.getQueueInfo(RabbitMQConfig.DEAD_LETTER_QUEUE);
            if (queueInfo == null) {
                return new DlqStatsResponse(
                        RabbitMQConfig.DEAD_LETTER_QUEUE,
                        0L,
                        0,
                        RabbitMQConfig.CHECKED_DLX_EXCHANGE,
                        "NOT_FOUND"
                );
            }
            return new DlqStatsResponse(
                    RabbitMQConfig.DEAD_LETTER_QUEUE,
                    queueInfo.getMessageCount(),
                    queueInfo.getConsumerCount(),
                    RabbitMQConfig.CHECKED_DLX_EXCHANGE,
                    "ACTIVE"
            );
        } catch (Exception ex) {
            log.warn("[DLQ-ADMIN] Không thể lấy thông tin hàng đợi DLQ: {}", ex.getMessage());
            return new DlqStatsResponse(
                    RabbitMQConfig.DEAD_LETTER_QUEUE,
                    0L,
                    0,
                    RabbitMQConfig.CHECKED_DLX_EXCHANGE,
                    "UNAVAILABLE"
            );
        }
    }

    public DlqReplayResponse replayMessages(Integer limit, String targetExchange) {
        int maxLimit = (limit != null && limit > 0) ? Math.min(limit, 1000) : 50;
        String exchange = (targetExchange != null && !targetExchange.isBlank())
                ? targetExchange
                : RabbitMQConfig.CHECKED_DIRECT_EXCHANGE;

        int replayedCount = 0;
        int failedCount = 0;
        List<String> details = new ArrayList<>();

        for (int i = 0; i < maxLimit; i++) {
            Message message;
            try {
                // Nhận từng message với timeout 1000ms
                message = rabbitTemplate.receive(RabbitMQConfig.DEAD_LETTER_QUEUE, 1000L);
            } catch (Exception ex) {
                log.error("[DLQ-ADMIN] Lỗi khi nhận message từ DLQ: {}", ex.getMessage());
                failedCount++;
                details.add("Lỗi nhận message: " + ex.getMessage());
                break;
            }

            if (message == null) {
                // Không còn message nào trong DLQ
                break;
            }

            try {
                String routingKey = resolveRoutingKey(message);
                MessageProperties properties = message.getMessageProperties();

                // Gửi lại message về exchange đích với routing key tương ứng
                rabbitTemplate.send(exchange, routingKey, message);
                replayedCount++;

                String msgId = (properties != null && properties.getMessageId() != null)
                        ? properties.getMessageId()
                        : "msg-" + (i + 1);
                details.add(String.format("Replayed msg [%s] to exchange [%s] with routingKey [%s]",
                        msgId, exchange, routingKey));
                log.info("[DLQ-ADMIN] Replayed DLQ message {} về {} với key {}", msgId, exchange, routingKey);
            } catch (Exception ex) {
                failedCount++;
                log.error("[DLQ-ADMIN] Lỗi khi replay message: {}", ex.getMessage());
                details.add("Lỗi replay message: " + ex.getMessage());
            }
        }

        long remainingCount = 0L;
        try {
            QueueInformation queueInfo = amqpAdmin.getQueueInfo(RabbitMQConfig.DEAD_LETTER_QUEUE);
            if (queueInfo != null) {
                remainingCount = queueInfo.getMessageCount();
            }
        } catch (Exception ignored) {
        }

        return new DlqReplayResponse(replayedCount, failedCount, remainingCount, details);
    }

    @SuppressWarnings("unchecked")
    private String resolveRoutingKey(Message message) {
        MessageProperties properties = message.getMessageProperties();
        if (properties == null) {
            return RabbitMQConfig.PHOTO_UPLOAD_ROUTING_KEY;
        }

        // 1. Kiểm tra header "x-death" từ RabbitMQ dead-lettering
        Object xDeath = properties.getHeader("x-death");
        if (xDeath instanceof List<?> deathList && !deathList.isEmpty()) {
            Object firstDeath = deathList.get(0);
            if (firstDeath instanceof Map<?, ?> deathMap) {
                Object routingKeys = deathMap.get("routing-keys");
                if (routingKeys instanceof List<?> rkList && !rkList.isEmpty() && rkList.get(0) != null) {
                    return rkList.get(0).toString();
                }
            }
        }

        // 2. Fallback dựa theo header __TypeId__ của Jackson JSON converter
        Object typeId = properties.getHeader("__TypeId__");
        if (typeId != null) {
            String typeStr = typeId.toString();
            if (typeStr.contains("PhotoUploadEvent")) {
                return RabbitMQConfig.PHOTO_UPLOAD_ROUTING_KEY;
            } else if (typeStr.contains("OtpEmailMessage")) {
                return RabbitMQConfig.MAIL_NOTIFICATION_ROUTING_KEY;
            } else if (typeStr.contains("FeedFanoutEvent")) {
                return RabbitMQConfig.FEED_FANOUT_ROUTING_KEY;
            }
        }

        // 3. Fallback theo receivedRoutingKey nếu có
        if (properties.getReceivedRoutingKey() != null && !properties.getReceivedRoutingKey().isBlank()) {
            return properties.getReceivedRoutingKey();
        }

        return RabbitMQConfig.PHOTO_UPLOAD_ROUTING_KEY;
    }
}
