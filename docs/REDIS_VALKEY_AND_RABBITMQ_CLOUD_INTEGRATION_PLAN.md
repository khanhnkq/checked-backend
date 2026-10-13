# Kế Hoạch Tích Hợp Redis (Valkey) & RabbitMQ Cloud Cho Backend

> **Trạng thái:** Bản thảo thiết kế kiến trúc (Architectural Plan)  
> **Dự án:** Checked Backend (`locket-clone-api`)  
> **Môi trường triển khai:** Heroku (`heroku-7usd`, Region: `us-east-1`) kết hợp Cloud Managed Services  
> **Tác giả:** Antigravity Engineering Team & Nguyễn Kim Quốc Khánh  
> **Ngày lập:** 13/10/2026  

---

## 1. Bối Cảnh & Vấn Đề Cần Giải Quyết (Problem Statement)

### 1.1. Hiện trạng kiến trúc
- **Server:** Chạy trên Heroku Eco/Basic Dyno (`us-east-1`) với giới hạn tài nguyên nghiêm ngặt: **512MB RAM**, Shared vCPU.
- **Databases:** 
  - PostgreSQL trên Neon Cloud (`ap-southeast-1` Singapore).
  - MongoDB Atlas (`ap-southeast-1` Singapore) cho Chat & WebSocket.
- **Cache nội bộ (Caffeine):** Dữ liệu cache người dùng và Bucket4j Rate Limiting được lưu trực tiếp trên RAM Heap của máy ảo JVM.
- **Tác vụ nặng chạy đồng bộ (Synchronous Blocking):** Các luồng HTTP tải ảnh, nén ảnh thumbnail (`Thumbnailator`), upload mạng S3/Garage, gửi email xác thực (`JavaMailSender`) đang chạy đồng thời trên cùng luồng xử lý web request.

### 1.2. Các nút thắt cổ chai nghiêm trọng (Bottlenecks & Root Causes)
1. **Lỗi tràn RAM & Sập Metaspace (`Error R14`, `Metaspace OOM`):**
   - Khi có nhiều request đồng thời hoặc người dùng đăng ảnh, JVM phải nạp thêm các lớp của `Thumbnailator` và `AWS SDK S3`, đồng thời lưu cache Caffeine trên Heap làm RAM vượt quá 512MB (`Process running mem=543M (104.8%)`), kích hoạt lỗi 500 sập API.
2. **Độ trễ API kéo dài (High Latency 6.000ms – 9.000ms):**
   - Người dùng đăng 1 tấm ảnh phải chờ từ 6 đến 9 giây trên màn hình thiết bị vì API phải resize 2 kích thước ảnh, upload 2 lượt HTTP lên S3, sau đó mới ghi database.
3. **Mất sạch Cache khi Heroku Restart (Cold Cache & Thundering Herd):**
   - Heroku dyno tự động khởi động lại ít nhất 1 lần mỗi ngày hoặc sau mỗi lần deploy code. Toàn bộ Caffeine Cache bị xóa sạch, khiến hàng loạt request ùa vào database Neon cùng lúc làm nghẽn HikariCP connection pool.
4. **Không hỗ trợ mở rộng đa instance (Horizontally Scaling):**
   - Caffeine không thể chia sẻ giữa nhiều dynos. Nếu scale lên `web.1` và `web.2`, trạng thái rate limit và cache người dùng sẽ bị lệch nhau hoàn toàn (Data Inconsistency).

---

## 2. Chiến Lược Lựa Chọn Nhà Cung Cấp Cloud & Vị Trí Vùng (Region Selection)

Để khắc phục triệt để vấn đề mà không làm tốn tài nguyên trên VPS hay chi phí hạ tầng, hệ thống sẽ sử dụng các dịch vụ Cloud Managed miễn phí tốt nhất hiện nay:

| Dịch vụ | Nhà cung cấp Cloud | Gói dịch vụ | Hạn mức sử dụng | Vùng (Region) bắt buộc |
| :--- | :--- | :--- | :--- | :--- |
| **Message Broker (RabbitMQ)** | **CloudAMQP** | **Little Lemur** (Free) | • 1.000.000 messages / tháng<br>• 20 kết nối đồng thời<br>• 5 Queues / Exchanges | **AWS US-East-1 (N. Virginia)** |
| **Distributed Cache (Redis/Valkey)** | **Upstash Redis** | **Free Tier** (Serverless) | • 10.000 lệnh / ngày<br>• 256MB Storage<br>• Tương thích 100% Redis RESP protocol | **AWS US-East-1 (N. Virginia)** |

