# Kế Hoạch Chi Tiết PR #5: Phân Vùng 5 - Cache Badge Tin Nhắn Chưa Đọc & Danh Sách Cuộc Trò Chuyện (Chat Unread & Previews)

> **Tài liệu tham chiếu:**  
> - [REDIS_VALKEY_AND_RABBITMQ_CLOUD_INTEGRATION_PLAN.md](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/docs/REDIS_VALKEY_AND_RABBITMQ_CLOUD_INTEGRATION_PLAN.md) (Kiến trúc tổng thể)  
> - [REDIS_RABBITMQ_PR_CHECKLIST.md](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/docs/REDIS_RABBITMQ_PR_CHECKLIST.md) (Checklist tiến độ)  
> **Nhánh Git dự kiến:** `feat/cache-partition-chat-badges`  
> **Phân vùng Redis:** Phân vùng 5 (`chat_unread` & `chat_conversations`)  
> **Mục tiêu:** Cắt giảm triệt để các truy vấn quét collection MongoDB khi render badge đỏ và tab Chat trên ứng dụng iOS.

---

## 1. Vấn Đề Kỹ Thuật & Mục Tiêu

### 1.1. Hiện Trạng
- Ứng dụng iOS gửi request `GET /api/v1/conversations/unread-total` liên tục mỗi khi:
  - Mở app.
  - Chuyển tab giữa Camera, Feed, Lịch, Chat.
  - Nhận Push Notification hoặc có tương tác mới.
- Trong `ChatServiceImpl.getTotalUnreadCount`:
  - Hệ thống phải query toàn bộ `Conversation` của user trong MongoDB.
  - Với mỗi cuộc trò chuyện, thực hiện tính toán số tin nhắn chưa đọc dựa trên timestamp `readStatus`.
- Trong `ChatServiceImpl.getMyConversations`:
  - Hệ thống query danh sách `Conversation`, sau đó batch query `userRepository.findAllById(friendIds)` từ PostgreSQL để ghép `FriendSummaryResponse`.
  - Mặc dù có độ trễ chấp nhận được, nhưng khi lượng user đồng thời cao, việc truy vấn liên tục vào MongoDB làm tiêu tốn I/O không cần thiết.

### 1.2. Mục Tiêu PR #5
1. **Cache Badge Tin Chưa Đọc**:
   - Cache `chat_unread` với key `#currentUserId`, TTL **10 phút**.
   - Phản hồi siêu tốc trong `< 2ms` thay vì quét MongoDB.
2. **Cache Danh Sách Hội Thoại**:
   - Cache `chat_conversations` với key `#currentUserId`, TTL **10 phút**.
   - Lưu trữ đầy đủ preview tin nhắn gần nhất (`LastMessageInfo`), số tin chưa đọc (`unreadCount`), và thông tin bạn bè (`FriendSummaryResponse`).
3. **Targeted Two-Way Eviction**:
   - Khi `userA` gửi tin nhắn cho `userB`:
     - `userA`: Evict `chat_conversations` (để cập nhật vị trí hội thoại và `lastMessage`).
     - `userB`: Evict CẢ `chat_unread` (để badge tăng) và `chat_conversations` (để hội thoại nhảy lên đầu).
   - Khi `userB` đọc tin nhắn (`markAsRead`):
     - `userB`: Evict CẢ `chat_unread` (badge giảm về 0) và `chat_conversations` (`unreadCount` về 0).
   - Khi tạo hội thoại mới hoặc thu hồi tin nhắn:
     - Evict cache 2 chiều tương ứng.
4. **An Toàn Serialization**:
   - Mọi list trong DTO trả về bọc `new ArrayList<>(...)`.
   - Các trường `Instant` (`createdAt`, `updatedAt`, `sentAt`) tương thích hoàn hảo với `JavaTimeModule` đã đăng ký trong `CacheConfig`.

---

## 2. Ma Trận Caching & Khóa Redis

| Danh mục | Tên Cache | TTL | Định Dạng Cache Key | DTO / Đối Tượng Cache | Trigger Eviction |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Tổng Tin Chưa Đọc** | `chat_unread` | 10 phút | `{userId}` | `UnreadTotalResponse` | - Khi nhận tin nhắn mới (`sendMessage`)<br>- Khi đọc tin nhắn (`markAsRead`)<br>- Khi thu hồi tin nhắn (`deleteMessage`) |
| **Danh Sách Hội Thoại** | `chat_conversations` | 10 phút | `{userId}` | `List<ConversationResponse>` | - Khi gửi tin nhắn mới (cả 2 bên)<br>- Khi đọc tin nhắn (`markAsRead`)<br>- Khi tạo cuộc trò chuyện mới<br>- Khi xóa/thu hồi tin nhắn |

