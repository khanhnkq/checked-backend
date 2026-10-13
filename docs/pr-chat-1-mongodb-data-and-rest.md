# Kế Hoạch Triển Khai Chi Tiết: PR-Chat-1

> **Tiêu đề PR**: `feat(chat): backend-mongodb-data-layer-and-rest-api`  
> **Nhánh Git**: `feature/chat-mongodb-rest` (tạo từ `main`)  
> **Mức độ ưu tiên**: 🟡 **P1 (Core Feature - Nền tảng dữ liệu cho toàn bộ module Chat)**  
> **Phạm vi ảnh hưởng**: `build.gradle`, `application.yml`, `common/config`, `common/exception`, `chat/*`  
> **Cam kết an toàn**: ❌ **KHÔNG DEPLOY HEROKU TRƯỚC KHI TESTED** (Kiểm thử toàn bộ tại local với MongoDB Atlas M0 hoặc local Docker MongoDB)

---

## 1. 🎯 Mục Tiêu Của PR-Chat-1

1. **Thiết lập nền tảng Spring Data MongoDB**:
   - Tích hợp driver `spring-boot-starter-data-mongodb` chạy song song cùng `spring-boot-starter-data-jpa` (PostgreSQL Supabase).
   - Đảm bảo cấu hình repository độc lập (`@EnableMongoRepositories` và `@EnableJpaRepositories`), không gây xung đột Bean hay persistence context.
2. **Xây dựng Data Layer (NoSQL Documents & Indexes)**:
   - Document `Conversation`: Quản lý cuộc hội thoại giữa 2 bạn bè, lưu snapshot tin nhắn cuối `lastMessage`, bản đồ thời điểm đọc `readStatus`.
   - Document `Message`: Quản lý tin nhắn (loại `TEXT` hoặc `PHOTO_REPLY`), lưu context trích dẫn `replyTo`, snapshot ảnh `photoContext`.
   - Tự động tạo Compound Indexes tối ưu truy vấn phân trang và lọc dữ liệu.
3. **Hiện thực Nghiệp Vụ Core (`ChatService` & `ChatServiceImpl`)**:
   - Kiểm tra quan hệ bạn bè qua `FriendshipRepository` (PostgreSQL): Chỉ cho phép nhắn tin khi trạng thái bạn bè là `ACCEPTED`.
   - Chuẩn hoá và sắp xếp `participantIds` để đảm bảo mỗi cặp user chỉ có duy nhất 1 cuộc trò chuyện.
   - Tính toán số lượng tin nhắn chưa đọc (`unreadCount`) cho từng hội thoại dựa trên `readStatus`.
   - Phân trang tin nhắn hiệu năng cao bằng Cursor-based pagination (`sentAt`).
4. **Cung cấp REST API Endpoints**:
   - `GET /api/conversations`: Lấy danh sách hội thoại của user.
   - `POST /api/conversations`: Mở/tạo hội thoại với `friendId`.
   - `GET /api/conversations/{id}/messages`: Lấy tin nhắn theo cursor.
   - `PUT /api/conversations/{id}/read`: Đánh dấu đã đọc.

---

## 2. 🗂️ Danh Sách Files Thay Đổi & Tạo Mới

### 2.1. Thay đổi cấu hình dự án
- [build.gradle](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/build.gradle): Thêm `spring-boot-starter-data-mongodb`.
- [application.yml](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/resources/application.yml): Thêm cấu hình `spring.data.mongodb`.
- [.env.example](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/.env.example): Thêm biến mẫu `MONGODB_URI`.
- [ErrorCode.java](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/java/com/codegym/locketclone/common/exception/ErrorCode.java): Bổ sung mã lỗi liên quan đến Chat.

### 2.2. Files mã nguồn tạo mới (`chat/` package)
```
src/main/java/com/codegym/locketclone/
├── common/config/
│   └── MongoConfig.java                         ← Kích hoạt Mongo Auditing, MongoRepositories
└── chat/
    ├── document/
    │   ├── Conversation.java                    ← Document collection 'conversations'
    │   ├── Message.java                         ← Document collection 'messages'
    │   ├── MessageType.java                     ← Enum: TEXT, PHOTO_REPLY
    │   ├── LastMessageInfo.java                 ← Embedded snapshot
    │   ├── ReplyToInfo.java                     ← Embedded snapshot
    │   └── PhotoContextInfo.java                ← Embedded snapshot
    ├── repository/
    │   ├── ConversationRepository.java          ← Spring Data MongoRepository
    │   └── MessageRepository.java               ← Spring Data MongoRepository
    ├── dto/
    │   ├── request/
    │   │   ├── CreateConversationRequest.java
    │   │   └── SendMessageRequest.java
    │   └── response/
    │       ├── ConversationResponse.java
    │       ├── MessageResponse.java
    │       ├── MessageCursorPageResponse.java
    │       └── FriendSummaryResponse.java
    ├── mapper/
    │   └── ChatMapper.java                      ← MapStruct mapper
    ├── service/
    │   ├── ChatService.java                     ← Business interface
    │   └── impl/ChatServiceImpl.java             ← Business logic
    └── controller/
        └── ChatController.java                  ← REST Controller (/api/conversations)
```