> [!IMPORTANT]
> **Nguyên tắc vàng về Vị trí mạng (Network Proximity):**
> Dyno Heroku đang đặt tại vùng **US-East** (`us-east-1`). Việc bắt buộc chọn CloudAMQP và Upstash Redis cùng đặt tại **AWS US-East-1** giúp các kết nối TCP nội bộ có độ trễ cực thấp (**< 1.5ms**). Tuyệt đối **không** chọn cụm Tokyo hay Mumbai vì sẽ gây thêm độ trễ vượt biển 250ms–300ms cho mỗi lượt publish tin nhắn.

---

## 3. Kiến Trúc Hệ Thống Đích (Target Architecture)

```mermaid
flowchart TD
    Client["iOS Client (Checked App)"]
    
    subgraph HerokuDyno ["Heroku Web Dyno (Spring Boot 4)"]
        WebController["REST Controllers\n(/api/v1/photos, /chat, /auth)"]
        CacheLayer["Spring Cache Abstraction\n(Lettuce Redis Client)"]
        EventPublisher["RabbitTemplate\n(Non-blocking Publisher)"]
        RabbitConsumer["@RabbitListener Workers\n(Background Threads)"]
    end
    
    subgraph CloudInfra ["Managed Cloud Infrastructure (US-East-1)"]
        UpstashRedis[("Upstash Redis / Valkey\n• User Profiles (TTL 15m)\n• Feed Timelines (TTL 1h)\n• Distributed RateLimit")]
        CloudAMQPBroker{{"CloudAMQP RabbitMQ Broker\nExchange: checked.direct.exchange\n• photo.upload.queue\n• mail.notification.queue\n• chat.fanout.queue\n• checked.dlq"}}
    end
    
    subgraph StorageDataTier ["Database & Storage Tier"]
        S3Storage["S3 / Garage Storage\n(Object Storage CDN)"]
        NeonDB[("Neon PostgreSQL Cloud\n(Relational Data)")]
        MongoAtlas[("MongoDB Atlas Cloud\n(Chat Messages)")]
    end

    %% Flows
    Client -->|1. POST /photos < 100ms| WebController
    WebController -->|Check / Set Cache| CacheLayer
    CacheLayer <-->|Roundtrip < 1.5ms| UpstashRedis
    WebController -->|2. Push Async Task| EventPublisher
    EventPublisher -->|AMQP Publish| CloudAMQPBroker
    WebController -->|3. Trả về 202 Accepted| Client
    
    CloudAMQPBroker -->|Consume Task| RabbitConsumer
    RabbitConsumer -->|Resize & Upload S3| S3Storage
    RabbitConsumer -->|Save Photo Metadata| NeonDB
    RabbitConsumer -->|Evict / Update Feed Cache| UpstashRedis
    RabbitConsumer -->|Deliver Realtime Message| MongoAtlas
```

---

## 4. Thiết Kế Chi Tiết RabbitMQ (Message Broker)

### 4.1. Topology: Exchanges & Queues

```mermaid
flowchart LR
    Publisher["Spring Boot Web"] -->|Routing Key| Exchange["checked.direct.exchange\n(Direct Exchange)"]
    
    Exchange -->|photo.upload| QPhoto["photo.upload.queue\n(Resize & Upload S3)"]
    Exchange -->|mail.send| QMail["mail.notification.queue\n(Send OTP & Emails)"]
    Exchange -->|chat.event| QChat["chat.fanout.queue\n(Push Notifications & Badges)"]
    
    QPhoto -.->|Khi lỗi 3 lần| DLX["checked.dlx.exchange\n(Fanout DLX)"]
    DLX --> QDLQ["checked.dead-letter.queue\n(Lưu vết sự cố để replay)"]
```

### 4.2. Danh sách Queues & Thông số kỹ thuật

| Queue Name | Routing Key | Mục đích xử lý | Dead Letter Exchange | x-max-priority |
| :--- | :--- | :--- | :--- | :---: |
| `photo.upload.queue` | `photo.upload` | Nhận raw byte ảnh, xử lý cắt vuông 1:1, tạo thumbnail 160x160 qua `Thumbnailator`, upload S3, ghi DB. | `checked.dlx.exchange` | 5 |
| `mail.notification.queue` | `mail.send` | Gửi email kích hoạt tài khoản OTP, khôi phục mật khẩu (thay thế `@Async` in-memory). | `checked.dlx.exchange` | 10 |
| `chat.fanout.queue` | `chat.event` | Cập nhật số tin nhắn chưa đọc, đẩy WebSocket notification đến các thiết bị bạn bè. | `checked.dlx.exchange` | 3 |
| `checked.dead-letter.queue` | `#` | Lưu trữ toàn bộ tin nhắn thất bại sau 3 lần retry để phân tích và không làm mất dữ liệu người dùng. | N/A | N/A |

