# Kế Hoạch Triển Khai Chi Tiết: PR #11

> **Tiêu đề PR**: `perf(db): fix-n-plus-one-and-friendship-fetch`  
> **Nhánh Git**: `perf/sql-friendship-n-plus-one` (tạo từ `main`)  
> **Mức độ ưu tiên**: 🔴 **P0 (Critical - Cải thiện trực tiếp độ trễ ứng dụng)**  
> **Phạm vi ảnh hưởng**: `friendship`, `friendship/invite`, `test`  
> **Cam kết an toàn**: ❌ **KHÔNG DEPLOY HEROKU** (Mọi thay đổi chỉ thực thi và kiểm thử tại local)

---

## 1. 🔍 Phân Tích Hiện Trạng & Nguyên Nhân Gốc (Root Cause)

### 1.1. Lỗ hổng N+1 Queries nghiêm trọng tại `getAllFriends`
- **Đoạn code hiện tại**:
  - [FriendshipRepository.java#L15-L16](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/java/com/codegym/locketclone/friendship/FriendshipRepository.java#L15-L16):
    ```java
    @Query("SELECT f FROM Friendship f WHERE (f.user.id = :userId OR f.friend.id = :userId) AND f.status = com.codegym.locketclone.friendship.FriendshipStatus.ACCEPTED")
    List<Friendship> findAllAcceptedFriends(@Param("userId") UUID userId);
    ```
  - [FriendshipServiceImpl.java#L22-L28](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/java/com/codegym/locketclone/friendship/FriendshipServiceImpl.java#L22-L28):
    ```java
    return friendshipRepository.findAllAcceptedFriends(userId)
            .stream()
            .map(friendship -> resolveFriendUser(friendship, userId))
            .map(userMapper::toFriendProfileResponse)
            .toList();
    ```
- **Cơ chế gây lỗi**:
  - Entity `Friendship` cấu hình:
    ```java
    @ManyToOne(fetch = FetchType.LAZY)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    private User friend;
    ```
  - Khi câu truy vấn `findAllAcceptedFriends` chạy, Hibernate chỉ lấy dữ liệu của bảng `friendships` (trả về danh sách proxy cho `user` và `friend`).
  - Khi luồng stream gọi `resolveFriendUser` rồi chuyển sang `userMapper.toFriendProfileResponse`:
    - Mapper truy cập `displayName`, `avatarUrl`, `username` của đối tượng `User`.
    - Do các trường này chưa được load, Hibernate tự động kích hoạt lazy loading và bắn **1 câu lệnh SELECT riêng rẽ** đến bảng `users` cho từng bản ghi bạn bè.
  - **Tác động thực tế**:
    - Với user có 50 bạn bè: Hệ thống thực hiện $1 + 50 = 51$ lượt truy vấn database.
    - Với Cloud Database Neon PostgreSQL (đặt tại AWS Singapore, mạng internet có độ trễ network round-trip $\approx 50\text{ms}$):
      $$\text{Thời gian tải} \approx 51 \times 50\text{ms} = 2.550\text{ms} \sim 3.5\text{s}$$
    - Người dùng trên ứng dụng mobile phải chờ hơn 3 giây chỉ để mở danh sách bạn bè!

### 1.2. Lãng phí CPU và RAM do thiếu `readOnly = true`
- Tại [FriendshipServiceImpl.java#L21](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/java/com/codegym/locketclone/friendship/FriendshipServiceImpl.java#L21), phương thức `getAllFriends` đang đặt annotation `@Transactional` mặc định (`readOnly = false`).
- Hibernate buộc phải tạo snapshot của toàn bộ các Entity trong `PersistenceContext` để thực hiện Dirty Checking khi hoàn tất transaction, gây lãng phí bộ nhớ Heap và chu kỳ CPU không cần thiết cho một thao tác chỉ đọc.

### 1.3. Truy vấn lặp lại tuần tự khi chấp nhận invite token
- Tại [FriendInviteLinkServiceImpl.java#L107-L109](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/java/com/codegym/locketclone/friendship/invite/FriendInviteLinkServiceImpl.java#L107-L109):
  ```java
  FriendInviteLink link = friendInviteLinkRepository.findByTokenAndRevokedAtIsNull(normalizedToken)
          .orElseGet(() -> friendInviteLinkRepository.findByToken(normalizedToken)
                  .orElseThrow(() -> new AppException(ErrorCode.INVALID_FRIEND_INVITE_TOKEN)));
  ```
  Nếu link đã bị thu hồi hoặc hết hạn, code thực hiện tới **2 truy vấn liên tiếp** vào cùng bảng `friend_invite_links`. Việc này hoàn toàn có thể rút gọn thành 1 câu `findByToken(token)` duy nhất và kiểm tra trạng thái bằng mã Java.

---

## 2. 💡 Thiết Kế Kỹ Thuật (Technical Design)

### 2.1. Eager Fetching với `JOIN FETCH` trong JPQL
Thay thế câu query trong `FriendshipRepository`:
```java
@Query("""
        SELECT f
        FROM Friendship f
        JOIN FETCH f.user
        JOIN FETCH f.friend
        WHERE (f.user.id = :userId OR f.friend.id = :userId)
          AND f.status = com.codegym.locketclone.friendship.FriendshipStatus.ACCEPTED
        """)
List<Friendship> findAllAcceptedFriends(@Param("userId") UUID userId);
```
- **Tại sao `JOIN FETCH` an toàn ở đây?**
  - Quan hệ giữa `Friendship` với `user` và `friend` là `@ManyToOne` (to-one relationship).
  - Không tồn tại rủi ro Cartesian Product (bùng nổ dòng) như với quan hệ `@OneToMany` (collection).
  - SQL sinh ra sẽ là một lệnh `INNER JOIN` duy nhất giữa bảng `friendships` và bảng `users` (2 lần alias).
  - Toàn bộ dữ liệu của `User` được nạp sẵn vào Hibernate First-Level Cache ngay trong lần đọc đầu tiên.
  - Số lượng truy vấn giảm từ $51 \rightarrow 1$. Thời gian phản hồi giảm từ $\approx 3.000\text{ms} \rightarrow \approx 60\text{ms}$ (**giảm 98% thời gian chờ**).

### 2.2. Bật `readOnly = true` cho Transaction
Cập nhật annotation tại Service:
```java
@Override
@Transactional(readOnly = true)
public List<FriendProfileResponse> getAllFriends(UUID userId) { ... }
```
- Tắt cơ chế Dirty Checking của Session Hibernate.
- Giúp connection pool và JDBC Driver định tuyến tối ưu (nếu có read-replica).

### 2.3. Rút gọn truy vấn Invite Token
Trong `FriendInviteLinkServiceImpl#acceptByToken`:
```java
String normalizedToken = token.trim();
FriendInviteLink link = friendInviteLinkRepository.findByToken(normalizedToken)
        .orElseThrow(() -> new AppException(ErrorCode.INVALID_FRIEND_INVITE_TOKEN));

validateAcceptableLink(link);
```
- Gom 2 round-trip thành đúng 1 round-trip.
- Phương thức `validateAcceptableLink(link)` đã có sẵn logic kiểm tra `link.getRevokedAt() != null` và ném `ErrorCode.FRIEND_INVITE_LINK_REVOKED`, đảm bảo tính toàn vẹn 100%.

---

## 3. 📂 Chi Tiết Từng File Thay Đổi & Code Diffs

### File 1: `FriendshipRepository.java`
- **Đường dẫn**: [src/main/java/com/codegym/locketclone/friendship/FriendshipRepository.java](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/java/com/codegym/locketclone/friendship/FriendshipRepository.java)
- **Thay đổi**:
```diff
-    @Query("SELECT f FROM Friendship f WHERE (f.user.id = :userId OR f.friend.id = :userId) AND f.status = com.codegym.locketclone.friendship.FriendshipStatus.ACCEPTED")
-    List<Friendship> findAllAcceptedFriends(@Param("userId") UUID userId);
+    @Query("""
+            SELECT f
+            FROM Friendship f
+            JOIN FETCH f.user
+            JOIN FETCH f.friend
+            WHERE (f.user.id = :userId OR f.friend.id = :userId)
+              AND f.status = com.codegym.locketclone.friendship.FriendshipStatus.ACCEPTED
+            """)
+    List<Friendship> findAllAcceptedFriends(@Param("userId") UUID userId);
```

### File 2: `FriendshipServiceImpl.java`
- **Đường dẫn**: [src/main/java/com/codegym/locketclone/friendship/FriendshipServiceImpl.java](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/java/com/codegym/locketclone/friendship/FriendshipServiceImpl.java)
- **Thay đổi**:
```diff
     @Override
-    @Transactional
+    @Transactional(readOnly = true)
     public List<FriendProfileResponse> getAllFriends(UUID userId) {
         return friendshipRepository.findAllAcceptedFriends(userId)
```

### File 3: `FriendInviteLinkServiceImpl.java`
- **Đường dẫn**: [src/main/java/com/codegym/locketclone/friendship/invite/FriendInviteLinkServiceImpl.java](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/java/com/codegym/locketclone/friendship/invite/FriendInviteLinkServiceImpl.java)
- **Thay đổi**:
```diff
         String normalizedToken = token.trim();
-        FriendInviteLink link = friendInviteLinkRepository.findByTokenAndRevokedAtIsNull(normalizedToken)
-                .orElseGet(() -> friendInviteLinkRepository.findByToken(normalizedToken)
-                        .orElseThrow(() -> new AppException(ErrorCode.INVALID_FRIEND_INVITE_TOKEN)));
+        FriendInviteLink link = friendInviteLinkRepository.findByToken(normalizedToken)
+                .orElseThrow(() -> new AppException(ErrorCode.INVALID_FRIEND_INVITE_TOKEN));
 
         validateAcceptableLink(link);
```

### File 4: `FriendInviteLinkServiceImplTest.java`
- **Đường dẫn**: [src/test/java/com/codegym/locketclone/friendship/invite/FriendInviteLinkServiceImplTest.java](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/test/java/com/codegym/locketclone/friendship/invite/FriendInviteLinkServiceImplTest.java)
- **Thay đổi**: Cập nhật mock expectations từ `findByTokenAndRevokedAtIsNull` sang `findByToken` cho các test case của `acceptByToken`.

---

## 4. 🛠️ Quy Trình Triển Khai Từng Bước (Step-by-Step Execution)

1. **Bước 1**: Tạo nhánh tính năng mới từ `main`:
   ```bash
   git checkout main && git pull origin main
   git checkout -b perf/sql-friendship-n-plus-one
   ```
2. **Bước 2**: Chỉnh sửa [FriendshipRepository.java](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/java/com/codegym/locketclone/friendship/FriendshipRepository.java) bổ sung `JOIN FETCH f.user JOIN FETCH f.friend`.
3. **Bước 3**: Chỉnh sửa [FriendshipServiceImpl.java](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/java/com/codegym/locketclone/friendship/FriendshipServiceImpl.java) đặt `@Transactional(readOnly = true)`.
4. **Bước 4**: Chỉnh sửa [FriendInviteLinkServiceImpl.java](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/java/com/codegym/locketclone/friendship/invite/FriendInviteLinkServiceImpl.java) rút gọn câu truy vấn token.
5. **Bước 5**: Chỉnh sửa test suites trong [FriendInviteLinkServiceImplTest.java](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/test/java/com/codegym/locketclone/friendship/invite/FriendInviteLinkServiceImplTest.java) và [FriendshipServiceImplTest.java](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/test/java/com/codegym/locketclone/friendship/FriendshipServiceImplTest.java).
6. **Bước 6**: Chạy toàn bộ test suite để đảm bảo không có bất kỳ regression nào:
   ```bash
   ./gradlew clean test bootJar
   ```
7. **Bước 7**: Commit theo chuẩn Conventional Commits và đẩy nhánh lên GitHub:
   ```bash
   git add .
   git commit -m "perf(db): fix N+1 query in friendships and optimize invite token lookup"
   git push -u origin perf/sql-friendship-n-plus-one
   ```

---

## 5. ✅ Tiêu Chí Nghiệm Thu & Test Checklist

- [ ] Lệnh truy vấn `findAllAcceptedFriends` chứa đầy đủ mệnh đề `JOIN FETCH f.user JOIN FETCH f.friend`.
- [ ] Khi chạy `GET /api/v1/friendships`, Hibernate chỉ sinh ra **1 câu lệnh SELECT duy nhất** kết hợp `INNER JOIN`.
- [ ] Thông tin trả về của bạn bè (`id`, `username`, `displayName`, `avatarUrl`) không bị rỗng và không bị ảnh hưởng.
- [ ] Phương thức `acceptByToken` hoạt động chính xác với cả token hợp lệ, token hết hạn, và token bị thu hồi.
- [ ] Toàn bộ 158/158 bài kiểm thử tự động (Unit Test & Integration Test) đều **PASS 100%**.
- [ ] Bản build `bootJar` thành công không có bất kỳ warning hay lỗi biên dịch nào.
- [ ] **Tuân thủ quy định**: Tuyệt đối không thực hiện bất kỳ lệnh deploy nào lên Heroku.
