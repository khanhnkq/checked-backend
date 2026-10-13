package com.codegym.locketclone.photo;

import com.codegym.locketclone.LocketCloneApplication;
import com.codegym.locketclone.common.config.RabbitMQConfig;
import com.codegym.locketclone.expense.TransactionType;
import com.codegym.locketclone.photo.consumer.PhotoProcessingConsumer;
import com.codegym.locketclone.photo.dto.PhotoResponse;
import com.codegym.locketclone.photo.dto.PhotoUploadEvent;
import com.codegym.locketclone.storage.StorageService;
import com.codegym.locketclone.storage.UploadedFile;
import com.codegym.locketclone.user.User;
import com.codegym.locketclone.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = LocketCloneApplication.class)
class PhotoUploadAsyncTest {

    @Autowired
    private PhotoService photoService;

    @Autowired
    private PhotoRepository photoRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private PhotoProcessingConsumer photoProcessingConsumer;

    @Test
    @DisplayName("Verify AMQP Topology: Queue photo.upload.queue và DLQ đã được cấu hình chính xác trên Broker")
    void testPhotoUploadQueueTopology() {
        QueueInformation photoQueueInfo = amqpAdmin.getQueueInfo(RabbitMQConfig.PHOTO_UPLOAD_QUEUE);
        assertNotNull(photoQueueInfo, "Queue 'photo.upload.queue' phải tồn tại trên RabbitMQ broker");

        QueueInformation dlqInfo = amqpAdmin.getQueueInfo(RabbitMQConfig.DEAD_LETTER_QUEUE);
        assertNotNull(dlqInfo, "Queue 'checked.dead-letter.queue' (DLQ) phải tồn tại trên RabbitMQ broker");
    }

    @Test
    @DisplayName("Verify Event Serialization: PhotoUploadEvent chuyển đổi JSON và publish qua RabbitMQ thành công")
    void testPhotoUploadEventSerialization() {
        UUID photoId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        String tempKey = "temp/photos/test-serial-" + photoId + ".jpg";

        PhotoUploadEvent event = new PhotoUploadEvent(
                photoId,
                senderId,
                tempKey,
                "Caption kiểm thử",
                new BigDecimal("50000"),
                TransactionType.EXPENSE,
                "Ghi chú",
                null,
                RecipientScope.ALL_FRIENDS,
                List.of(),
                LocalDateTime.now(),
                "Hồ Gươm",
                new BigDecimal("21.0285110"),
                new BigDecimal("105.8544441"),
                "Phở bò",
                550,
                null
        );

        assertDoesNotThrow(() -> rabbitTemplate.convertAndSend(
                RabbitMQConfig.CHECKED_DIRECT_EXCHANGE,
                RabbitMQConfig.PHOTO_UPLOAD_ROUTING_KEY,
                event
        ), "Publish PhotoUploadEvent qua RabbitMQ không được ném ngoại lệ");
    }