---

## 3. 📐 Thiết Kế Chi Tiết Các Lớp (Class Design)

### 3.1. Document: `Conversation.java`
```java
@Document(collection = "conversations")
@CompoundIndexes({
    @CompoundIndex(name = "idx_conversations_user_updated", def = "{'participantIds': 1, 'updatedAt': -1}"),
    @CompoundIndex(name = "idx_conversations_participants_unique", def = "{'participantIds': 1}", unique = true)
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Conversation {

    @Id
    private String id; // Hex String đại diện cho ObjectId

    private List<UUID> participantIds; // Luôn sort tăng dần: [UUID_min, UUID_max]

    private LastMessageInfo lastMessage;

    @Builder.Default
    private Map<String, Instant> readStatus = new HashMap<>(); // Key: UUID string của user, Value: thời điểm đọc gần nhất

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
```

### 3.2. Document: `Message.java`
```java
@Document(collection = "messages")
@CompoundIndexes({
    @CompoundIndex(name = "idx_messages_conversation_sent_at", def = "{'conversationId': 1, 'sentAt': -1}"),
    @CompoundIndex(name = "idx_messages_conversation_read_by", def = "{'conversationId': 1, 'readBy': 1}")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message {

    @Id
    private String id;

    @Indexed
    private String conversationId;

    private UUID senderId;

    private String content;

    @Builder.Default
    private MessageType type = MessageType.TEXT;

    private ReplyToInfo replyTo; // Nullable

    private PhotoContextInfo photoContext; // Nullable, có dữ liệu khi type = PHOTO_REPLY

    @Builder.Default
    private Set<UUID> readBy = new HashSet<>();

    @Builder.Default
    private Set<UUID> deletedFor = new HashSet<>();

    @CreatedDate
    private Instant sentAt;
}
```

### 3.3. Enum & Value Objects (Embedded Documents)
```java
public enum MessageType {
    TEXT,
    PHOTO_REPLY
}

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LastMessageInfo {
    private String messageId;
    private UUID senderId;
    private String content;
    private MessageType type;
    private Instant sentAt;
}

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReplyToInfo {
    private String messageId;
    private UUID senderId;
    private String content;
    private MessageType type;
}

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PhotoContextInfo {
    private UUID photoId;
    private String imageUrl;
    private String senderName;
}
```

---

## 4. ⚙️ Repository Interfaces

### 4.1. `ConversationRepository.java`
```java
@Repository
public interface ConversationRepository extends MongoRepository<Conversation, String> {

    // Tìm cuộc hội thoại giữa 2 user cụ thể
    Optional<Conversation> findByParticipantIds(List<UUID> participantIds);

    // Lấy danh sách hội thoại của 1 user, sắp xếp theo thời gian cập nhật mới nhất
    List<Conversation> findByParticipantIdsContainingOrderByUpdatedAtDesc(UUID userId);
}
```

### 4.2. `MessageRepository.java`
```java
@Repository
public interface MessageRepository extends MongoRepository<Message, String> {

    // Lấy tin nhắn trang đầu tiên (mới nhất)
    List<Message> findByConversationIdOrderBySentAtDesc(String conversationId, Pageable pageable);

    // Lấy tin nhắn cũ hơn cursor (before sentAt)
    List<Message> findByConversationIdAndSentAtBeforeOrderBySentAtDesc(
            String conversationId, Instant sentAt, Pageable pageable);

    // Đếm số tin chưa đọc trong hội thoại đối với user hiện tại
    long countByConversationIdAndSenderIdNotAndReadByNotContaining(
            String conversationId, UUID currentUserId, UUID currentUserIdCheck);
}
```

---

## 5. 🌐 Đặc Tả REST API Endpoints (ChatController)