---

## 3. Chi Tiết Kế Hoạch Triển Khai

### Bước 1: Khai Báo Annotations trên `ChatService.java`
- Thêm import `CacheConfig`, `@Cacheable`.
- Gắn `@Cacheable(value = CacheConfig.CHAT_UNREAD_CACHE, key = "#currentUserId")` cho `getTotalUnreadCount`.
- Gắn `@Cacheable(value = CacheConfig.CHAT_CONVERSATIONS_CACHE, key = "#currentUserId")` cho `getMyConversations`.
- Khai báo helper method:
  ```java
  void evictChatCaches(UUID userId, boolean evictUnread, boolean evictConversations);
  ```

### Bước 2: Nâng Cấp `ChatServiceImpl.java`
1. **Inject `CacheManager`**:
   - Sử dụng `@Autowired(required = false) private CacheManager cacheManager;` kèm setter (giữ trọn vẹn tính tương thích với unit tests mock).
2. **Chuẩn Hóa Serialization trong `getMyConversations`**:
   - Thay `Collections.emptyList()` bằng `new ArrayList<>()`.
   - Bọc kết quả stream map bằng `new ArrayList<>(...)`.
3. **Cài Đặt Helper `evictChatCaches`**:
   ```java
   @Override
   public void evictChatCaches(UUID userId, boolean evictUnread, boolean evictConversations) {
       if (cacheManager == null || userId == null) return;
       try {
           if (evictUnread) {
               Cache unreadCache = cacheManager.getCache(CacheConfig.CHAT_UNREAD_CACHE);
               if (unreadCache != null) unreadCache.evict(userId);
           }
           if (evictConversations) {
               Cache conversationsCache = cacheManager.getCache(CacheConfig.CHAT_CONVERSATIONS_CACHE);
               if (conversationsCache != null) conversationsCache.evict(userId);
           }
       } catch (Exception ex) {
           log.warn("Lỗi khi evict chat cache cho user {}: {}", userId, ex.getMessage());
       }
   }
   ```
4. **Kích Hoạt Eviction Chính Xác tại Các Mutation**:
   - Trong `sendMessage`:
     ```java
     evictChatCaches(currentUserId, false, true); // Người gửi: chỉ đổi conversations
     evictChatCaches(otherUserId, true, true);     // Người nhận: đổi cả unread và conversations
     ```
   - Trong `markAsRead`:
     ```java
     evictChatCaches(currentUserId, true, true);  // Người đọc: unread giảm và conversations đổi
     ```
   - Trong `getOrCreateConversation`:
     - Nếu tạo mới Conversation trong MongoDB:
       ```java
       evictChatCaches(currentUserId, false, true);
       evictChatCaches(friendId, false, true);
       ```
   - Trong `deleteMessage`:
     - Nếu `DeleteMode.FOR_ME`: evict conversations của `currentUserId`.
     - Nếu `DeleteMode.FOR_EVERYONE`: evict conversations và unread của cả `currentUserId` và `otherUserId`.

### Bước 3: Viết Integration Test `ChatCacheTest.java`
- Tạo test class trong `com.codegym.locketclone.infra`:
  1. `testChatUnreadCountCache_HitAndEviction`:
     - Gọi `getTotalUnreadCount` -> cache hit.
     - Bạn bè gửi tin nhắn -> cache unread bị evict.
     - Đọc tin nhắn -> cache unread bị evict.
  2. `testChatConversationsCache_HitAndEviction`:
     - Gọi `getMyConversations` -> cache hit và deserialize an toàn các trường `Instant`, `LastMessageInfo`, `FriendSummaryResponse`.
     - Gửi tin nhắn -> cache conversations của cả 2 bên bị evict.

### Bước 4: Kiểm Thử Toàn Diện & Deploy
1. Chạy test suite: `./gradlew test` (đảm bảo 100% test cases PASS).
2. Commit trên nhánh `feat/cache-partition-chat-badges`.
3. Merge `main`, push GitHub `origin main`.
4. Deploy Heroku: `git push heroku main` -> Release v35.
5. Kiểm tra live log và cập nhật checklist.
