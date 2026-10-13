# Kế Hoạch Chi Tiết PR #7: Non-blocking Photo Upload & PhotoProcessingConsumer (Resize 1:1, Thumb, S3 Worker)

> **Tài liệu tham chiếu:**
> - [REDIS_VALKEY_AND_RABBITMQ_CLOUD_INTEGRATION_PLAN.md](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/docs/REDIS_VALKEY_AND_RABBITMQ_CLOUD_INTEGRATION_PLAN.md)
> - [REDIS_RABBITMQ_PR_ROADMAP.md](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/docs/REDIS_RABBITMQ_PR_ROADMAP.md)
> - [REDIS_RABBITMQ_PR_CHECKLIST.md](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/docs/REDIS_RABBITMQ_PR_CHECKLIST.md)
> **Mức độ ưu tiên:** 🔴 **P0 (Critical - Khắc phục triệt để lỗi sập RAM Heroku 512MB & Error R14)**  
> **Nhánh Git dự kiến:** `feat/amqp-photo-processing-worker`  
> **Mục tiêu cốt lõi:**
> 1. Chuyển đổi API đăng ảnh `POST /api/v1/photos` từ xử lý đồng bộ blocking (3.000ms - 7.000ms) sang mô hình **Bất đồng bộ Non-blocking Event-Driven** phản hồi ngay trong **< 150ms** với trạng thái `PhotoStatus.PROCESSING`.
> 2. Đưa toàn bộ tác vụ nặng (giải mã ảnh kích thước lớn, center-crop 1:1 vuông, nén thumbnail 320x320/160x160 qua Thumbnailator, và tải song song lên Garage S3) vào Worker nền `PhotoProcessingConsumer` được giới hạn concurrency nghiêm ngặt (`concurrency = 1`), cách ly hoàn toàn rủi ro tràn RAM (OOM) khỏi luồng xử lý Web HTTP của Heroku.
> 3. Tích hợp Dead Letter Queue (`checked.dead-letter.queue`) tự động bảo vệ dữ liệu khi xử lý ảnh thất bại.

---

## 1. Bối Cảnh & Vấn Đề Cần Giải Quyết

### 1.1. Hiện trạng gây lỗi trên Heroku Dyno 512MB
- Khi người dùng gửi ảnh từ ứng dụng iOS:
  1. Luồng HTTP Tomcat nhận file `MultipartFile` dung lượng từ 3MB - 10MB.
  2. Phương thức `imageProcessingService.processPhoto(file)` tiến hành giải mã dữ liệu nhị phân thành `BufferedImage` trong bộ nhớ Heap. Đối với ảnh camera điện thoại hiện đại (độ phân giải 12MP - 48MP), một đối tượng raster bitmap không nén có thể chiếm tới **150MB - 300MB RAM** trong tích tắc.
  3. Quá trình xử lý: Center-crop tỷ lệ 1:1, nén ảnh chính (1080x1080, quality 0.85) và nén thumbnail (320x320, quality 0.80).
  4. Quá trình tải mạng: Upload 2 file lên Garage S3 qua mạng VPS.
  5. Quá trình cơ sở dữ liệu: Ghi bản ghi vào PostgreSQL, ghi nhật ký calo, xóa cache.
- **Hậu quả nghiêm trọng**:
  - Toàn bộ chuỗi thao tác này diễn ra trên **luồng HTTP đồng bộ**, giữ kết nối Tomcat từ **3 đến 7 giây**.
  - Nếu chỉ cần 2 hoặc 3 người dùng tải ảnh đồng thời, lượng RAM sử dụng của JVM sẽ vượt mốc 512MB -> Heroku kích hoạt cảnh báo **Error R14 (Memory quota exceeded)** và tiến hành kill dyno với **Error R15 (Memory quota vastly exceeded)**.
  - Người dùng di động phải nhìn màn hình loading xoay tròn nhiều giây, trải nghiệm thiếu mượt mà.

