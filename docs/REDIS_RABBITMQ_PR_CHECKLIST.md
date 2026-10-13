# Checklist Danh Sách Pull Requests: Tích Hợp Redis (Valkey) & RabbitMQ Cloud

> **Tài liệu tham chiếu:**  
> - [REDIS_VALKEY_AND_RABBITMQ_CLOUD_INTEGRATION_PLAN.md](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/docs/REDIS_VALKEY_AND_RABBITMQ_CLOUD_INTEGRATION_PLAN.md) (Kiến trúc tổng thể)  
> - [REDIS_RABBITMQ_PR_ROADMAP.md](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/docs/REDIS_RABBITMQ_PR_ROADMAP.md) (Lộ trình PR & Ma trận phụ thuộc)  
> **Dự án:** Checked Backend (`locket-clone-api`)  
> **Môi trường triển khai:** Heroku (`heroku-7usd`, 512MB RAM, `us-east-1`)  
> **Ngày tạo:** 13/10/2026  

---

## 📈 Bảng Điều Khiển Tiến Độ (Progress Dashboard)

- [x] **Phase 1: Dependencies, Cấu hình Môi trường & Kết nối Cloud TLS** *(Hoàn thành: Commit `59a0c19`, Release v29 UP)*
- [ ] **Phase 2: Triển Khai Full-Stack Redis Caching Suite (5 Phân Vùng)**
  - [x] **PR #1:** Core RedisCacheManager đa TTL, Jackson Serializer & Graceful Degradation *(Hoàn thành: Commits `bfb03cd`, `427c68f`, Heroku Release v31 UP)*
  - [x] **PR #2:** Phân vùng 1: Cache User Identity & Danh sách Bạn bè *(Hoàn thành: Commit `5ebeb79`, Heroku Release v32 UP)*
  - [ ] **PR #3:** Phân vùng 2: Cache Reactions Summary, Feed & Kho ảnh MyPhotos
  - [ ] **PR #4:** Phân vùng 3 & 4: Cache Dashboard Chi Tiêu & Nhật Ký Calo
  - [ ] **PR #5:** Phân vùng 5: Cache Badge Tin Nhắn Chưa Đọc (Chat Unread)
- [ ] **Phase 3: RabbitMQ Event Producer & Consumer Pipeline**
  - [ ] **PR #6:** Worker Gửi Email OTP Bất Đồng Bộ & Xử Lý Thất Bại (DLQ)
  - [ ] **PR #7:** Non-blocking Photo Upload & PhotoProcessingConsumer (Resize 1:1, Thumb, S3)
  - [ ] **PR #8:** Feed Fan-out Cache Warmer & Quản Trị DLQ Replay Admin API
- [ ] **Phase 4: Benchmarking, Giám Sát RAM & Nghiệm Thu**
  - [ ] **PR #9:** Load Testing, Đo RAM Heroku Dyno < 300MB & Nghiệm Thu Toàn Diện

---

## 🛠️ Danh Sách Chi Tiết Từng Pull Request Cần Thực Hiện

---

