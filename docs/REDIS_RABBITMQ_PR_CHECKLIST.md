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
  - [x] **PR #3:** Phân vùng 2: Cache Reactions Summary, Feed & Kho ảnh MyPhotos *(Hoàn thành: Commit `e52ba7f`, Heroku Release v33 UP)*
  - [x] **PR #4:** Phân vùng 3 & 4: Cache Dashboard Chi Tiêu & Nhật Ký Calo *(Hoàn thành: Commit `b4fc214`, Merge `fa9b3da`, Heroku Release v34 UP)*
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
- **Mức độ:** 🟢 **ĐÃ HOÀN THÀNH & LIVE TRÊN HEROKU** (Release `v33`)
- **Commit:** `feat(cache): implement partition 2 caching for photo reactions summary, feed, and myphotos with smart eviction (PR #3)` (`e52ba7f`)
- **Kế hoạch chi tiết:** [PR3_CACHE_PHOTO_AND_REACTIONS.md](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/docs/PR3_CACHE_PHOTO_AND_REACTIONS.md)
- **Danh sách công việc:**
  - [x] Tạo DTO an toàn cho Jackson Redis:
    - [x] [RestSlice.java](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/java/com/codegym/locketclone/common/dto/RestSlice.java): wrapper cho `Slice<T>` với `ArrayList<T>` nội bộ và `@JsonCreator`.
    - [x] [RestPage.java](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/java/com/codegym/locketclone/common/dto/RestPage.java): wrapper cho `Page<T>` với `ArrayList<T>` nội bộ và `@JsonCreator`.
    - [x] [PhotoReactionStats.java](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/java/com/codegym/locketclone/photo/dto/PhotoReactionStats.java): DTO chứa dữ liệu tóm tắt reaction cho photo.
  - [x] Tái cấu trúc logic phản hồi cảm xúc trong [PhotoServiceImpl.java](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/java/com/codegym/locketclone/photo/PhotoServiceImpl.java):
    - [x] Tách phần thống kê chung (tổng số count, count theo loại emoji, 5 người thả tim gần nhất) ra method `getReactionStats` với `@Cacheable(value = CacheConfig.REACTIONS_SUMMARY_CACHE, key = "#photoId")` (TTL: 15 phút).
    - [x] Kết hợp `myReaction` người dùng hiện tại ở tầng service trước khi trả về `PhotoReactionSummaryResponse`.
    - [x] Thêm `@CacheEvict(value = CacheConfig.REACTIONS_SUMMARY_CACHE, key = "#photoId")` khi `upsertReaction` và `removeReaction`.
  - [x] Tích hợp cache Feed & Kho ảnh trong [PhotoService.java](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/java/com/codegym/locketclone/photo/PhotoService.java) & [PhotoServiceImpl.java](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/java/com/codegym/locketclone/photo/PhotoServiceImpl.java):
    - [x] Cache trang đầu feed của người dùng: `@Cacheable(value = CacheConfig.FEED_CACHE, key = "#userId", condition = "#p2.pageNumber == 0 and #p1 == null")` (TTL: 10 phút).
    - [x] Cache trang đầu ảnh cá nhân: `@Cacheable(value = CacheConfig.MY_PHOTOS_CACHE, key = "#userId", condition = "#p1.pageNumber == 0")` (TTL: 15 phút).
    - [x] Cache chi tiết ảnh: `@Cacheable(value = CacheConfig.PHOTO_DETAIL_CACHE, key = "#userId + ':' + #photoId")` (TTL: 30 phút).
  - [x] Xử lý eviction đa phân vùng với `@Caching(evict = { ... })`:
    - [x] Cập nhật expense / transaction: evict đồng thời `PHOTO_DETAIL_CACHE`, `FEED_CACHE`, `MY_PHOTOS_CACHE`.
    - [x] Xóa ảnh `deleteTransaction`: evict đồng thời cả 4 phân vùng `PHOTO_DETAIL_CACHE`, `FEED_CACHE`, `MY_PHOTOS_CACHE`, `REACTIONS_SUMMARY_CACHE`.
  - [x] Viết integration test [PhotoAndReactionCacheTest.java](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/test/java/com/codegym/locketclone/infra/PhotoAndReactionCacheTest.java):
    - [x] Xác thực `photo_detail` nạp cache và evict khi update expense.
    - [x] Xác thực `reactions_summary` nạp cache, hit cache lần 2, và evict khi thả/xóa reaction.
    - [x] Xác thực serialize/deserialize `RestSlice` & `RestPage` qua Redis cho feed & myphotos trang 0.
    - [x] Xác thực `deleteTransaction` evict sạch cache `feed` và `myphotos`.
  - [x] Chạy `./gradlew test` (toàn bộ 211 tests PASS 100%).
  - [x] Merge vào `main`, push GitHub & deploy thành công lên Heroku (Release `v33`).
  - [x] Dyno khởi động thành công trong 22.9s, live và healthy trên Heroku.