### 1.2. Giải pháp kiến trúc Non-blocking Event-Driven
```
[iOS Client]
     │
     ▼ (1) POST /api/v1/photos (Multipart)
[PhotoController] ── (2) Stream file thô vào S3 tempKey ──► [Garage S3: temp/photos/...]
     │                                                             ▲
     ├── (3) Lưu Photo (status: PROCESSING) ──► [PostgreSQL]       │
     ├── (4) Publish PhotoUploadEvent ──► [RabbitMQ Exchange]      │
     │                                           │                 │
     ▼ (5) Return PhotoResponse (< 150ms)        │ (Routing)       │
[iOS Client Nhận 200/202]                        ▼                 │
                                    [Queue: photo.upload.queue]    │
                                                 │                 │
                                                 ▼ (Consume)       │
                                     [PhotoProcessingConsumer] ────┘
                                                 │ (Download raw stream)
                                                 ├──► Crop 1:1 (1080x1080)
                                                 ├──► Nén Thumbnail (320x320)
                                                 ├──► Upload S3 permanent keys
                                                 ├──► Xóa file tạm tempKey
                                                 ├──► Cập nhật Photo -> READY
                                                 ├──► Kích hoạt CalorieLog
                                                 └──► Invalidate Cache Redis
```

---

## 2. Chi Tiết Thay Đổi Kỹ Thuật

### 2.1. Cập nhật Enum `PhotoStatus.java`
Bổ sung trạng thái `PROCESSING`:
```java
public enum PhotoStatus {
    PROCESSING, // Ảnh vừa upload, đang trong hàng đợi xử lý nền
    READY,      // Ảnh đã crop 1:1, nén thumb, upload S3 thành công
    FAILED,     // Xử lý nền gặp sự cố sau khi đã retry hết số lần
    DELETED     // Người dùng đã xóa ảnh (soft delete)
}
```
*Ghi chú CSDL*: Cột `status` trong bảng `photos` đã là kiểu `VARCHAR(20)` (từ migration `V1__init_database.sql`), không cần can thiệp script DDL.

### 2.2. DTO Sự Kiện `PhotoUploadEvent.java`
Tạo mới DTO ghi nhận đầy đủ ngữ cảnh để worker thực hiện độc lập:
```java
package com.codegym.locketclone.photo.dto;

import com.codegym.locketclone.calorie.MealType;
import com.codegym.locketclone.expense.TransactionType;
import com.codegym.locketclone.photo.RecipientScope;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record PhotoUploadEvent(
        UUID photoId,
        UUID senderId,
        String tempKey,
        String caption,
        BigDecimal amount,
        TransactionType transactionType,
        String note,
        UUID categoryId,
        RecipientScope recipientScope,
        List<UUID> recipientIds,
        LocalDateTime takenAt,
        String locationName,
        BigDecimal latitude,
        BigDecimal longitude,
        String dishName,
        Integer calories,
        MealType mealType
) implements Serializable {}
```

### 2.3. Mở rộng `StorageService` & `GarageS3StorageService`
1. **Trong `StorageService.java`**:
   - Bổ sung `UploadedFile uploadTempPhoto(MultipartFile file) throws IOException;`
   - Bổ sung `UploadedFile processAndFinalizePhoto(String tempKey) throws IOException;`
2. **Trong `GarageS3StorageService.java`**:
   - `uploadTempPhoto`: Tải trực tiếp luồng byte nhị phân của file vào S3 với key `temp/photos/{uuid}.{ext}` thông qua `RequestBody.fromInputStream(file.getInputStream(), file.getSize())`. **Hoàn toàn không gọi Thumbnailator hay giải nén ảnh tại luồng này**, thời gian thực thi chỉ mất ~50ms - 80ms và sử dụng < 2MB RAM (streaming).
   - `processAndFinalizePhoto`:
     - Tải stream từ `tempKey` qua `s3Client.getObject(...)`.
     - Chạy `imageProcessingService.processPhoto(rawBytes)`.
     - Upload song song 2 đối tượng chính thức `photos/{yyyy/MM}/{uuid}.jpg` và `photos/{yyyy/MM}/{uuid}_thumb.jpg`.
     - Xóa `tempKey` khỏi S3.
     - Trả về đối tượng `UploadedFile` hoàn chỉnh.

### 2.4. Mở rộng `ImageProcessingService.java`
- Bổ sung overload `public ProcessedImage processPhoto(byte[] imageBytes) throws IOException` nhận trực tiếp mảng byte nhị phân để Worker có thể xử lý file tải về từ S3 mà không phụ thuộc vào `MultipartFile`.

### 2.5. Tái cấu trúc `PhotoServiceImpl.java`
- Bổ sung phụ thuộc `RabbitTemplate`:
  ```java
  @Autowired(required = false)
  private RabbitTemplate rabbitTemplate;
  ```