### PR #1: Core RedisCacheManager Đa TTL, Jackson Serializer & Graceful Degradation
- **Nhánh Git:** `feat/cache-infra-redis-manager-and-fallback`
- **Mức độ:** 🟢 **ĐÃ HOÀN THÀNH & LIVE TRÊN HEROKU** (Release `v31`)
- **Commit đề xuất:** `feat(cache): implement multi-ttl redis cache manager with graceful degradation` (`bfb03cd`)
- **Tối ưu Procfile:** `perf(jvm): optimize memory limits for 512mb heroku dyno with serial gc` (`427c68f`)
- **Danh sách công việc:**
  - [x] Bổ sung `@JsonCreator` và `@JsonProperty` cho [UserPrincipal.java](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/java/com/codegym/locketclone/security/service/UserPrincipal.java) để tương thích Jackson Redis Serializer.
  - [x] Tạo mới [CustomCacheErrorHandler.java](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/java/com/codegym/locketclone/common/config/CustomCacheErrorHandler.java) kế thừa `CacheErrorHandler`:
    - [x] `handleCacheGetError`: Ghi log warn, fallback đọc DB trực tiếp, không làm sập API.
    - [x] `handleCachePutError`: Ghi log warn, bỏ qua lỗi ghi cache.
    - [x] `handleCacheEvictError`: Ghi log warn, tiếp tục luồng xử lý.
    - [x] `handleCacheClearError`: Ghi log warn, tiếp tục luồng xử lý.
  - [x] Tái cấu trúc [CacheConfig.java](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/java/com/codegym/locketclone/common/config/CacheConfig.java):
    - [x] Thay thế `CaffeineCacheManager` bằng Bean `RedisCacheManager`.
    - [x] Cấu hình `RedisCacheConfiguration` mặc định kèm `GenericJackson2JsonRedisSerializer`.
    - [x] Thiết lập 5 nhóm thời gian sống (TTL):
      - `30m`: `users`, `expense_summary`, `expense_cashflow`, `expense_categories`, `expense_budgets`, `calorie_daily`, `calorie_summary`, `photo_detail`.
      - `20m`: `friends`.
      - `15m`: `reactions_summary`, `myphotos`.
      - `10m`: `feed`, `chat_unread`, `chat_conversations`.
      - `1h`: `calorie_goal`.
    - [x] Đăng ký `errorHandler(new CustomCacheErrorHandler())`.
  - [x] Viết integration test [RedisCacheManagerTest.java](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/test/java/com/codegym/locketclone/infra/RedisCacheManagerTest.java):
    - [x] Test ghi/đọc đối tượng `UserPrincipal` từ cache Redis.
    - [x] Test fallback tự động khi mock Redis ném exception.
  - [x] Chạy `./gradlew test` (202/202 tests pass 100%).
  - [x] Commit push và deploy lên Heroku để test thực tế:
    - [x] Buildpack Gradle compile & build thành công.
    - [x] Release v31 khởi động trong 22.8 giây, Dyno UP.
    - [x] Kiểm thử trực tiếp `GET /v3/api-docs` (HTTP 200 OK, latency ~1.6s).
    - [x] Kiểm thử trực tiếp `POST /api/v1/auth/login` (HTTP 400 & 401 xử lý chuẩn xác, latency 360ms - 958ms).
    - [x] Tối ưu SerialGC (`-XX:+UseSerialGC -Xmx180m -Xms96m`) giúp Dyno chạy mượt mà, không gặp lỗi R14.

---

### PR #2: Phân Vùng 1 - Cache User Identity & Danh Sách Bạn Bè
- **Nhánh Git:** `feat/cache-partition-user-and-friendship`
- **Mức độ:** 🟢 **ĐÃ HOÀN THÀNH & LIVE TRÊN HEROKU** (Release `v32`)
- **Commit:** `feat(cache): integrate redis caching for user identity and friendship queries` (`5ebeb79`)
- **Kế hoạch chi tiết:** [PR2_CACHE_USER_AND_FRIENDSHIP.md](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/docs/PR2_CACHE_USER_AND_FRIENDSHIP.md)
- **Danh sách công việc:**
  - [x] Cập nhật [CacheConfig.java](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/java/com/codegym/locketclone/common/config/CacheConfig.java):
    - [x] Nâng cấp `redisObjectMapper` sang `DefaultTyping.EVERYTHING` và `JsonTypeInfo.As.WRAPPER_ARRAY` để hỗ trợ deserialization an toàn cả `UserPrincipal` lẫn root collection Java Records `List<FriendProfileResponse>`.
  - [x] Cập nhật [UserDetailsServiceImpl.java](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/java/com/codegym/locketclone/security/service/UserDetailsServiceImpl.java):
    - [x] Chuẩn hóa `@Cacheable(value = CacheConfig.USERS_CACHE, key = "#id")` hoạt động trơn tru với Redis serialization.
  - [x] Cập nhật [UserServiceImpl.java](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/java/com/codegym/locketclone/user/UserServiceImpl.java):
    - [x] Bổ sung `@CacheEvict(value = CacheConfig.USERS_CACHE, key = "#userId")` khi cập nhật profile / avatar / personal info.
  - [x] Cập nhật [FriendshipService.java](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/java/com/codegym/locketclone/friendship/FriendshipService.java) & [FriendshipServiceImpl.java](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/java/com/codegym/locketclone/friendship/FriendshipServiceImpl.java):
    - [x] Thêm `@Cacheable(value = CacheConfig.FRIENDS_CACHE, key = "#userId")` vào method `getAllFriends(UUID userId)` (TTL: 20 phút).
  - [x] Cập nhật [FriendInviteLinkServiceImpl.java](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/java/com/codegym/locketclone/friendship/invite/FriendInviteLinkServiceImpl.java):
    - [x] Thêm evict 2 chiều xóa cache `friends::{ownerUserId}` và `friends::{currentUserId}` khi chấp nhận lời mời kết bạn `acceptByToken`.
  - [x] Viết integration test [UserAndFriendshipCacheTest.java](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/test/java/com/codegym/locketclone/infra/UserAndFriendshipCacheTest.java):
    - [x] Xác thực `loadUserById` nạp cache và hit cache lần 2.
    - [x] Xác thực `updateCurrentUserProfile` evict cache `users::{userId}`.
    - [x] Xác thực `getAllFriends` nạp cache và hit cache `friends::{userId}`.
    - [x] Xác thực `acceptByToken` evict cache bạn bè 2 chiều cho cả 2 user.
  - [x] Chạy `./gradlew test` (toàn bộ 207 tests PASS 100%).
  - [x] Merge vào `main`, push GitHub & deploy thành công lên Heroku (Release `v32`).
  - [x] Test live Heroku endpoint `GET /api/v1/friendships` phản hồi chuẩn xác.