### 4.3. Định dạng Message Payload chuẩn (JSON DTO)

#### Photo Upload Event Payload:
```json
{
  "eventId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "senderId": "86faa1a3-3296-4cd1-bd85-9d4aa06b8596",
  "tempStoragePath": "temp/20261013/raw_photo_xyz.jpg",
  "caption": "Ăn trưa cùng đồng nghiệp",
  "amount": 45000.0,
  "locationName": "TP. Hồ Chí Minh",
  "latitude": 10.762622,
  "longitude": 106.660172,
  "dishName": "Cơm tấm sườn bì",
  "calories": 650,
  "mealType": "LUNCH",
  "recipientScope": "ALL_FRIENDS",
  "createdAt": "2026-10-13T11:30:00Z"
}
```

---

## 5. Thiết Kế Chi Tiết Redis / Valkey (Distributed Cache)

### 5.1. Quy ước đặt tên Key (Key Naming Schema & TTL)

| Phân vùng Cache | Key Pattern | Kiểu dữ liệu | TTL (Thời gian sống) | Mô tả công dụng |
| :--- | :--- | :--- | :--- | :--- |
| **User Profile** | `users:{userId}` | `String (JSON)` | **15 phút** | Thông tin UserPrincipal, email, avatar, quyền hạn. |
| **Feed Timeline** | `feed:{userId}` | `ZSet (Sorted Set)` | **1 giờ** | Danh sách `photoId` với `score` là Unix timestamp `createdAt`. |
| **Friendship List** | `friends:{userId}` | `Set` | **30 phút** | Danh sách UUID của các bạn bè có quan hệ `ACCEPTED`. |
| **Realtime Presence** | `presence:{userId}` | `String` | **60 giây** | Trạng thái Online / Offline kết hợp Heartbeat từ WebSocket. |
| **Rate Limiter** | `rl:{action}:{ipOrUser}` | `Hash` | **10 phút** | Lưu trữ số lượng token của thuật toán Token Bucket (Bucket4j). |

### 5.2. Tối ưu truy vấn Feed bằng `Redis Sorted Set` (Thay thế SQL Join nặng)
- **Cơ chế ghi (Write):** Khi 1 bức ảnh được publish, Worker đẩy `photoId` vào các key `feed:{friendId}` bằng lệnh:
  ```redis
  ZADD feed:usr_123 1791890000 photo_abc
  ZREMRANGEBYRANK feed:usr_123 0 -501  # Giữ tối đa 500 bài đăng mới nhất
  ```
- **Cơ chế đọc (Read):** Lấy trang đầu tiên 15 bài đăng của feed:
  ```redis
  ZREVRANGEBYSCORE feed:usr_123 +inf -inf LIMIT 0 15
  ```
  *(Thời gian thực thi chỉ mất **0.8ms**, thay thế hoàn toàn câu lệnh SQL JOIN 4 bảng phức tạp trên Postgres).*

---

## 6. Lộ Trình Triển Khai Chi Tiết (4 Phases)

### Phase 1: Bổ Sung Dependencies & Cấu Hình Môi Trường
- **Tệp tác động**:
  - `build.gradle`
  - `src/main/resources/application.yml`
  - `src/main/resources/application-prod.yml`
- **Nội dung thực hiện**:
  1. Khai báo dependencies:
     ```groovy
     implementation 'org.springframework.boot:spring-boot-starter-data-redis'
     implementation 'org.springframework.boot:spring-boot-starter-amqp'
     implementation 'com.bucket4j:bucket4j-redis:8.10.1'
     ```
  2. Bổ sung cấu hình biến môi trường an toàn:
     - `SPRING_DATA_REDIS_URL` (URL kết nối Upstash: `rediss://default:***@us1-xxx.upstash.io:6379`)
     - `SPRING_RABBITMQ_ADDRESSES` (URL kết nối CloudAMQP: `amqps://***.rmq.cloudamqp.com/***`)

---

