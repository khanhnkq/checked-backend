# 🛡️ Kế Hoạch Triển Khai Chi Tiết: Phase 3
## Chặn/Bỏ Chặn Người Dùng, Sinh Mã QR Cá Nhân & Gia Cố Bảo Mật (Block, QR Generator & Security Hardening)

> **Tài liệu tham chiếu**: [`docs/IMPLEMENTATION_PLAN_PROFILE_FRIEND_INVITATION.md`](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/docs/IMPLEMENTATION_PLAN_PROFILE_FRIEND_INVITATION.md)  
> **Nhánh Git dự kiến**: `feat/profile-link-friend-invitation-phase-3` (tạo từ `main`)  
> **Mức độ ưu tiên**: 🟡 **P1 (Hoàn thiện vòng đời bảo mật, chống abuse & nâng tầm trải nghiệm QR Code)**  
> **Mục tiêu**:  
> 1. Triển khai trọn vẹn tính năng Chặn (`POST /{userId}/block`), Bỏ chặn (`DELETE /{userId}/block`) và Danh sách bị chặn (`GET /blocked`).  
> 2. Gia cố bảo mật cho hệ thống Link mời kết bạn cũ (Vá lỗ hổng bypass Block & Lost Update race condition trên `usedCount`).  
> 3. Cung cấp API sinh mã QR Profile cá nhân (`GET /api/v1/users/profile/{username}/qr`) phục vụ quét kết bạn trực tiếp ngoài đời thực.  
> 4. Tích hợp Rate Limiting (Bucket4j) chống spam gửi lời mời kết bạn và chống bot cào dữ liệu profile.

---

## 1. MA TRẬN API ENDPOINTS PHASE 3

| Method | Endpoint | Quyền hạn | Mục đích nghiệp vụ |
| :--- | :--- | :---: | :--- |
| **`POST`** | `/api/v1/friendships/{userId}/block` | JWT | Chặn người dùng (hủy bạn bè, hủy pending request, chuyển sang `BLOCKED`) |
| **`DELETE`** | `/api/v1/friendships/{userId}/block` | JWT | Bỏ chặn người dùng (xóa bản ghi `BLOCKED`, đưa quan hệ về `NONE`) |
| **`GET`** | `/api/v1/friendships/blocked` | JWT | Xem danh sách những người dùng mình đang chặn |
| **`GET`** | `/api/v1/users/profile/{username}/qr` | Public | Tải ảnh mã QR (PNG) trỏ tới link cá nhân `https://checked.app/u/{username}` |
| **`GET`** | `/api/v1/friend-invite-links/preview` | Public | Xem trước thông tin người mời từ Invite Token (tránh click mù) |

---

## 2. PHÂN TÍCH KỸ THUẬT & GIẢI PHÁP CHI TIẾT

### 2.1 Chức Năng Chặn Người Dùng (Block / Unblock Lifecycle)
- **Khi A bấm Chặn B (`POST /api/v1/friendships/{bId}/block`)**:
  - Không cho phép tự chặn chính mình (`CANNOT_BLOCK_SELF`).
  - Kiểm tra quan hệ 2 chiều hiện có qua `friendshipRepository.findRelationBetweenUsers(aId, bId)`:
    - Nếu đã có bản ghi (dù đang là `ACCEPTED` hay `PENDING`): Cập nhật `user = A` (người chặn), `friend = B` (người bị chặn), `status = BLOCKED`.
    - Nếu chưa có: Tạo bản ghi `Friendship` mới với `user = A`, `friend = B`, `status = BLOCKED`.
  - **Dọn dẹp Cache**: Xóa cả `CacheConfig.FRIENDS_CACHE` và `CacheConfig.FEED_CACHE` của cả 2 tài khoản.
  - Bắn sự kiện realtime WebSocket để ngắt kết nối bạn bè ngay lập tức.
- **Khi A Bỏ Chặn B (`DELETE /api/v1/friendships/{bId}/block`)**:
  - Kiểm tra bản ghi quan hệ giữa A và B. Nếu `status == BLOCKED` và `f.user_id == A` (chỉ người chủ động chặn mới có quyền bỏ chặn) $\rightarrow$ Xóa bản ghi `Friendship` khỏi DB. Quan hệ trở về `NONE`.