---

### PR #3: Phân Vùng 2 - Cache Reactions Summary, Feed & Kho Ảnh MyPhotos
- **Nhánh Git:** `feat/cache-partition-photo-and-reactions`
- **Mức độ:** 🔴 **P0 (Critical - Điểm nóng hiệu năng lớn nhất)**
- **Commit đề xuất:** `feat(cache): cache photo reactions summary, feed, and myphotos with smart eviction`
- **Danh sách công việc:**
  - [ ] Tái cấu trúc logic phản hồi cảm xúc trong [PhotoServiceImpl.java](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/java/com/codegym/locketclone/photo/PhotoServiceImpl.java):
    - [ ] Tách phần thống kê chung (tổng số count, count theo loại emoji, 5 người thả tim gần nhất) ra method riêng để cache: `@Cacheable(value = "reactions_summary", key = "#photoId")`.
    - [ ] Kết hợp với `myReaction` của người dùng hiện tại trong tầng service trước khi trả về [PhotoReactionSummaryResponse](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/java/com/codegym/locketclone/photo/dto/PhotoReactionSummaryResponse.java).
    - [ ] Thêm `@CacheEvict(value = "reactions_summary", key = "#photoId")` khi `reactToPhoto` hoặc `removeReaction`.
  - [ ] Tích hợp cache Feed & Kho ảnh trong [PhotoServiceImpl.java](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/java/com/codegym/locketclone/photo/PhotoServiceImpl.java):
    - [ ] Cache trang đầu feed của người dùng: `@Cacheable(value = "feed", key = "#userId", condition = "#pageable.pageNumber == 0 && #friendId == null")`.
    - [ ] Cache trang đầu ảnh cá nhân: `@Cacheable(value = "myphotos", key = "#userId", condition = "#pageable.pageNumber == 0")`.
    - [ ] Cache chi tiết ảnh: `@Cacheable(value = "photo_detail", key = "#photoId")`.
  - [ ] Xử lý eviction khi người dùng đăng ảnh mới hoặc xóa ảnh:
    - [ ] Evict `myphotos::{userId}` của người đăng.
    - [ ] Evict `photo_detail::{photoId}` khi cập nhật hoặc xóa ảnh.
  - [ ] Viết integration test đo lường:
    - [ ] Lần gọi thứ 2 của `getReactionSummary` trả về từ cache trong < 2ms.
    - [ ] Sau khi thả tim mới, cache tự xóa và tính toán lại chính xác.
  - [ ] Chạy `./gradlew test` và tạo PR.

---