- Tái cấu trúc hàm `uploadPhoto`:
  1. Kiểm tra tính hợp lệ cơ bản của file (đuôi ảnh, dung lượng).
  2. Tải nhanh vào khu vực tạm qua `storageService.uploadTempPhoto(file)`.
  3. Tạo bản ghi `Photo` với `status = PhotoStatus.PROCESSING`, gán tạm thời `imageUrl` trỏ tới URL tạm để tránh lỗi client nil, gán `publicId = tempKey`.
  4. Lưu `Photo` và danh sách người nhận vào CSDL.
  5. Nếu `rabbitTemplate != null`: Bắn `PhotoUploadEvent` vào `checked.direct.exchange` với routing key `photo.upload`.
  6. *(Cơ chế tương thích ngược)*: Nếu `rabbitTemplate == null` (như trong các unit test cũ chạy ngoài Spring context): Gọi xử lý trực tiếp `processAndFinalizePhoto` đồng bộ.
  7. Trả về `PhotoResponse` cho client trong thời gian **< 150ms**.

### 2.6. Tạo mới Worker `PhotoProcessingConsumer.java`
Đặt tại `com.codegym.locketclone.photo.consumer.PhotoProcessingConsumer`:
```java
@Component
@Slf4j
@RequiredArgsConstructor
public class PhotoProcessingConsumer {

    private final PhotoRepository photoRepository;
    private final StorageService storageService;
    private final CalorieService calorieService;
    private final ExpenseService expenseService;
    private final CacheManager cacheManager;

    @RabbitListener(
            queues = RabbitMQConfig.PHOTO_UPLOAD_QUEUE,
            concurrency = "${rabbitmq.worker.photo-concurrency:1}"
    )
    public void processPhotoUpload(PhotoUploadEvent event) {
        // 1. Tải và xử lý ảnh (Crop 1:1, nén thumbnail, upload S3 chính thức, xóa file tạm)
        // 2. Cập nhật bản ghi Photo: status = READY, imageUrl, thumbnailUrl, width, height, fileSize
        // 3. Tự động ghi CalorieLog (nếu có dishName/calories)
        // 4. Invalidate Cache: my_photos, feed, expense_summary, calorie_daily
        // 5. Nếu thất bại sau retry -> Đẩy vào Dead Letter Queue và cập nhật status = FAILED
    }
}
```
- **Kiểm soát dung lượng RAM**: Thiết lập `concurrency = 1` đảm bảo tại một thời điểm dyno chỉ xử lý nén 1 ảnh, không bao giờ xảy ra tình trạng nhiều tiến trình nén tranh chấp RAM.

---

## 3. Quản Lý Lỗi & Dead Letter Queue (DLQ)

1. **Cấu hình Retry**:
   - Sử dụng cấu hình có sẵn trong `application.yml`:
     - Số lần thử lại tối đa: `max-attempts: 3`
     - Khoảng thời gian ban đầu: `initial-interval: 1000ms`
     - Hệ số nhân Exponential Backoff: `multiplier: 2.0`
     - `default-requeue-rejected: false`
2. **Định tuyến DLQ**:
   - Hàng đợi `photo.upload.queue` đã được gắn sẵn đối số `x-dead-letter-exchange: checked.dlx.exchange`.
   - Khi thông điệp bị từ chối sau 3 lần retry thất bại, RabbitMQ tự động đẩy thông điệp vào `checked.dead-letter.queue`.
3. **Cập nhật trạng thái CSDL**:
   - Worker bắt lỗi ngoại lệ cuối cùng để cập nhật bản ghi `Photo` thành `PhotoStatus.FAILED`, tránh việc bản ghi bị treo vĩnh viễn ở trạng thái `PROCESSING`.

---

## 4. Kế Hoạch Kiểm Thử (Testing Strategy)

### 4.1. Nguyên tắc cốt lõi: ZERO HARDCODING
- **Tuyệt đối không hardcode credentials** (mật khẩu, URL, access key, secret key) trong bất kỳ file code, test hay cấu hình commit nào.
- Toàn bộ thông tin nhạy cảm được nạp động từ file `.env` qua Gradle test runner và các biến môi trường `${...}`.

### 4.2. Test Suite Chi Tiết
1. **Unit Test & Service Test (`PhotoServiceImplTest.java`)**:
   - Đảm bảo toàn bộ các ca kiểm thử upload hiện có tiếp tục PASS 100% (nhờ cơ chế fallback linh hoạt khi `rabbitTemplate` null/mocked).
