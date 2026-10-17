# 🤝 Kế Hoạch Triển Khai Chi Tiết: Phase 4
## Bạn Chung (Mutual Friends), Quản Lý Lời Mời Đã Gửi, Giới Hạn Hạn Ngạch Bạn Bè & Social Share Page

> **Tài liệu tham chiếu**: [`docs/IMPLEMENTATION_PLAN_PROFILE_FRIEND_INVITATION.md`](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/docs/IMPLEMENTATION_PLAN_PROFILE_FRIEND_INVITATION.md)  
> **Nhánh Git dự kiến**: `feat/profile-link-friend-invitation-phase-4` (tạo từ `main`)  
> **Mức độ ưu tiên**: 🟢 **P1 (Hoàn thiện trải nghiệm mạng xã hội, thúc đẩy chuyển đổi kết bạn & chuẩn hóa sản phẩm)**  
> **Mục tiêu**:  
> 1. **Hiển thị Bạn Chung (Mutual Friends)**: Tính toán số lượng và danh sách bạn chung khi xem link profile để tăng độ tin cậy và kích thích gửi lời mời kết bạn.  
> 2. **Quản lý lời mời đã gửi (Sent Requests)**: Cho phép người dùng theo dõi và thu hồi danh sách các lời mời mình đã phát đi.  
> 3. **Kiểm soát hạn ngạch bạn bè (Friend Quota Enforcement)**: Áp dụng quy tắc mạng xã hội thân thiết (Close Friends) với trần tối đa 20 bạn cho tài khoản thường và 100 bạn cho Gold Member.  
> 4. **Thao tác hàng loạt (Batch Operations)**: Hỗ trợ "Chấp nhận tất cả" (`accept-all`) và "Từ chối tất cả" (`decline-all`) cho người dùng nhận nhiều lời mời cùng lúc.  
> 5. **Trang OpenGraph Preview (`GET /u/{username}`)**: Tối ưu hiển thị rich card khi chia sẻ link profile lên Zalo, Messenger, iMessage, Telegram.

---

## 1. MA TRẬN API ENDPOINTS PHASE 4

| Method | Endpoint | Quyền hạn | Mục đích nghiệp vụ |
| :--- | :--- | :---: | :--- |
| **`GET`** | `/api/v1/friendships/requests/sent` | JWT | Lấy danh sách các lời mời kết bạn mình đã gửi đang chờ duyệt |
| **`GET`** | `/api/v1/friendships/requests/sent/count` | JWT | Lấy số lượng lời mời mình đã gửi đi đang trong trạng thái chờ |
| **`GET`** | `/api/v1/friendships/{targetUserId}/mutual` | JWT | Xem danh sách đầy đủ tất cả bạn bè chung giữa mình và người dùng khác |
| **`GET`** | `/api/v1/users/profile/{username}` | Public/JWT | Mở rộng bổ sung `mutualFriendsCount` và `mutualFriendsPreview` (tối đa 3 bạn chung) |
| **`PUT`** | `/api/v1/friendships/requests/accept-all` | JWT | Chấp nhận tất cả lời mời kết bạn đang chờ cùng lúc (1-click accept all) |
| **`PUT`** | `/api/v1/friendships/requests/decline-all` | JWT | Từ chối và dọn sạch tất cả lời mời kết bạn đang chờ |
| **`GET`** | `/u/{username}` | Public | Trả về trang HTML chứa thẻ OpenGraph (meta tag) và hỗ trợ Deep Link `checked://u/{username}` |

---

## 2. PHÂN TÍCH KỸ THUẬT & KIẾN TRÚC THỰC HIỆN

### 2.1 Thuật Toán Tính Bạn Chung (Mutual Friends Engine)
Bảng `friendships` sử dụng cấu trúc quan hệ 2 chiều với ràng buộc `uk_friendships_bidirectional` trên `(LEAST(user_id, friend_id), GREATEST(user_id, friend_id))`.  
Do đó, tập bạn bè của một người dùng $U$ là:
$$\text{Friends}(U) = \{ f.\text{friend\_id} \mid f.\text{user\_id} = U \land f.\text{status} = \text{'ACCEPTED'} \} \cup \{ f.\text{user\_id} \mid f.\text{friend\_id} = U \land f.\text{status} = \text{'ACCEPTED'} \}$$