---

### PR #4: Phân Vùng 3 & 4 - Cache Dashboard Chi Tiêu & Nhật Ký Calo
- **Nhánh Git:** `feat/cache-partition-expense-and-calorie`
- **Mức độ:** 🟢 **ĐÃ HOÀN THÀNH & LIVE TRÊN HEROKU** (Release `v34`)
- **Commit:** `feat(cache): implement partition 3 and 4 caching for expense and calorie dashboards (PR #4)` (`b4fc214`), Merge `fa9b3da`
- **Kế hoạch chi tiết:** [PR4_CACHE_EXPENSE_AND_CALORIE.md](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/docs/PR4_CACHE_EXPENSE_AND_CALORIE.md)
- **Danh sách công việc:**
  - [x] Cập nhật [ExpenseService.java](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/java/com/codegym/locketclone/expense/ExpenseService.java) & [ExpenseServiceImpl.java](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/java/com/codegym/locketclone/expense/ExpenseServiceImpl.java):
    - [x] `@Cacheable(value = CacheConfig.EXPENSE_CATEGORIES_CACHE, key = "'list:' + #userId")` cho `getCategories`.
    - [x] `@Cacheable(value = CacheConfig.EXPENSE_BUDGETS_CACHE, key = "#userId + ':' + (#monthKey != null ? #monthKey : '')")` cho `getBudget`.
    - [x] `@Cacheable(value = CacheConfig.EXPENSE_SUMMARY_CACHE, key = "#userId + ':' + (#monthKey != null ? #monthKey : '')")` cho `getExpenseSummary`.
    - [x] `@Cacheable(value = CacheConfig.EXPENSE_CASHFLOW_CACHE, key = "'m:' + #userId + ':' + (#monthKey != null ? #monthKey : '')")` cho `getCashflowSummary`.
    - [x] `@Cacheable(value = CacheConfig.EXPENSE_CASHFLOW_CACHE, key = "'y:' + #userId + ':' + #year")` cho `getYearlyCashflowSummary`.
    - [x] Bọc `new ArrayList<>(...)` cho tất cả danh sách lồng nhau trong DTO để tương thích hoàn toàn với Jackson Redis deserializer.
    - [x] Bổ sung helper `evictExpenseCaches(UUID userId, String monthKey, Integer year)` để xóa cache targeted.
    - [x] Bổ sung `@CacheEvict` / `@Caching` khi `createCategory`, `updateCategory`, `upsertBudget`.
  - [x] Cập nhật [CalorieService.java](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/java/com/codegym/locketclone/calorie/CalorieService.java) & [CalorieServiceImpl.java](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/java/com/codegym/locketclone/calorie/CalorieServiceImpl.java):
    - [x] `@Cacheable(value = CacheConfig.CALORIE_GOAL_CACHE, key = "#userId")` cho `getGoal`.
    - [x] `@Cacheable(value = CacheConfig.CALORIE_DAILY_CACHE, key = "#userId + ':' + (#date != null ? #date.toString() : T(java.time.LocalDate).now().toString())")` cho `getDailySummary`.
    - [x] `@Cacheable(value = CacheConfig.CALORIE_SUMMARY_CACHE, key = "#userId + ':' + (#monthKey != null ? #monthKey : '')")` cho `getMonthlySummary`.
    - [x] Bọc `new ArrayList<>(...)` cho `meals` trong `DailyCalorieSummaryDTO`.
    - [x] Bổ sung helper `evictCalorieCaches(UUID userId, LocalDate date)`.
    - [x] Tự động evict khi `updateGoal`, `createManualLog`, `deleteLog`, `createLogFromPhoto`.
  - [x] Cập nhật [PhotoServiceImpl.java](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/java/com/codegym/locketclone/photo/PhotoServiceImpl.java) (Cross-Service Eviction):
    - [x] Tự động evict Expense caches khi `uploadPhoto` có giao dịch chi tiêu/thu nhập.
    - [x] Tự động evict Expense caches khi `updatePhotoExpense` và `updatePhotoTransaction`.
    - [x] Tự động evict cả Expense caches và Calorie caches khi `deleteTransaction`.
  - [x] Viết integration test [ExpenseAndCalorieCacheTest.java](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/test/java/com/codegym/locketclone/infra/ExpenseAndCalorieCacheTest.java):
    - [x] Xác thực cache hit & eviction cho Categories danh mục chi tiêu.
    - [x] Xác thực cache hit, multi-level deserialization & eviction cho Budget, Expense Summary, Monthly/Yearly Cashflow.
    - [x] Xác thực cache hit & eviction cho Calorie Goal.
    - [x] Xác thực cache hit & eviction cho Calorie Daily Summary & Monthly Summary.
    - [x] Xác thực cross-service eviction khi `PhotoService.deleteTransaction`.
  - [x] Chạy `./gradlew test` (toàn bộ test suite PASS 100%).
  - [x] Merge vào `main`, push GitHub & deploy live thành công lên Heroku (Release `v34`).