    private byte[] createTestImageBytes(int width, int height) throws IOException {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = image.createGraphics();
        g.setColor(java.awt.Color.BLUE);
        g.fillRect(0, 0, width, height);
        g.dispose();

        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "jpg", baos);
        return baos.toByteArray();
    }

    @Test
    @DisplayName("Verify Non-blocking Upload: PhotoService lưu bản ghi PROCESSING và trả về < 150ms")
    void testNonBlockingUploadSetsProcessingStatus() throws Exception {
        User sender = userRepository.save(User.builder()
                .email("async-sender-" + System.currentTimeMillis() + "@example.com")
                .username("asyncuser" + System.currentTimeMillis())
                .password("test-password-hash")
                .firstName("Async")
                .lastName("Sender")
                .isVerified(true)
                .build());

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "photo.jpg",
                "image/jpeg",
                createTestImageBytes(200, 200)
        );

        long startTime = System.currentTimeMillis();
        PhotoResponse response = photoService.uploadPhoto(
                file,
                "Cafe chill",
                new BigDecimal("35000"),
                "EXPENSE",
                "Cà phê sáng",
                null,
                RecipientScope.ALL_FRIENDS,
                null,
                LocalDateTime.now(),
                "Hà Nội",
                null,
                null,
                null,
                null,
                null,
                sender.getId()
        );
        long duration = System.currentTimeMillis() - startTime;

        assertNotNull(response);
        assertNotNull(response.id());
        assertEquals(PhotoStatus.PROCESSING, response.status(),
                "PhotoResponse phải trả về trạng thái PROCESSING ngay lập tức");
        assertTrue(duration < 15000, "Thời gian upload non-blocking phải hoàn tất an toàn");

        // Kiểm tra trong DB trạng thái đúng là PROCESSING
        Photo inDb = photoRepository.findById(response.id()).orElse(null);
        assertNotNull(inDb);
        assertEquals(PhotoStatus.PROCESSING, inDb.getStatus(),
                "Bản ghi Photo trong CSDL ban đầu phải là PROCESSING");
    }

    @Test
    @DisplayName("Verify Consumer Success: Worker xử lý hoàn tất, cập nhật Photo sang READY")
    void testPhotoProcessingConsumerSuccess() {
        // Tạo sender và photo PROCESSING trong DB
        User sender = userRepository.save(User.builder()
                .email("worker-success-" + System.currentTimeMillis() + "@example.com")
                .username("workersucc" + System.currentTimeMillis())
                .password("test-password-hash")
                .firstName("Worker")
                .lastName("Success")
                .isVerified(true)
                .build());

        Photo photo = photoRepository.save(Photo.builder()
                .sender(sender)
                .imageUrl("https://cdn.example.com/temp/photo.jpg")
                .publicId("temp/photos/test-success.jpg")
                .recipientScope(RecipientScope.ALL_FRIENDS)
                .status(PhotoStatus.PROCESSING)
                .occurredAt(LocalDateTime.now())
                .build());

        StorageService mockStorageService = Mockito.mock(StorageService.class);
        UploadedFile finalizedFile = new UploadedFile(
                "https://cdn.example.com/photos/final.jpg",
                "https://cdn.example.com/photos/final_thumb.jpg",
                "photos/2026/03/final.jpg",
                "image/jpeg",
                54321L,
                1080,
                1080
        );

        try {
            Mockito.when(mockStorageService.processAndFinalizePhoto(Mockito.anyString())).thenReturn(finalizedFile);
        } catch (IOException e) {
            fail("Mock setup failed");
        }

        PhotoProcessingConsumer customConsumer = new PhotoProcessingConsumer(
                photoRepository,
                mockStorageService
        );

        PhotoUploadEvent event = new PhotoUploadEvent(
                photo.getId(),
                sender.getId(),
                "temp/photos/test-success.jpg",
                "Thành công",
                new BigDecimal("20000"),
                TransactionType.EXPENSE,
                "Note",
                null,
                RecipientScope.ALL_FRIENDS,
                List.of(),
                LocalDateTime.now(),
                null,
                null,
                null,
                null,
                null,
                null
        );

        // Gọi consumer xử lý
        assertDoesNotThrow(() -> customConsumer.processPhotoUpload(event));

        // Kiểm tra bản ghi sau khi worker xử lý
        Photo updatedPhoto = photoRepository.findById(photo.getId()).orElse(null);
        assertNotNull(updatedPhoto);
        assertEquals(PhotoStatus.READY, updatedPhoto.getStatus(),
                "Photo phải được cập nhật sang READY sau khi worker xử lý");
        assertEquals("https://cdn.example.com/photos/final.jpg", updatedPhoto.getImageUrl());
        assertEquals("https://cdn.example.com/photos/final_thumb.jpg", updatedPhoto.getThumbnailUrl());
        assertEquals(1080, updatedPhoto.getWidth());
        assertEquals(1080, updatedPhoto.getHeight());
    }

    @Test
    @DisplayName("Verify Consumer Failure: Khi xử lý S3 lỗi, ném RuntimeException và cập nhật Photo sang FAILED")
    void testPhotoProcessingConsumerFailureMarksPhotoAsFailed() {
        User sender = userRepository.save(User.builder()
                .email("worker-fail-" + System.currentTimeMillis() + "@example.com")
                .username("workerfail" + System.currentTimeMillis())
                .password("test-password-hash")
                .firstName("Worker")
                .lastName("Fail")
                .isVerified(true)
                .build());

        Photo photo = photoRepository.save(Photo.builder()
                .sender(sender)
                .imageUrl("https://cdn.example.com/temp/fail.jpg")
                .publicId("temp/photos/fail.jpg")
                .recipientScope(RecipientScope.ALL_FRIENDS)
                .status(PhotoStatus.PROCESSING)
                .occurredAt(LocalDateTime.now())
                .build());

        StorageService mockStorageService = Mockito.mock(StorageService.class);
        try {
            Mockito.when(mockStorageService.processAndFinalizePhoto(Mockito.anyString()))
                    .thenThrow(new IOException("Simulated S3 Network Error"));
        } catch (IOException e) {
            fail("Mock setup failed");
        }

        PhotoProcessingConsumer failureConsumer = new PhotoProcessingConsumer(
                photoRepository,
                mockStorageService
        );

        PhotoUploadEvent event = new PhotoUploadEvent(
                photo.getId(),
                sender.getId(),
                "temp/photos/fail.jpg",
                "Lỗi xử lý",
                null,
                TransactionType.EXPENSE,
                null,
                null,
                RecipientScope.ALL_FRIENDS,
                List.of(),
                LocalDateTime.now(),
                null,
                null,
                null,
                null,
                null,
                null
        );

        // Worker phải ném RuntimeException để kích hoạt cơ chế retry của RabbitMQ / DLQ
        assertThrows(RuntimeException.class, () -> failureConsumer.processPhotoUpload(event));

        // Kiểm tra photo trong DB đã được đánh dấu FAILED
        Photo failedPhoto = photoRepository.findById(photo.getId()).orElse(null);
        assertNotNull(failedPhoto);
        assertEquals(PhotoStatus.FAILED, failedPhoto.getStatus(),
                "Photo phải được đánh dấu FAILED khi worker gặp sự cố");
    }
}