2. **Tích hợp Bất Đồng Bộ Mới (`PhotoUploadAsyncTest.java`)**:
   - **`uploadPhoto_nonBlocking_returnsProcessingImmediately`**:
     - Upload ảnh qua `PhotoService`.
     - Xác nhận thời gian phản hồi cực nhanh.
     - Xác nhận bản ghi Photo được lưu với `PhotoStatus.PROCESSING`.
     - Xác minh `rabbitTemplate.convertAndSend` được gọi với Exchange `checked.direct.exchange`, Routing Key `photo.upload`, và payload `PhotoUploadEvent`.
   - **`consumer_processPhotoUpload_updatesPhotoToReadyAndEvictsCache`**:
     - Giả lập thông điệp `PhotoUploadEvent`.
     - Worker gọi `storageService.processAndFinalizePhoto`.
     - Xác nhận `Photo` chuyển trạng thái sang `READY` kèm `imageUrl` và `thumbnailUrl` chính thức.
     - Xác nhận cache `my_photos` và `feed` được dọn dẹp sạch sẽ.
   - **`consumer_processingFailure_marksPhotoAsFailed`**:
     - Giả lập lỗi S3 hoặc lỗi xử lý ảnh.
     - Xác nhận ngoại lệ được ném ra để kích hoạt cơ chế retry của RabbitMQ và cập nhật trạng thái `PhotoStatus.FAILED`.
3. **Chạy kiểm thử toàn bộ dự án**:
   - Chạy `./gradlew test` đảm bảo 100% (225+ tests) PASS hoàn hảo trước khi tạo nhánh hoặc commit.

---

## 5. Kế Hoạch Triển Khai (Step-by-Step)

| Bước | Hành Động | Tệp Tin Tác Động |
| :--- | :--- | :--- |
| **Bước 1** | Tạo nhánh Git làm việc mới | `feat/amqp-photo-processing-worker` |
| **Bước 2** | Thêm enum `PhotoStatus.PROCESSING` | `src/main/java/com/codegym/locketclone/photo/PhotoStatus.java` |
| **Bước 3** | Tạo DTO sự kiện `PhotoUploadEvent` | `src/main/java/com/codegym/locketclone/photo/dto/PhotoUploadEvent.java` |
| **Bước 4** | Mở rộng xử lý mảng byte trong ImageProcessingService | `src/main/java/com/codegym/locketclone/storage/image/ImageProcessingService.java` |
| **Bước 5** | Thêm hàm `uploadTempPhoto` & `processAndFinalizePhoto` | `src/main/java/com/codegym/locketclone/storage/StorageService.java`<br>`src/main/java/com/codegym/locketclone/storage/s3/GarageS3StorageService.java` |
| **Bước 6** | Tái cấu trúc `PhotoServiceImpl.java` sang luồng Non-blocking | `src/main/java/com/codegym/locketclone/photo/PhotoServiceImpl.java` |
| **Bước 7** | Tạo mới Worker `PhotoProcessingConsumer.java` | `src/main/java/com/codegym/locketclone/photo/consumer/PhotoProcessingConsumer.java` |
| **Bước 8** | Viết Integration Test `PhotoUploadAsyncTest.java` | `src/test/java/com/codegym/locketclone/photo/PhotoUploadAsyncTest.java` |
| **Bước 9** | Chạy toàn bộ test suite `./gradlew test` (225+ tests) | Toàn bộ project |
| **Bước 10** | Commit, đẩy nhánh, merge `main`, deploy Heroku (Release `v37`) | Git / Heroku CLI |

---

## 6. Kế Hoạch Rollback & Giám Sát

1. **Giám Sát Vận Hành (Logging & Metrics)**:
   - Thêm tiền tố log chuẩn mực: `[PHOTO-UPLOAD-ASYNC]` tại Controller/Service và `[PHOTO-WORKER]` tại Consumer.
   - Ghi nhận thời gian hoàn tất từng khâu (thời gian lưu temp, thời gian nén ảnh, thời gian đẩy S3).
2. **Kế Hoạch Rollback**:
   - Nếu RabbitMQ gặp sự cố kết nối đột xuất, hệ thống có thể bật cờ cấu hình fallback đồng bộ mà không cần dừng dịch vụ.
   - Trường hợp cần hoàn tác hoàn toàn: Revert commit trên nhánh `main`, đẩy lên remote và kích hoạt Heroku build lại phiên bản `v36`.