Tập bạn chung giữa người dùng $A$ và $B$ là giao điểm:
$$\text{MutualFriends}(A, B) = \text{Friends}(A) \cap \text{Friends}(B)$$

#### Câu lệnh JPQL tối ưu:
```sql
SELECT u FROM User u
WHERE u.id IN (
    SELECT CASE WHEN f1.user.id = :userA THEN f1.friend.id ELSE f1.user.id END
    FROM Friendship f1
    WHERE (f1.user.id = :userA OR f1.friend.id = :userA)
      AND f1.status = com.codegym.locketclone.friendship.FriendshipStatus.ACCEPTED
)
AND u.id IN (
    SELECT CASE WHEN f2.user.id = :userB THEN f2.friend.id ELSE f2.user.id END
    FROM Friendship f2
    WHERE (f2.user.id = :userB OR f2.friend.id = :userB)
      AND f2.status = com.codegym.locketclone.friendship.FriendshipStatus.ACCEPTED
)
ORDER BY u.displayName ASC
```
- **Preview 3 bạn chung**: Trong API `GET /api/v1/users/profile/{username}`, khi có token đăng nhập (`currentUserId != null` và `currentUserId != targetUser.id`), backend sẽ chạy query đếm (`countMutualFriends`) và lấy tối đa 3 bạn chung đầu tiên để FE hiển thị ngay:  
  *“Có 5 bạn chung: @tuan, @linh, @nam và 2 người khác”*.

---

### 2.2 Quản Lý Lời Mời Đã Gửi (Sent Requests)
- `FriendshipRepository` đã có sẵn câu query:
  ```java
  List<Friendship> findPendingSentRequests(@Param("userId") UUID userId);
  ```
- **DTO trả về** (`SentFriendRequestResponse`):
  ```json
  {
    "friendshipId": "c928421b-4172-4d2a-89aa-55f72cf89823",
    "targetUser": {
      "id": "18f8e07a-2489-408a-b8b8-07e324c4786d",
      "username": "anh_tuan",
      "displayName": "Anh Tuan",
      "avatarUrl": "https://storage.checked.app/avatars/tuan.jpg",
      "isGoldMember": false
    },
    "createdAt": "2026-10-17T11:45:00"
  }
  ```
- Người dùng có thể nhấn nút "Hủy" trên danh sách này để gọi endpoint `DELETE /api/v1/friendships/requests/{friendshipId}/cancel` đã có từ Phase 2.

---

### 2.3 Cơ Chế Giới Hạn Bạn Bè (Friend Quota Enforcement)
Checked được định vị là mạng xã hội chia sẻ ảnh cho bạn bè thân thiết (Close Friends):
1. **Quy định hạn ngạch**:
   - Tài khoản thường (`isGoldMember = false`): Tối đa **20 bạn bè** (`MAX_STANDARD_FRIENDS = 20`).
   - Tài khoản Gold Member (`isGoldMember = true`): Tối đa **100 bạn bè** (`MAX_GOLD_FRIENDS = 100`).
2. **Kiểm tra khi gửi lời mời (`sendFriendRequest`)**:
   - Nếu `currentUser` đã đạt trần bạn bè $\rightarrow$ Ném `AppException(ErrorCode.MAX_FRIENDS_REACHED)`.
   - Nếu `targetUser` đã đạt trần bạn bè $\rightarrow$ Ném `AppException(ErrorCode.TARGET_USER_MAX_FRIENDS_REACHED)`.
3. **Kiểm tra khi chấp nhận lời mời (`acceptFriendRequest` & `acceptByToken`)**:
   - Đảm bảo cả người duyệt và người gửi đều chưa vượt quá số lượng bạn bè cho phép trước khi chuyển trạng thái sang `ACCEPTED`.

---