### PR #4: Phân Vùng 3 & 4 - Cache Dashboard Chi Tiêu & Nhật Ký Calo
- **Nhánh Git:** `feat/cache-partition-expense-and-calorie`
- **Mức độ:** 🟡 **P1 (High - Giải phóng Neon DB khỏi các lệnh aggregate)**
- **Commit đề xuất:** `feat(cache): cache monthly expense dashboards and daily calorie logs`
- **Danh sách công việc:**
  - [ ] Cập nhật [ExpenseServiceImpl.java](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/java/com/codegym/locketclone/expense/ExpenseServiceImpl.java):
    - [ ] `@Cacheable(value = "expense_summary", key = "#userId + ':' + #monthKey")` cho `getExpenseSummary`.
    - [ ] `@Cacheable(value = "expense_cashflow", key = "#userId + ':' + #year")` cho `getYearlyCashflowSummary`.
    - [ ] `@Cacheable(value = "expense_categories", key = "#userId + ':' + #monthKey + ':' + #limit")` cho `getTopSpendingCategories`.
    - [ ] `@Cacheable(value = "expense_budgets", key = "#userId + ':' + #monthKey")` cho `getBudget`.
    - [ ] Bổ sung `@Caching(evict = ...)` khi thêm, sửa, xóa khoản chi tiêu hoặc cập nhật ngân sách.
  - [ ] Cập nhật [CalorieServiceImpl.java](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/java/com/codegym/locketclone/calorie/CalorieServiceImpl.java):
    - [ ] `@Cacheable(value = "calorie_daily", key = "#userId + ':' + (#date != null ? #date.toString() : T(java.time.LocalDate).now().toString())")` cho `getDailySummary`.
    - [ ] `@Cacheable(value = "calorie_summary", key = "#userId + ':' + #monthKey")` cho `getMonthlySummary`.
    - [ ] `@Cacheable(value = "calorie_goal", key = "#userId")` cho `getGoal`.
    - [ ] Bổ sung `@CacheEvict` khi ghi log bữa ăn thủ công, log từ ảnh, xóa log, hoặc sửa mục tiêu calo.
  - [ ] Viết test tự động xác nhận cache hit và eviction cho Expense và Calorie.
  - [ ] Chạy `./gradlew test` và tạo PR.

---

### PR #5: Phân Vùng 5 - Cache Badge Tin Nhắn Chưa Đọc (Chat Unread)
- **Nhánh Git:** `feat/cache-partition-chat-badges`
- **Mức độ:** 🟢 **P2 (Medium)**
- **Commit đề xuất:** `feat(cache): cache realtime chat unread badges and conversation previews`
- **Danh sách công việc:**
  - [ ] Cập nhật [ChatServiceImpl.java](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/java/com/codegym/locketclone/chat/service/impl/ChatServiceImpl.java):
    - [ ] `@Cacheable(value = "chat_unread", key = "#currentUserId")` cho `getTotalUnreadCount`.
    - [ ] `@Cacheable(value = "chat_conversations", key = "#currentUserId")` cho `getMyConversations`.
    - [ ] Bổ sung `@CacheEvict(value = {"chat_unread", "chat_conversations"}, key = "#recipientId")` khi có tin nhắn mới gửi đến.
    - [ ] Bổ sung `@CacheEvict(value = {"chat_unread", "chat_conversations"}, key = "#currentUserId")` khi đọc tin nhắn (`markConversationAsRead`).
  - [ ] Viết unit test xác nhận tốc độ truy vấn Badge iOS không còn quét toàn bộ MongoDB.
  - [ ] Chạy `./gradlew test` và tạo PR.

---