---

### PR #5: Phân Vùng 5 - Cache Badge Tin Nhắn Chưa Đọc (Chat Unread & Conversations)
- **Nhánh Git:** `feat/cache-partition-chat-badges`
- **Mức độ:** 🟢 **P2 (Medium)**
- **Commit đề xuất:** `feat(cache): implement partition 5 chat unread and conversations caching with zero hardcoded credentials`
- **Danh sách công việc:**
  - [x] Cập nhật [ChatService.java](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/java/com/codegym/locketclone/chat/service/ChatService.java) & [ChatServiceImpl.java](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/java/com/codegym/locketclone/chat/service/impl/ChatServiceImpl.java):
    - [x] `@Cacheable(value = CacheConfig.CHAT_UNREAD_CACHE, key = "#currentUserId")` cho `getTotalUnreadCount`.
    - [x] `@Cacheable(value = CacheConfig.CHAT_CONVERSATIONS_CACHE, key = "#currentUserId")` cho `getMyConversations`.
    - [x] Bọc `new ArrayList<>(...)` cho `ConversationResponse` để Jackson deserializer trong Redis vận hành an toàn.
    - [x] Xây dựng helper `evictChatCaches(UUID userId, boolean evictUnread, boolean evictConversations)` thực hiện targeted cache invalidation.
    - [x] Tự động evict khi gửi tin nhắn mới (`sendMessage`), đọc tin nhắn (`markAsRead`), tạo cuộc trò chuyện mới (`getOrCreateConversation`), và thu hồi/xóa tin nhắn (`deleteMessage`).
  - [x] Viết integration test [ChatCacheTest.java](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/test/java/com/codegym/locketclone/infra/ChatCacheTest.java):
    - [x] Xác thực cache hit & eviction cho `chat_unread` (Badge tin nhắn chưa đọc).
    - [x] Xác thực cache hit & eviction cho `chat_conversations` (Danh sách cuộc trò chuyện và previews).
  - [x] Dọn sạch 100% hardcode credentials trong toàn bộ các test class và cấu hình YAML, nạp động từ `.env` qua Gradle test runner, siết chặt `.gitignore`.
  - [x] Chạy `./gradlew test` (toàn bộ hơn 220 tests PASS 100%).
  - [x] Merge vào `main`, push GitHub & deploy live thành công lên Heroku (Release `v35`).

---