### 2.4 Thao Tác Lời Mời Hàng Loạt (Batch Operations)
- **`accept-all`**:
  - Lấy tất cả các bản ghi `PENDING` gửi đến `currentUserId`.
  - Kiểm tra quota bạn bè còn trống của `currentUserId`.
  - Chuyển trạng thái sang `ACCEPTED`, lưu CSDL, đồng loạt evict cache `friends` & `feed` và gửi realtime WebSocket cho từng người gửi.
- **`decline-all`**:
  - Xóa tất cả các bản ghi `PENDING` gửi đến `currentUserId`.

---

### 2.5 OpenGraph Dynamic Share Page (`GET /u/{username}`)
- Khi người dùng gửi link `https://checked.app/u/{username}` qua tin nhắn Zalo / iMessage / Messenger / Facebook, các bot crawler (Facebook External Hit, ZaloBot, Twitterbot) sẽ cào trang HTML này.
- Backend trả về HTML nhẹ có chứa các meta tags chuẩn SEO & Social:
  ```html
  <!DOCTYPE html>
  <html lang="vi">
  <head>
      <meta charset="UTF-8">
      <title>Khanh Nguyen (@khanhnkq) trên Checked</title>
      <meta property="og:title" content="Kết bạn với Khanh Nguyen trên Checked" />
      <meta property="og:description" content="Quét mã QR hoặc nhấn vào liên kết để kết bạn với @khanhnkq và cùng chia sẻ khoảnh khắc lên màn hình chính." />
      <meta property="og:image" content="https://storage.checked.app/avatars/khanh.jpg" />
      <meta property="og:url" content="https://checked.app/u/khanhnkq" />
      <meta name="twitter:card" content="summary_large_image" />
      <!-- Smart App Banner / Deep Link Redirection -->
      <meta http-equiv="refresh" content="0; url=checked://u/khanhnkq" />
  </head>
  <body>
      <p>Đang chuyển hướng tới ứng dụng Checked...</p>
      <script>
          window.location.href = "checked://u/khanhnkq";
          setTimeout(function() {
              window.location.href = "https://checked.app/download";
          }, 1500);
      </script>
  </body>
  </html>
  ```

---

## 3. PHÂN RÃ CÁC TASK TRIỂN KHAI (TASK BREAKDOWN)

```
# Phase 4: Mutual Friends, Sent Requests, Quota & Social Share Page

## Tasks
- [x] Task 4.1: Bổ sung ErrorCode mới vào ErrorCode.java:
      - MAX_FRIENDS_REACHED (400, "Bạn đã đạt số lượng bạn bè tối đa cho phép của gói tài khoản")
      - TARGET_USER_MAX_FRIENDS_REACHED (400, "Người dùng này đã đạt giới hạn bạn bè tối đa")
- [x] Task 4.2: Triển khai Quản lý Lời Mời Đã Gửi (Sent Requests):
      - Tạo DTO `SentFriendRequestResponse.java` và `SentFriendRequestCountResponse.java`
      - Thêm `getPendingSentRequests(UUID currentUserId)` và `getPendingSentCount(UUID currentUserId)` vào FriendshipService
      - Thêm endpoints GET /api/v1/friendships/requests/sent và GET /api/v1/friendships/requests/sent/count vào FriendshipController
- [x] Task 4.3: Triển khai Thuật toán Bạn Chung (Mutual Friends Engine):
      - Bổ sung query `countMutualFriends(UUID userA, UUID userB)` và `findMutualFriends(UUID userA, UUID userB)` trong FriendshipRepository
      - Thêm `getMutualFriends(UUID currentUserId, UUID targetUserId)` vào FriendshipService
      - Cập nhật UserProfileWithRelationResponse bổ sung `mutualFriendsCount` và `mutualFriendsPreview`
      - Thêm endpoint GET /api/v1/friendships/{targetUserId}/mutual
- [x] Task 4.4: Kiểm soát Hạn Ngạch Bạn Bè (Friend Quota Enforcement):
      - Cấu hình hạn ngạch: Standard (20), Gold (100)
      - Ràng buộc trần bạn bè trong `sendFriendRequest`, `acceptFriendRequest`, và `acceptByToken`
- [x] Task 4.5: Triển khai Thao tác Hàng loạt (Batch Operations):
      - Thêm `acceptAllPendingRequests(UUID currentUserId)` và `declineAllPendingRequests(UUID currentUserId)`
      - Endpoints PUT /api/v1/friendships/requests/accept-all và PUT /api/v1/friendships/requests/decline-all
- [x] Task 4.6: Xây dựng Endpoint OpenGraph Social Share Page (ShareController):
      - Endpoint GET /u/{username} trả về HTML chứa OpenGraph meta tags và kịch bản Deep Link fallback
      - PermitAll trong SecurityConfig
- [x] Task 4.7: Viết Unit Tests & Chạy Toàn Bộ Test Suite:
      - Unit tests cho Mutual Friends, Sent Requests, Quota limit, Batch operations và ShareController
      - Chạy ./gradlew test đảm bảo 100% test pass (326/326 tests passed)

## Done When
- [x] Người dùng xem được danh sách và số lượng lời mời mình đã gửi đi để chủ động thu hồi.
- [x] Profile cá nhân hiển thị được số bạn chung và preview bạn chung tăng uy tín kết bạn.
- [x] Người dùng có thể xem toàn bộ danh sách bạn chung với bất kỳ người nào.
- [x] Hệ thống chặn kết bạn khi chạm giới hạn 20 bạn (thường) / 100 bạn (Gold).
- [x] Người dùng có thể duyệt hoặc từ chối toàn bộ lời mời cùng lúc.
- [x] Khi gửi link https://checked.app/u/{username} lên Zalo/Messenger/iMessage, card hiển thị hình ảnh và tên đẹp mắt.
- [x] Toàn bộ test suite pass 100% không có hồi quy.
```