### 5.1. `GET /api/conversations`
- **Mô tả**: Lấy danh sách toàn bộ cuộc trò chuyện của user hiện tại.
- **Header**: `Authorization: Bearer <jwt>`
- **Response**: `200 OK`
```json
[
  {
    "id": "670c1234abcd5678ef901234",
    "friend": {
      "id": "f1e2d3c4-b5a6-7890-abcd-ef1234567890",
      "fullName": "Nguyễn Văn A",
      "avatarUrl": "https://res.cloudinary.com/..."
    },
    "lastMessage": {
      "messageId": "670c5678abcd5678ef909999",
      "senderId": "f1e2d3c4-b5a6-7890-abcd-ef1234567890",
      "content": "Trưa nay ăn gì thế?",
      "type": "TEXT",
      "sentAt": "2026-10-13T12:30:15Z"
    },
    "unreadCount": 2,
    "updatedAt": "2026-10-13T12:30:15Z"
  }
]
```

### 5.2. `POST /api/conversations`
- **Mô tả**: Tìm hoặc khởi tạo cuộc trò chuyện với bạn bè.
- **Request Body**:
```json
{
  "friendId": "f1e2d3c4-b5a6-7890-abcd-ef1234567890"
}
```
- **Validation**:
  - Không được chat với chính mình (`CANNOT_CHAT_WITH_SELF`).
  - Phải là bạn bè đã ACCEPTED trong PostgreSQL (`CANNOT_CHAT_WITH_NON_FRIEND`).
- **Response**: `200 OK` (Trả về thông tin cuộc trò chuyện vừa tạo hoặc đã có sẵn).

### 5.3. `GET /api/conversations/{conversationId}/messages`
- **Mô tả**: Lấy lịch sử tin nhắn của cuộc trò chuyện theo cơ chế Cursor-based.
- **Query Params**:
  - `before`: ISO-8601 Instant string (ví dụ: `2026-10-13T12:00:00Z`). Nếu không truyền -> Lấy các tin mới nhất.
  - `limit`: Số lượng tin (mặc định: `30`, tối đa: `50`).
- **Response**: `200 OK`
```json
{
  "messages": [
    {
      "id": "670c5678abcd5678ef909999",
      "conversationId": "670c1234abcd5678ef901234",
      "senderId": "c8a1b2c3-4d5e-6f7a-8b9c-0d1e2f3a4b5c",
      "content": "Bát phở ngon thế!",
      "type": "PHOTO_REPLY",
      "sentAt": "2026-10-13T12:30:15Z",
      "replyTo": null,
      "photoContext": {
        "photoId": "9a8b7c6d-5e4f-3a2b-1c0d-ef9876543210",
        "imageUrl": "https://res.cloudinary.com/.../pho.jpg",
        "senderName": "Quốc Khánh"
      },
      "isRead": true
    }
  ],
  "nextCursor": "2026-10-13T11:45:00Z",
  "hasMore": true
}
```

### 5.4. `PUT /api/conversations/{conversationId}/read`
- **Mô tả**: Đánh dấu người dùng hiện tại đã đọc toàn bộ tin nhắn trong cuộc trò chuyện đến thời điểm hiện tại.
- **Response**: `200 OK`
```json
{
  "conversationId": "670c1234abcd5678ef901234",
  "readAt": "2026-10-13T12:35:00Z"
}
```

---

## 6. 🧪 Kế Hoạch Kiểm Thử (Testing Strategy)

1. **Unit Test với Mockito (`ChatServiceImplTest`)**:
   - Test tạo cuộc trò chuyện khi chưa có: kiểm tra sắp xếp `participantIds`.
   - Test mở cuộc trò chuyện đã có sẵn: trả về đúng conversation cũ.
   - Test chặn khi người dùng chưa kết bạn: ném `AppException(ErrorCode.CANNOT_CHAT_WITH_NON_FRIEND)`.
   - Test chặn tự chat với bản thân: ném `AppException(ErrorCode.CANNOT_CHAT_WITH_SELF)`.
   - Test phân trang Cursor: kiểm tra limit và điều kiện `sentAtBefore`.
   - Test đánh dấu đã đọc: kiểm tra cập nhật map `readStatus` và `readBy`.
2. **Integration Test REST Controller (`ChatControllerTest`)**:
   - Sử dụng `@WebMvcTest` hoặc `@SpringBootTest` kết hợp `@MockBean` kiểm tra response status và JSON contract.

---

## 7. ✅ Checklist Hoàn Thành PR-Chat-1

- [ ] Gradle build thành công, không xung đột JPA và Mongo dependencies.
- [ ] Document classes và Repository Mongo hoạt động bình thường.
- [ ] Toàn bộ 4 REST Endpoints hoạt động đúng contract.
- [ ] Xử lý ngoại lệ với `ErrorCode` đầy đủ.
- [ ] 100% Unit test cho `ChatServiceImpl` vượt qua (pass).
- [ ] Local server khởi động trơn tru khi cấu hình `MONGODB_URI`.