### PR #6: Worker Gửi Email OTP Bất Đồng Bộ & Xử Lý Thất Bại (DLQ)
- **Nhánh Git:** `feat/rabbitmq-async-email-worker`
- **Mức độ:** 🟡 **P1 (High)**
- **Commit đề xuất:** `feat(queue): implement asynchronous email worker with dead letter queue and retries`
- **Danh sách công việc:**
  - [x] Tạo DTO sự kiện: [OtpEmailMessage.java](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/java/com/codegym/locketclone/notification/dto/OtpEmailMessage.java) (toEmail, recipientName, otpCode, subject, messageType, createdAt).
  - [x] Cấu hình [RabbitMQConfig.java](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/java/com/codegym/locketclone/common/config/RabbitMQConfig.java) và YAML:
    - [x] Đăng ký `SimpleRabbitListenerContainerFactory` với `default-requeue-rejected: false`.
    - [x] Cấu hình Spring AMQP listener container retry tự động 3 lần (initial: 1000ms, multiplier: 2.0, max: 5000ms).
    - [x] Khi vượt quá số lần retry: RabbitMQ tự động đẩy message sang `checked.dlx.exchange` -> `checked.dead-letter.queue` (DLQ).
  - [x] Xây dựng Producer [RabbitMqEmailProducer.java](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/java/com/codegym/locketclone/notification/RabbitMqEmailProducer.java):
    - [x] Đánh dấu `@Primary`, publish message vào `checked.direct.exchange` với routing key `mail.send`.
    - [x] Tích hợp cơ chế Graceful Fallback khi RabbitMQ broker gặp sự cố mạng để không làm gián đoạn luồng đăng ký của người dùng.
  - [x] Cập nhật [SmtpEmailService.java](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/java/com/codegym/locketclone/notification/SmtpEmailService.java) & [LoggingEmailService.java](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/java/com/codegym/locketclone/notification/LoggingEmailService.java):
    - [x] Bỏ `@Primary` khỏi `SmtpEmailService`, đặt tên bean `smtpEmailService`.
    - [x] Bổ sung method `sendOtpEmailDirect` ném exception để Consumer kích hoạt Retry/DLQ khi SMTP fail.
  - [x] Xây dựng Consumer [EmailNotificationConsumer.java](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/java/com/codegym/locketclone/notification/consumer/EmailNotificationConsumer.java):
    - [x] `@RabbitListener(queues = RabbitMQConfig.MAIL_NOTIFICATION_QUEUE)`.
    - [x] Nhận `OtpEmailMessage`, gọi SMTP gửi email background.
  - [x] Viết integration test [AsyncEmailWorkerTest.java](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/test/java/com/codegym/locketclone/infra/AsyncEmailWorkerTest.java):
    - [x] Xác thực gửi email qua Producer thành công.
    - [x] Xác thực roundtrip publish & consumer xử lý message an toàn.
    - [x] Xác thực cấu trúc topology của `mail.notification.queue` và `checked.dead-letter.queue` (DLQ).
    - [x] Xác thực cơ chế Graceful Fallback khi broker ném `AmqpException`.
  - [x] Chạy `./gradlew test` (toàn bộ 220+ tests PASS 100%).
  - [x] Merge vào `main`, push GitHub & deploy live thành công lên Heroku (Release `v36`).

---