---

## 4. MA TRẬN TEST CASES (TEST MATRIX)

| Test Case | Điều kiện đầu vào | Kết quả kỳ vọng |
| :--- | :--- | :--- |
| `getPendingSentRequests_returnsListWithTargetUsers` | Có 2 lời mời do mình gửi | Trả về 2 item chứa thông tin `targetUser` |
| `getMutualFriends_whenHaveSharedFriends_returnsList` | A và B cùng kết bạn với C và D | Trả về danh sách C và D |
| `getUserProfile_withLogin_includesMutualFriendsInfo` | User A xem profile User B | Trả về `mutualFriendsCount = 2` và `mutualFriendsPreview` |
| `sendFriendRequest_whenSenderExceedsQuota_throwsMaxFriendsReached` | User thường đã có 20 bạn | Ném `MAX_FRIENDS_REACHED` (400) |
| `sendFriendRequest_whenTargetExceedsQuota_throwsTargetMaxFriends` | Target đã có đủ 20 bạn | Ném `TARGET_USER_MAX_FRIENDS_REACHED` (400) |
| `acceptAllPendingRequests_acceptsAllAndEvictsCache` | Nhận được 3 lời mời | Cả 3 chuyển thành `ACCEPTED`, cache feed & friends được xóa sạch |
| `declineAllPendingRequests_deletesAllPending` | Nhận được 3 lời mời | Toàn bộ 3 bản ghi bị xóa khỏi CSDL |
| `getSocialSharePage_validUser_returnsHtmlWithOgTags` | Username hợp lệ | Trả về HTML chứa `og:title`, `og:image` và script deep link |
| `getSocialSharePage_userNotFound_returns404Html` | Username không tồn tại | Trả về 404 hoặc trang báo người dùng không khả dụng |

---

## 5. ĐỀ XUẤT NHÁNH GIT & TRÌNH TỰ MERGE
- **Tạo nhánh**: `git checkout -b feat/profile-link-friend-invitation-phase-4` (từ `main`)
- **Triển khai tuần tự theo 7 Tasks trên**
- **Xác thực toàn bộ bằng `./gradlew test`**
- **Commit và merge local vào `main`**:
  ```bash
  git checkout main
  git merge feat/profile-link-friend-invitation-phase-4
  git branch -d feat/profile-link-friend-invitation-phase-4
  ```