### PR #6: Worker Gửi Email OTP Bất Đồng Bộ & Xử Lý Thất Bại (DLQ)
- **Nhánh Git:** `feat/amqp-mail-notification-worker`
- **Mức độ:** 🟡 **P1 (High)**
- **Commit đề xuất:** `feat(amqp): decouple otp email delivery into asynchronous rabbitmq consumer`
- **Danh sách công việc:**
  - [ ] Tạo DTO sự kiện: `MailNotificationEvent` (toEmail, recipientName, otpCode, template, timestamp).
  - [ ] Chuyển đổi [SmtpEmailService.java](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/java/com/codegym/locketclone/notification/SmtpEmailService.java):
    - [ ] Thay vì gọi trực tiếp `JavaMailSender.send()` trên luồng web, chuyển sang gửi event qua `RabbitTemplate.convertAndSend(CHECKED_DIRECT_EXCHANGE, MAIL_NOTIFICATION_ROUTING_KEY, event)`.
  - [ ] Tạo mới `MailNotificationConsumer.java`:
    - [ ] Gắn `@RabbitListener(queues = RabbitMQConfig.MAIL_NOTIFICATION_QUEUE)`.
    - [ ] Gọi `JavaMailSender` thực hiện gửi email thật trong background.
    - [ ] Cấu hình ném exception để RabbitMQ kích hoạt Retry 3 lần (Initial: 2s, Multiplier: 2.0, Max: 10s).
    - [ ] Nếu thất bại quá 3 lần -> Tự động chuyển message vào `checked.dead-letter.queue`.
  - [ ] Viết integration test `MailAmqpIntegrationTest.java`:
    - [ ] Xác nhận gửi mail không còn block luồng HTTP.
    - [ ] Xác nhận định tuyến thành công sang DLQ khi cố tình ném lỗi gửi mail.
  - [ ] Chạy `./gradlew test` và tạo PR.

---

### PR #7: Non-blocking Photo Upload & PhotoProcessingConsumer (Resize 1:1, Thumb, S3)
- **Nhánh Git:** `feat/amqp-photo-processing-worker`
- **Mức độ:** 🔴 **P0 (Critical - Giải quyết triệt để lỗi sập RAM Heroku 512MB & Error R14)**
- **Commit đề xuất:** `feat(amqp): implement non-blocking photo upload and async processing worker`
- **Danh sách công việc:**
  - [ ] Tạo DTO sự kiện: `PhotoUploadEvent` (photoId, senderId, tempStorageKey, caption, amount, transactionType, location, calories, mealType, recipientScope, createdAt).
  - [ ] Tái cấu trúc `uploadPhoto` trong [PhotoServiceImpl.java](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/java/com/codegym/locketclone/photo/PhotoServiceImpl.java):
    - [ ] Tải file thô lên khu vực tạm (temp bucket/path) với tốc độ nhanh nhất.
    - [ ] Tạo bản ghi `Photo` trong Neon DB với trạng thái `PhotoStatus.PROCESSING`.
    - [ ] Bắn `PhotoUploadEvent` vào `photo.upload.queue` qua `RabbitTemplate`.
    - [ ] Trả về ngay lập tức cho client trong **< 150ms** kèm trạng thái `202 Accepted` hoặc DTO tạm thời.
  - [ ] Tạo mới `PhotoProcessingConsumer.java`:
    - [ ] `@RabbitListener(queues = RabbitMQConfig.PHOTO_UPLOAD_QUEUE)`.
    - [ ] Đọc file tạm, thực hiện center-crop 1:1 và resize thumbnail 160x160 bằng `Thumbnailator`.
    - [ ] Upload 2 file chính thức lên S3/Garage với header `Cache-Control: public, max-age=31536000, immutable`.
    - [ ] Cập nhật bản ghi `Photo` thành `PhotoStatus.ACTIVE` kèm URL chính thức.
    - [ ] Tự động ghi CalorieLog (nếu có calo) và evict cache `myphotos::{senderId}`.
    - [ ] Xuất bản event `feed.fanout` để làm ấm feed bạn bè.
    - [ ] Dọn dẹp file tạm.
  - [ ] Viết test `PhotoUploadAsyncTest.java`:
    - [ ] Kiểm thử luồng upload phản hồi nhanh < 150ms.
    - [ ] Kiểm thử worker xử lý ảnh hoàn chỉnh từ queue.
  - [ ] Chạy `./gradlew test` và tạo PR.

---

