package com.codegym.locketclone.infra;

import com.codegym.locketclone.LocketCloneApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = LocketCloneApplication.class)
@TestPropertySource(properties = {
        // Cấu hình test trỏ tới Upstash Redis Cloud
        "spring.data.redis.host=adapting-moth-229902.upstash.io",
        "spring.data.redis.port=6379",
        "spring.data.redis.password=gQAAAAAAA4IOAAIgcDE3ODJkNjY1ZjRmM2U0Zjk5OTY2YTVjODg5NTAwY2Q1Zg",
        "spring.data.redis.ssl.enabled=true",
        "spring.data.redis.timeout=5000ms",

        // Cấu hình test trỏ tới CloudAMQP RabbitMQ Cloud
        "spring.rabbitmq.host=leopard.lmq.cloudamqp.com",
        "spring.rabbitmq.port=5671",
        "spring.rabbitmq.username=kufeoult",
        "spring.rabbitmq.password=mv1YvpuQOsWylToYRguVJyPtCYxdItsj",
        "spring.rabbitmq.virtual-host=kufeoult",
        "spring.rabbitmq.ssl.enabled=true"
})
class CloudConnectivityTest {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ConnectionFactory rabbitConnectionFactory;

    @Test
    @DisplayName("Verify Upstash Redis: Ghi, đọc và xóa key thành công qua kết nối TLS")
    void testUpstashRedisConnectivity() {
        String testKey = "checked:test:connectivity";
        String testValue = "UPSTASH_REDIS_CONNECTED_SUCCESSFULLY";

        // Ghi key vào Redis kèm TTL 30s
        redisTemplate.opsForValue().set(testKey, testValue, Duration.ofSeconds(30));

        // Đọc lại key
        Object retrieved = redisTemplate.opsForValue().get(testKey);
        assertNotNull(retrieved, "Giá trị đọc từ Redis không được null");
        assertEquals(testValue, retrieved.toString(), "Giá trị đọc từ Redis phải trùng khớp");

        // Dọn dẹp key sau test
        Boolean deleted = redisTemplate.delete(testKey);
        assertTrue(Boolean.TRUE.equals(deleted), "Key test phải được xóa sạch");
    }

    @Test
    @DisplayName("Verify CloudAMQP RabbitMQ: Thiết lập kết nối AMQPS thành công")
    void testCloudAmqpRabbitMQConnectivity() {
        assertNotNull(rabbitConnectionFactory, "Rabbit ConnectionFactory phải được khởi tạo");

        try (Connection connection = rabbitConnectionFactory.createConnection()) {
            assertNotNull(connection, "Kết nối tới CloudAMQP không được null");
            assertTrue(connection.isOpen(), "Kết nối tới CloudAMQP phải ở trạng thái MỞ (Open)");
        }
    }
}