- **Xem danh sách chặn (`GET /api/v1/friendships/blocked`)**:
  - Truy vấn các bản ghi `WHERE f.user.id = :currentUserId AND f.status = BLOCKED`. Trả về danh sách profile người bị chặn (ID, username, displayName, avatarUrl).

---

### 2.2 Gia Cố Bảo Mật & Concurrency Cho `FriendInviteLink` Cũ
Theo kết quả audit ban đầu:
1. **Chặn bypass BLOCKED**: Trong `FriendInviteLinkServiceImpl#acceptExistingOrCreate`:
   - Nếu `candidate.getStatus() == FriendshipStatus.BLOCKED` $\rightarrow$ Ném ngay lỗi `AppException(ErrorCode.USER_BLOCKED)`, không cho phép tự động chuyển thành `ACCEPTED`.
2. **Khắc phục Lost Update trên `usedCount`**:
   - Sử dụng `findByTokenAndRevokedAtIsNull(token)` với `@Lock(LockModeType.PESSIMISTIC_WRITE)` hoặc câu lệnh atomic update để tuần tự hóa các giao dịch concurrent accept khi `usedCount` tiệm cận `maxUses`.
3. **Evict `FEED_CACHE`**: Bổ sung xóa `CacheConfig.FEED_CACHE` cho cả `ownerUserId` và `currentUserId` khi accept invite link thành công.
4. **API Preview Token**: Thêm endpoint `GET /api/v1/friend-invite-links/preview?token={token}` (Public) trả về preview an toàn của người mời.

---

### 2.3 Sinh Mã QR Profile Cá Nhân (`Profile QR Code Generator`)
- **Mục tiêu**: Mỗi người dùng có một mã QR độc nhất trỏ tới link `https://checked.app/u/{username}`.
- **Cách thức thực hiện**:
  - Sử dụng thư viện `com.google.zxing:core:3.5.3` và `com.google.zxing:javase:3.5.3`.
  - Sinh mảng byte hình ảnh PNG kích thước $512 \times 512\text{px}$ với chuẩn Error Correction Level `M` (hoặc `H`).
  - Trả về trực tiếp với `Content-Type: image/png` và header `Cache-Control: public, max-age=86400` (cache 24 giờ).
  - Khách quét mã bằng camera điện thoại sẽ tự động mở trang web/app: `https://checked.app/u/{username}`.

---

### 2.4 Rate Limiting Bucket4j Chống Spam Kết Bạn
- **Cấu hình Bucket4j cho Endpoint Friend Requests**:
  - `POST /api/v1/friendships/requests`: Tối đa **15 requests / 10 phút** cho mỗi `user_id`. Ngăn chặn việc bấm spam gửi lời mời liên tục.
  - `GET /api/v1/users/profile/{username}`: Tối đa **60 requests / phút** theo `Client-IP`. Ngăn chặn bot cào danh sách tài khoản qua link profile.

---

## 3. PHÂN RÃ CÁC TASK TRIỂN KHAI (TASK BREAKDOWN)