### PR #8: Feed Fan-out Cache Warmer & Quản Trị DLQ Replay Admin API
- **Nhánh Git:** `feat/amqp-feed-fanout-and-dlq-management`
- **Mức độ:** 🟢 **P2 (Medium)**
- **Commit đề xuất:** `feat(amqp): implement feed fan-out cache warming and dlq replay admin api`
- **Danh sách công việc:**
  - [ ] Bổ sung Queue `feed.fanout.queue` và binding vào [RabbitMQConfig.java](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/java/com/codegym/locketclone/common/config/RabbitMQConfig.java).
  - [ ] Tạo mới `FeedFanoutConsumer.java`:
    - [ ] Nhận sự kiện `feed.fanout`.
    - [ ] Quét danh sách bạn bè của người đăng.
    - [ ] Làm ấm (warm-up) hoặc evict cache feed của toàn bộ bạn bè để khi mở app là thấy ngay ảnh mới.
  - [ ] Xây dựng dịch vụ quản trị Dead Letter Queue:
    - [ ] Tạo `DlqAdminService.java` và `DlqAdminController.java`:
      - `GET /api/v1/admin/dlq/stats`: Thống kê số lượng message đang kẹt trong DLQ.
      - `POST /api/v1/admin/dlq/replay`: Lấy message từ DLQ và gửi ngược lại exchange gốc để chạy lại.
  - [ ] Viết integration test cho luồng DLQ replay.
  - [ ] Chạy `./gradlew test` và tạo PR.

---

### PR #9: Load Testing, Đo RAM Heroku Dyno < 300MB & Nghiệm Thu Toàn Diện
- **Nhánh Git:** `perf/redis-rabbitmq-acceptance-and-benchmarking`
- **Mức độ:** 🟡 **P1 (High - Nghiệm thu Release Production)**
- **Commit đề xuất:** `perf(benchmark): verify heroku dyno memory stability and api latency benchmarks`
- **Danh sách công việc:**
  - [ ] Viết kịch bản test hiệu năng (benchmark script) giả lập tải:
    - [ ] 100 requests đồng thời xem Reaction Summary (kỳ vọng: < 3ms).
    - [ ] 50 requests đồng thời xem Dashboard chi tiêu & calo (kỳ vọng: < 5ms).
    - [ ] 20 uploads ảnh đồng thời (kỳ vọng: phản hồi API < 150ms).
  - [ ] Deploy lên Heroku và theo dõi qua `heroku logs --tail --app heroku-7usd`:
    - [ ] Đo lượng RAM tiêu thụ thực tế của JVM: Mục tiêu **< 300MB / 512MB**.
    - [ ] Xác nhận không còn lỗi `Error R14` và `Metaspace OOM`.
  - [ ] Tạo tài liệu kết quả đo lường: `docs/BENCHMARK_RESULTS_REDIS_RABBITMQ.md`.
  - [ ] Cập nhật trạng thái hoàn thành 100% trong [REDIS_VALKEY_AND_RABBITMQ_CLOUD_INTEGRATION_PLAN.md](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/docs/REDIS_VALKEY_AND_RABBITMQ_CLOUD_INTEGRATION_PLAN.md).

---

## 💻 Cheatsheet Các Lệnh Git Thao Tác Nhanh

```bash
# ==========================================
# 1. Bắt đầu PR #1: Core Cache Manager
# ==========================================
git checkout main
git pull origin main
git checkout -b feat/cache-infra-redis-manager-and-fallback

# (Thực hiện code PR #1...)
./gradlew test
git add .
git commit -m "feat(cache): implement multi-ttl redis cache manager with graceful degradation"
git push -u origin feat/cache-infra-redis-manager-and-fallback

# ==========================================
# 2. Sau khi merge PR #1 -> Tiếp tục PR #2
# ==========================================
git checkout main
git pull origin main
git checkout -b feat/cache-partition-user-and-friendship

# (Thực hiện code PR #2...)
./gradlew test
git add .
git commit -m "feat(cache): integrate redis caching for user identity and friendship queries"
git push -u origin feat/cache-partition-user-and-friendship
```

---

## 🔒 Quy Chuẩn An Toàn & Rollback (Safety & Rollback Protocol)

1. **Không ngắt quãng người dùng (Zero Downtime & Graceful Degradation):**
   - Luôn duy trì `CustomCacheErrorHandler` để nếu Upstash Redis gặp sự cố mạng, ứng dụng tự động truy vấn DB bình thường thay vì trả lỗi 500.
2. **Không thất thoát dữ liệu (Zero Message Loss):**
   - Mọi task nặng đẩy vào RabbitMQ đều được bảo vệ bởi Dead Letter Queue (`checked.dead-letter.queue`).
3. **Bảo mật biến môi trường:**
   - 10 biến môi trường kết nối Cloud đã được đưa vào Heroku Config Vars và `.env` cục bộ (được ignore bởi git). Tuyệt đối không commit credentials.