### Phase 2: Triển Khai Valkey/Redis Caching & Distributed Rate Limit
- **Tệp tác động**:
  - `src/main/java/com/codegym/locketclone/common/config/RedisConfig.java` *(Tạo mới)*
  - `src/main/java/com/codegym/locketclone/common/config/CacheConfig.java` *(Refactor chuyển từ Caffeine sang RedisCacheManager)*
  - `src/main/java/com/codegym/locketclone/security/ratelimit/RateLimiterService.java`
- **Nội dung thực hiện**:
  1. Cấu hình `RedisTemplate<String, Object>` với `GenericJackson2JsonRedisSerializer` hỗ trợ JavaTimeModule.
  2. Định nghĩa các `RedisCacheConfiguration` riêng biệt theo TTL đã thiết kế ở Mục 5.1.
  3. Chuyển đổi `RateLimiterService` sang sử dụng Redis proxy backend, giải phóng toàn bộ RAM cache khỏi tiến trình JVM.

---

### Phase 3: Triển Khai RabbitMQ Producer & Consumer Pipeline
- **Tệp tác động**:
  - `src/main/java/com/codegym/locketclone/common/config/RabbitMQConfig.java` *(Tạo mới)*
  - `src/main/java/com/codegym/locketclone/photo/PhotoServiceImpl.java`
  - `src/main/java/com/codegym/locketclone/photo/consumer/PhotoProcessingConsumer.java` *(Tạo mới)*
  - `src/main/java/com/codegym/locketclone/notification/EmailConsumer.java` *(Tạo mới)*
- **Nội dung thực hiện**:
  1. Đăng ký Bean cho Exchange, Queues, Routing Keys và MessageConverter (JSON Jackson).
  2. Cấu hình Dead Letter Queue với 3 lần retry exponential backoff (`InitialInterval: 2s, Multiplier: 2.0, MaxInterval: 10s`).
  3. Cắt gọt hàm `uploadPhoto` trong `PhotoServiceImpl`: API chỉ lưu file thô tạm thời, bắn message `photo.upload`, và trả về `202 Accepted` ngay trong vòng **< 100ms**.
  4. Viết `PhotoProcessingConsumer` nhận message, chạy resize ảnh trong worker thread, upload S3 chính thức và kích hoạt cập nhật cache feed.

---

### Phase 4: Kiểm Thử, Benchmarking & Kế Hoạch Nghiệm Thu
- **Tệp tác động**:
  - Test suites với `Testcontainers` (Redis & RabbitMQ containers).
  - Cập nhật tài liệu kiểm thử và triển khai lên Heroku.
- **Tiêu chuẩn nghiệm thu (Acceptance Criteria)**:
  - [ ] **Thời gian phản hồi Upload Ảnh:** Giảm từ **6.794ms xuống dưới 150ms** trên client.
  - [ ] **Mức tiêu thụ RAM Heroku:** Ổn định ở mức **< 320MB / 512MB**, không còn bất kỳ log cảnh báo `Error R14` hay `Metaspace OOM`.
  - [ ] **Độ trễ truy vấn Feed:** Giảm từ **1.100ms xuống dưới 20ms** khi có cache Redis.
  - [ ] **Xử lý sự cố (Resilience):** Khi ngắt kết nối S3 giả lập, tin nhắn được chuyển vào Dead Letter Queue an toàn, không làm mất ảnh của người dùng.

---

## 7. Hướng Dẫn Thiết Lập Biến Môi Trường (Config Vars)

Sau khi tạo instance miễn phí trên CloudAMQP và Upstash Redis, cấu hình các biến môi trường sau trên Heroku:

```bash
# 1. Cấu hình Upstash Redis / Valkey
heroku config:set SPRING_DATA_REDIS_URL="rediss://default:YOUR_PASSWORD@YOUR_ENDPOINT.upstash.io:6379" --app heroku-7usd

# 2. Cấu hình CloudAMQP (RabbitMQ)
heroku config:set SPRING_RABBITMQ_ADDRESSES="amqps://YOUR_USER:YOUR_PASSWORD@YOUR_HOST.rmq.cloudamqp.com/YOUR_VHOST" --app heroku-7usd
heroku config:set SPRING_RABBITMQ_SSL_ENABLED="true" --app heroku-7usd
```

---

## 8. Kết Luận
Việc áp dụng kiến trúc **Valkey Caching + RabbitMQ Asynchronous Processing** là bước chuyển mình từ mô hình nguyên khối đơn giản (Monolithic Blocking) sang kiến trúc **Event-Driven Micro-optimized**. Hệ thống sẽ đạt được độ ổn định tối đa trên gói Heroku 512MB RAM hiện tại mà không phát sinh thêm chi phí hạ tầng.