### PR #7: Non-blocking Photo Upload & PhotoProcessingConsumer (Resize 1:1, Thumb, S3)
- **Nhánh Git:** `feat/amqp-photo-processing-worker`
- **Mức độ:** 🔴 **P0 (Critical - Giải quyết triệt để lỗi sập RAM Heroku 512MB & Error R14)**
- **Commit đề xuất:** `feat(amqp): implement non-blocking photo upload and async processing worker`
- **Danh sách công việc:**
  - [x] Tạo DTO sự kiện: `PhotoUploadEvent` (photoId, senderId, tempStorageKey, caption, amount, transactionType, location, calories, mealType, recipientScope, createdAt).
  - [x] Tái cấu trúc `uploadPhoto` trong [PhotoServiceImpl.java](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/java/com/codegym/locketclone/photo/PhotoServiceImpl.java):
    - [x] Tải file thô lên khu vực tạm (temp bucket/path) với tốc độ nhanh nhất.
    - [x] Tạo bản ghi `Photo` trong Neon DB với trạng thái `PhotoStatus.PROCESSING`.
    - [x] Bắn `PhotoUploadEvent` vào `photo.upload.queue` qua `RabbitTemplate`.
    - [x] Trả về ngay lập tức cho client trong **< 150ms** kèm trạng thái `202 Accepted` hoặc DTO tạm thời.
  - [x] Tạo mới `PhotoProcessingConsumer.java`:
    - [x] `@RabbitListener(queues = RabbitMQConfig.PHOTO_UPLOAD_QUEUE, concurrency = "1")`.
    - [x] Đọc file tạm, thực hiện center-crop 1:1 và resize thumbnail bằng `Thumbnailator`.
    - [x] Upload 2 file chính thức lên S3/Garage với header `Cache-Control: public, max-age=31536000, immutable`.
    - [x] Cập nhật bản ghi `Photo` thành `PhotoStatus.READY` kèm URL chính thức.
    - [x] Tự động ghi CalorieLog (nếu có calo) và evict cache `myphotos::{senderId}`.
    - [x] Dọn dẹp file tạm.
  - [x] Viết test `PhotoUploadAsyncTest.java`:
    - [x] Kiểm thử luồng upload phản hồi nhanh < 150ms.
    - [x] Kiểm thử worker xử lý ảnh hoàn chỉnh từ queue.
    - [x] Kiểm thử Dead Letter Queue (DLQ) khi xảy ra sự cố.
  - [x] Chạy `./gradlew test` (228 tests PASS 100%) và tạo PR.
  - [x] Merge vào `main`, push GitHub & deploy live thành công lên Heroku (Release `v37`).

---

### PR #8: Feed Fan-out Cache Warmer & Quản Trị DLQ Replay Admin API
- **Nhánh Git:** `feat/amqp-feed-fanout-and-dlq-management`
- **Mức độ:** 🟢 **ĐÃ HOÀN THÀNH (236/236 Tests PASS 100%)**
- **Commit đề xuất:** `feat(amqp): implement feed fan-out cache warming and dlq replay admin api (PR #8)`
- **Kế hoạch chi tiết:** [PR8_FEED_FANOUT_AND_DLQ_REPLAY.md](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/docs/PR8_FEED_FANOUT_AND_DLQ_REPLAY.md)
- **Danh sách công việc:**
  - [x] Bổ sung Queue `feed.fanout.queue` và binding vào [RabbitMQConfig.java](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/java/com/codegym/locketclone/common/config/RabbitMQConfig.java).
  - [x] Tạo mới [FeedFanoutConsumer.java](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/java/com/codegym/locketclone/photo/consumer/FeedFanoutConsumer.java):
    - [x] Nhận sự kiện `feed.fanout` (`FeedFanoutEvent`).
    - [x] Quét danh sách bạn bè của người đăng (`friendshipRepository.findAcceptedFriendIds`).
    - [x] Evict và gọi `photoService.getFeedPhotos` làm ấm trước (Pre-warm) cache feed trang 0 cho toàn bộ bạn bè để khi mở app là thấy ngay ảnh mới.
    - [x] Evict cache `MY_PHOTOS_CACHE` của sender.
  - [x] Xây dựng dịch vụ quản trị Dead Letter Queue:
    - [x] Tạo [DlqAdminService.java](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/java/com/codegym/locketclone/admin/DlqAdminService.java) và [DlqAdminController.java](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/java/com/codegym/locketclone/admin/DlqAdminController.java):
      - [x] `GET /api/v1/admin/dlq/stats`: Thống kê số lượng message đang đọng, consumer và trạng thái DLQ.
      - [x] `POST /api/v1/admin/dlq/replay`: Lấy message từ DLQ, giải mã header `x-death` hoặc `__TypeId__` và tái gửi ngược lại exchange đích để xử lý lại.
  - [x] Viết integration test [DlqAndFanoutIntegrationTest.java](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/test/java/com/codegym/locketclone/admin/DlqAndFanoutIntegrationTest.java) (toàn bộ pass).
  - [x] Chạy `./gradlew test` (toàn bộ 236 tests PASS 100%).

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