```
# Phase 3: Block, QR Generator & Security Hardening

## Tasks
- [ ] Task 3.1: Bổ sung thư viện ZXing QR Code vào build.gradle → Verify: ./gradlew compileJava tải và biên dịch thành công.
- [ ] Task 3.2: Triển khai các phương thức Repository cho Blocked Users trong FriendshipRepository:
      - `findBlockedUsers(UUID userId)`
      - `findBlockedRelation(UUID userA, UUID userB)`
      → Verify: Viết unit test kiểm tra câu query JPQL blocked.
- [ ] Task 3.3: Triển khai Service & Endpoints cho Chức năng Chặn & Bỏ Chặn (Block / Unblock):
      - `blockUser(UUID currentUserId, UUID targetUserId)`
      - `unblockUser(UUID currentUserId, UUID targetUserId)`
      - `getBlockedUsers(UUID currentUserId)`
      - Endpoints: POST /{id}/block, DELETE /{id}/block, GET /blocked
      → Verify: Chặn bạn bè chuyển status thành BLOCKED và xóa cache feed; Bỏ chặn xóa bản ghi BLOCKED.
- [ ] Task 3.4: Triển khai Service & Endpoint Sinh Mã QR Profile (QrCodeService):
      - `generateProfileQrCode(String username)` trả về byte[] PNG 512x512
      - Endpoint: GET /api/v1/users/profile/{username}/qr (PermitAll, MediaType.IMAGE_PNG)
      → Verify: Gọi GET /profile/{username}/qr trả về 200 OK với Content-Type image/png và mảng byte hợp lệ.
- [ ] Task 3.5: Gia cố bảo mật FriendInviteLink cũ (Vá lỗ hổng bypass Block & Lost Update usedCount):
      - Kiểm tra chặn BLOCKED trong acceptExistingOrCreate
      - Dùng Pessimistic Lock và evict FEED_CACHE khi acceptByToken
      - Endpoint: GET /api/v1/friend-invite-links/preview?token=...
      → Verify: Test kịch bản A block B thì B không thể dùng link của A để kết bạn; Preview token trả về đúng thông tin.
- [ ] Task 3.6: Cấu hình Rate Limiting Bucket4j cho Friend Requests:
      - Áp dụng rate limit 15 req/10m cho POST /friendships/requests
      → Verify: Gửi quá 15 request liên tục nhận HTTP 429 TOO_MANY_REQUESTS.
- [ ] Task 3.7: Viết Unit Tests & Chạy Test Suite toàn dự án:
      - Viết unit tests cho Block/Unblock, QrCodeService, InviteLink fix
      → Verify: ./gradlew test pass 100% (BUILD SUCCESSFUL).

## Done When
- [ ] Người dùng A có thể chặn người dùng B, B không thể xem feed hay gửi lời mời cho A.
- [ ] Người dùng có thể lấy mã QR profile cá nhân dạng PNG sắc nét để chia sẻ.
- [ ] Link mời cũ (token) không còn bị lỗ hổng bypass block hay mất mát lượt dùng.
- [ ] Hệ thống chống được spam gửi lời mời nhờ Rate Limiting.
- [ ] 100% test suite chạy thành công.
```

---

## 4. MA TRẬN TEST CASES (TEST MATRIX)

| Test Case | Điều kiện đầu vào | Kết quả kỳ vọng |
| :--- | :--- | :--- |
| `blockUser_whenSelf_throwsCannotBlockSelf` | `targetUserId == currentUserId` | Ném `INVALID_REQUEST` (400) |
| `blockUser_whenAlreadyFriends_convertsToBlocked` | A và B đang là bạn bè | Cập nhật `status = BLOCKED`, evict `friends` & `feed` cache |
| `blockUser_whenNew_createsBlocked` | A và B chưa quen | Tạo mới bản ghi `BLOCKED` |
| `unblockUser_whenBlockedByMe_deletesRelation` | A đã chặn B | Xóa bản ghi `BLOCKED`, quan hệ về `NONE` |
| `unblockUser_whenNotBlockedByMe_throwsException` | B cố tình unblock A | Ném `FORBIDDEN` hoặc `INVALID_REQUEST` |
| `generateQrCode_returnsPngByteArray` | Username hợp lệ | Trả về byte[] ảnh PNG kích thước $512\times 512$ |
| `acceptInviteToken_whenUserBlocked_throwsUserBlocked` | A đã block B, B dùng link của A | Ném `USER_BLOCKED` (403), không mở khóa |
| `previewInviteToken_validToken_returnsPreview` | Token còn hiệu lực | Trả về thông tin inviter + status `ACTIVE` |

---

Bản kế hoạch Phase 3 đã hoàn thiện đầy đủ mọi khía cạnh kỹ thuật. Khi bạn sẵn sàng, chúng ta có thể tạo nhánh `feat/profile-link-friend-invitation-phase-3` và bắt đầu thực hiện!
