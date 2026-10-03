# Danh Sách Sai Sót, Lỗ Hổng & Hạng Mục Cần Cải Thiện Trong Dự Án

> **Tài liệu**: Tổng hợp chi tiết các sai sót, hạn chế, nguy cơ bảo mật, điểm nghẽn hiệu năng và nợ kỹ thuật (Technical Debt) trong dự án `checked-backend` (`locket-clone`).  
> **Phân loại**: Đã chia theo mức độ nghiêm trọng (🔴 Nghiêm trọng, 🟡 Trung bình, 🟢 Nhẹ) kèm vị trí file, phân tích nguyên nhân và giải pháp khắc phục cụ thể.

---

## BẢNG TỔNG HỢP NHANH

| STT | Phân loại | Hạng mục | Vị trí file | Mức độ |
| :---: | :--- | :--- | :--- | :---: |
| 1 | **Hiệu năng** | Vòng lặp 24 truy vấn SQL trong `getYearlyCashflowSummary` | `ExpenseServiceImpl.java#L348-L369` | 🔴 **Nghiêm trọng** |
| 2 | **Bảo mật** | Nguy cơ Brute-force mã OTP (Thiếu Rate Limiting) | `AuthServiceImpl.java#L67-L89` | 🔴 **Nghiêm trọng** |
| 3 | **Kiểm thử** | Flyway Migration bị tắt hoàn toàn trong Test Suite | `src/test/resources/application.yml` | 🔴 **Nghiêm trọng** |
| 4 | **Kiến trúc** | Controller phụ thuộc trực tiếp vào Cloudinary | `UserController.java#L29, L74` | 🟡 **Trung bình** |
| 5 | **Tài nguyên** | Rò rỉ file trên Cloudinary (Orphaned Media Files) | `PhotoServiceImpl.java` & `UserController.java` | 🟡 **Trung bình** |
| 6 | **Bảo mật** | CORS cấu hình cứng `localhost` (Chặn môi trường thật) | `CorsConfig.java#L17` | 🟡 **Trung bình** |
| 7 | **Dữ liệu** | Thiếu ràng buộc chống trùng lặp kết bạn 2 chiều | `Friendship.java` & `V7__alter_database.sql` | 🟡 **Trung bình** |
| 8 | **Dữ liệu** | Cột `avatar_url` có độ dài quá ngắn (`VARCHAR(255)`) | `User.java#L40` | 🟡 **Trung bình** |
| 9 | **Hiệu năng** | DB hit trên từng request qua JWT (Thiếu Caching) | `JwtAuthenticationFilter.java#L44` | 🟡 **Trung bình** |
| 10 | **Hiệu năng** | Gửi email SMTP đồng bộ gây block thread HTTP | `AuthServiceImpl.java#L55` | 🟡 **Trung bình** |
| 11 | **Codebase** | Phương thức rỗng không triển khai nghiệp vụ | `FriendshipServiceImpl.java#L21-L28` | 🟢 **Nhẹ** |
| 12 | **Codebase** | Comment rác AI sót lại trong entity | `Photo.java#L24` | 🟢 **Nhẹ** |
| 13 | **Codebase** | Dead Code & Các Class/DTO mồ côi | `auth/dto`, `user/dto`, `message/` | 🟢 **Nhẹ** |
| 14 | **API Design** | Response lỗi validation trả về chuỗi String thô | `GlobalExceptionHandler.java#L41` | 🟢 **Nhẹ** |
| 15 | **Codebase** | Chưa tận dụng MapStruct cho `PhotoMapper` | `PhotoMapper.java` | 🟢 **Nhẹ** |

---

## CHI TIẾT TỪNG VẤN ĐỀ & GIẢI PHÁP KHẮC PHỤC

---

### 🔴 NHÓM 1: CÁC VẤN ĐỀ NGHIÊM TRỌNG (HIGH PRIORITY)

#### 1. Vòng lặp 24 truy vấn SQL trong `getYearlyCashflowSummary`
- **Vị trí**: [`src/main/java/com/codegym/locketclone/expense/ExpenseServiceImpl.java`](file:///workspaces/checked-backend/src/main/java/com/codegym/locketclone/expense/ExpenseServiceImpl.java#L348-L369)
- **Hiện trạng mã nguồn**:
  ```java
  for (int month = 1; month <= 12; month++) {
      YearMonth ym = YearMonth.of(year, month);
      MonthRange monthRange = monthRange(ym);

      BigDecimal income = safeAmount(photoRepository.sumTransactionAmountBySenderAndMonth(
              userId, PhotoStatus.DELETED, TransactionType.INCOME, monthRange.fromDate(), monthRange.toDate()
      ));
      BigDecimal expense = safeAmount(photoRepository.sumTransactionAmountBySenderAndMonth(
              userId, PhotoStatus.DELETED, TransactionType.EXPENSE, monthRange.fromDate(), monthRange.toDate()
      ));
      // ...
  }
  ```
- **Tác động tiêu cực**:
  - Mỗi khi client gọi xem báo cáo dòng tiền cả năm, backend bắn liên tiếp **24 câu truy vấn SQL độc lập** về PostgreSQL.
  - Khi lượng người dùng tăng, API này sẽ gây nghẽn kết nối HikariCP pool và làm quá tải CPU database.
- **Giải pháp khắc phục**:
  - Viết 1 câu query JPQL hoặc Native SQL duy nhất gom nhóm theo tháng:
    ```sql
    SELECT 
        EXTRACT(MONTH FROM COALESCE(p.occurred_at, p.taken_at, p.created_at)) AS m,
        p.transaction_type,
        SUM(p.amount)
    FROM photos p
    WHERE p.sender_id = :userId 
      AND p.status <> 'DELETED'
      AND COALESCE(p.occurred_at, p.taken_at, p.created_at) >= :startOfYear
      AND COALESCE(p.occurred_at, p.taken_at, p.created_at) < :endOfYear
    GROUP BY m, p.transaction_type;
    ```
  - Thay vì 24 query, giảm xuống còn **đúng 1 query duy nhất**.

---

#### 2. Lỗ hổng Brute-force mã xác thực OTP (Thiếu Rate Limiting)
- **Vị trí**: [`src/main/java/com/codegym/locketclone/auth/AuthServiceImpl.java`](file:///workspaces/checked-backend/src/main/java/com/codegym/locketclone/auth/AuthServiceImpl.java#L67-L89) & [`AuthController.java`](file:///workspaces/checked-backend/src/main/java/com/codegym/locketclone/auth/AuthController.java#L29-L31)
- **Hiện trạng mã nguồn**:
  - Endpoint `POST /api/v1/auth/verify` nhận `email` và `otp`.
  - Service chỉ so sánh `user.getOtpCode().equals(otp)` và kiểm tra `otpExpiresAt`.
  - Hoàn toàn **không đếm số lần nhập sai** và **không có rate limit**.
- **Tác động tiêu cực**:
  - Mã OTP chỉ gồm 6 chữ số (từ `000000` đến `999999`, tổng 1.000.000 khả năng).
  - Với thời hạn 5 phút, kẻ tấn công có thể dùng bot chạy đa luồng thử vét cạn hàng chục nghìn mã/giây để chiếm quyền tài khoản đang đăng ký.
- **Giải pháp khắc phục**:
  1. Thêm cột `otp_failed_attempts INTEGER DEFAULT 0` vào bảng `users`.
  2. Mỗi lần nhập sai: tăng biến đếm lên 1. Nếu `failed_attempts >= 5`: hủy mã OTP ngay lập tức (`setOtpCode(null)`), buộc người dùng phải gửi lại mã mới.
  3. Cài đặt Rate Limiting (bằng thư viện Bucket4j hoặc Redis) giới hạn tối đa 5 request verify/phút trên mỗi IP/Email.

---

#### 3. Flyway Migration bị vô hiệu hóa hoàn toàn trong Test Suite
- **Vị trí**: [`src/test/resources/application.yml`](file:///workspaces/checked-backend/src/test/resources/application.yml#L13-L14)
- **Hiện trạng mã nguồn**:
  ```yaml
  flyway:
    enabled: false
  jpa:
    hibernate:
      ddl-auto: create-drop
  ```
- **Tác động tiêu cực**:
  - Toàn bộ 14 file SQL migration từ [`V1`](file:///workspaces/checked-backend/src/main/resources/db/migration/V1__init_database.sql) đến [`V14`](file:///workspaces/checked-backend/src/main/resources/db/migration/V14__add_savings_goals.sql) **chưa từng được kiểm chứng thực tế** trong unit test hay trên CI pipeline của GitHub Actions.
  - Hibernate tự tạo bảng theo entity (`create-drop`), che giấu hoàn toàn các sai khác giữa Entity Java và cú pháp SQL của Postgres (ví dụ: sai tên cột, sai kiểu dữ liệu, lỗi chính tả PostgreSQL).
  - Lệnh test trên CI vẫn báo xanh (Pass), nhưng khi deploy lên môi trường Production thực tế sẽ bị crash nếu SQL migration bị lỗi.
- **Giải pháp khắc phục**:
  - Bổ sung cấu hình kiểm thử tích hợp sử dụng **Testcontainers PostgreSQL**:
    - Khởi chạy container PostgreSQL thật trong test.
    - Bật `flyway.enabled: true` để chạy toàn bộ chuỗi migration thật trước khi chạy test case.

---

### 🟡 NHÓM 2: CÁC VẤN ĐỀ TRUNG BÌNH (MEDIUM PRIORITY)

#### 4. Vi phạm Separation of Concerns trong `UserController`
- **Vị trí**: [`src/main/java/com/codegym/locketclone/user/UserController.java`](file:///workspaces/checked-backend/src/main/java/com/codegym/locketclone/user/UserController.java#L29) & dòng 74.
- **Vấn đề**:
  - `UserController` tiêm trực tiếp `CloudinaryService` và tự xử lý upload file ảnh:
    ```java
    uploadedImage = cloudinaryService.uploadImage(file);
    UpdatePersonalInfoRequest request = new UpdatePersonalInfoRequest(null, null, null, uploadedImage.secureUrl());
    return ResponseEntity.ok(userService.updatePersonalInfo(currentUser.getId(), request));
    ```
  - Controller làm thay nhiệm vụ của Service; không thể tái sử dụng logic cập nhật avatar này ở nơi khác; gây khó khăn khi viết Unit Test cho Controller.
- **Giải pháp khắc phục**:
  - Chuyển toàn bộ logic xử lý upload file avatar vào trong `UserServiceImpl.updateAvatar(UUID userId, MultipartFile file)`.
  - Tầng Service sẽ gọi tới tầng trừu tượng `StorageService`.

---

#### 5. Rò rỉ tài nguyên Storage (Orphaned Media Files)
- **Vị trí**: Toàn bộ luồng upload ảnh trong `PhotoServiceImpl.java` và `UserController.java`.
- **Vấn đề**:
  - Khi user upload avatar mới: avatar cũ vẫn nằm nguyên trên Cloudinary.
  - Khi user xóa giao dịch/ảnh (`PhotoStatus.DELETED`): bản ghi trong DB bị soft-delete nhưng file ảnh và thumbnail trên Cloudinary không bao giờ bị xóa.
- **Tác động**:
  - Tốn dung lượng lưu trữ vô ích theo thời gian.
  - Nguy cơ lộ lọt dữ liệu nhạy cảm cũ nếu người dùng muốn xóa vĩnh viễn hình ảnh hóa đơn/chi tiêu.
- **Giải pháp khắc phục**:
  - Khai báo phương thức `void deleteFile(String key)` trong tầng `StorageService`.
  - Khi update avatar: lấy `avatarUrl` cũ, trích xuất key và gửi lệnh xóa trên storage.
  - Cung cấp cơ chế hard-delete hoặc cronjob định kỳ dọn các ảnh có status `DELETED` quá 30 ngày.

---

#### 6. CORS cấu hình cứng `localhost` (Chặn môi trường thực tế)
- **Vị trí**: [`src/main/java/com/codegym/locketclone/common/config/CorsConfig.java`](file:///workspaces/checked-backend/src/main/java/com/codegym/locketclone/common/config/CorsConfig.java#L17)
- **Vấn đề**:
  ```java
  configuration.setAllowedOrigins(Arrays.asList("http://localhost:5173", "http://localhost:3000"));
  ```
  - Khi ứng dụng được deploy lên máy chủ production hoặc client truy cập từ domain khác (mobile app production, domain web thật), Spring Security sẽ chặn toàn bộ các request CORS preflight OPTIONS.
- **Giải pháp khắc phục**:
  - Đưa danh sách origin vào file `application.yml`:
    ```yaml
    app:
      cors:
        allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:5173,http://localhost:3000}
    ```
  - Tách chuỗi theo dấu phẩy để load động trong `CorsConfig`.

---

#### 7. Thiếu ràng buộc chống trùng lặp kết bạn 2 chiều
- **Vị trí**: [`src/main/java/com/codegym/locketclone/friendship/Friendship.java`](file:///workspaces/checked-backend/src/main/java/com/codegym/locketclone/friendship/Friendship.java#L17)
- **Vấn đề**:
  - Ràng buộc duy nhất hiện tại:
    ```java
    @Table(name = "friendships", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "friend_id"}))
    ```
  - Ràng buộc này chỉ chặn trùng `(A, B)` chứ **không chặn được cặp ngược lại `(B, A)`**.
  - Nếu xảy ra race condition khi cả A và B cùng accept link mời của nhau cùng 1 thời điểm, DB sẽ tồn tại đồng thời cả 2 dòng: `(A, B)` và `(B, A)`.
- **Giải pháp khắc phục**:
  - Tạo chỉ mục duy nhất vô hướng trong PostgreSQL qua Migration mới:
    ```sql
    CREATE UNIQUE INDEX uk_friendships_bidirectional 
    ON friendships (LEAST(user_id, friend_id), GREATEST(user_id, friend_id));
    ```

---

#### 8. Độ dài cột `avatar_url` quá ngắn (`VARCHAR(255)`)
- **Vị trí**: [`src/main/java/com/codegym/locketclone/user/User.java`](file:///workspaces/checked-backend/src/main/java/com/codegym/locketclone/user/User.java#L40) và [`V7__alter_database.sql`](file:///workspaces/checked-backend/src/main/resources/db/migration/V7__alter_database.sql#L37)
- **Vấn đề**:
  - Cột `avatar_url` được set cứng `length = 255`.
  - Trong khi đó, các URL từ S3 presigned, URL kèm token xác thực hoặc đường dẫn CDN phức tạp rất dễ vượt quá 255 ký tự.
  - Khi lưu sẽ gây ra lỗi `DataTruncationException` (HTTP 500).
- **Giải pháp khắc phục**:
  - Tạo Flyway migration `V15__increase_avatar_url_length.sql` chuyển sang `VARCHAR(500)` hoặc `TEXT`.

---

#### 9. Database Hit trên từng request với JWT (Thiếu Caching)
- **Vị trí**: [`src/main/java/com/codegym/locketclone/security/jwt/JwtAuthenticationFilter.java`](file:///workspaces/checked-backend/src/main/java/com/codegym/locketclone/security/jwt/JwtAuthenticationFilter.java#L44)
- **Vấn đề**:
  - Tại mỗi request có token hợp lệ:
    ```java
    var userDetails = userDetailsService.loadUserById(userId);
    ```
  - Luôn luôn thực hiện 1 câu `SELECT` vào bảng `users` để dựng `UserPrincipal`.
  - Với một ứng dụng mạng xã hội có tần suất lướt feed và call API liên tục, việc query DB cho từng request là một điểm nghẽn hiệu năng lớn.
- **Giải pháp khắc phục**:
  - Tích hợp bộ nhớ đệm Caffeine (In-Memory) hoặc Redis cho `UserDetailsServiceImpl.loadUserById(UUID id)` với TTL ngắn (khoảng 5–10 phút), tự động evict cache khi user cập nhật thông tin.

---

#### 10. Gửi Email OTP chạy đồng bộ làm block luồng HTTP
- **Vị trí**: [`src/main/java/com/codegym/locketclone/auth/AuthServiceImpl.java`](file:///workspaces/checked-backend/src/main/java/com/codegym/locketclone/auth/AuthServiceImpl.java#L55)
- **Vấn đề**:
  - Lệnh gửi email:
    ```java
    emailService.sendOtpEmail(user.getEmail(), user.getDisplayName(), otpCode);
    ```
    chạy đồng bộ trên thread xử lý request của Tomcat.
  - Quá trình kết nối mạng tới máy chủ SMTP Gmail (TLS handshake + gửi mail) thường mất từ 1 đến 3 giây, thậm chí timeout 5 giây nếu mạng chập chờn.
- **Giải pháp khắc phục**:
  - Bật `@EnableAsync` trong Spring Boot và gắn `@Async` vào phương thức `sendOtpEmail` trong `SmtpEmailService`.
  - Trả về response `201 Created` ngay cho client mà không bắt user phải đợi SMTP hoàn thành.

---

### 🟢 NHÓM 3: CÁC VẤN ĐỀ NHẸ, NỢ KỸ THUẬT & CODE RÁC (LOW PRIORITY)

#### 11. Phương thức rỗng không triển khai trong `FriendshipServiceImpl`
- **Vị trí**: [`src/main/java/com/codegym/locketclone/friendship/FriendshipServiceImpl.java`](file:///workspaces/checked-backend/src/main/java/com/codegym/locketclone/friendship/FriendshipServiceImpl.java#L21-L28)
- **Vấn đề**:
  ```java
  @Override
  public void sendFriendRequest(UUID senderId, UUID receiverId) {

  }

  @Override
  public void acceptFriendRequest(UUID userId, UUID friendId) {

  }
  ```
  - Hai phương thức này nằm trong `FriendshipService` nhưng ruột bỏ trống, không ném exception và cũng không xử lý gì.
- **Khắc phục**:
  - Nếu hệ thống đã chuyển hẳn sang luồng kết bạn qua Invite Link (`FriendInviteLinkService`), hãy xóa 2 phương thức này khỏi interface và service để tránh gây hiểu lầm cho lập trình viên khác.

---

#### 12. Comment rác do AI sinh mã còn sót lại
- **Vị trí**: [`src/main/java/com/codegym/locketclone/photo/Photo.java`](file:///workspaces/checked-backend/src/main/java/com/codegym/locketclone/photo/Photo.java#L24)
- **Vấn đề**:
  - Dòng 24 chứa comment: `// ...existing code...` do prompt AI tạo code dán đè lên file.
- **Khắc phục**: Xóa dòng comment rác này.

---

#### 13. Dead Code & Các Class/DTO mồ côi
- **Các file không còn sử dụng**:
  1. [`VerifyFirebaseTokenRequest.java`](file:///workspaces/checked-backend/src/main/java/com/codegym/locketclone/auth/dto/VerifyFirebaseTokenRequest.java): Tàn dư của Firebase auth cũ.
  2. [`FirebaseConfig.java`](file:///workspaces/checked-backend/src/main/java/com/codegym/locketclone/common/config/FirebaseConfig.java): Class rỗng chỉ chứa javadoc ghi chú.
  3. [`SetPasswordRequest.java`](file:///workspaces/checked-backend/src/main/java/com/codegym/locketclone/auth/dto/SetPasswordRequest.java): Không có controller sử dụng.
  4. [`UserRequest.java`](file:///workspaces/checked-backend/src/main/java/com/codegym/locketclone/user/dto/UserRequest.java): Chứa các trường cũ như `phoneNumber` không còn khớp với luồng đăng ký mới.
  5. [`UpdateFcmTokenRequest.java`](file:///workspaces/checked-backend/src/main/java/com/codegym/locketclone/user/dto/UpdateFcmTokenRequest.java): Không có API controller mapping.
  6. [`DirectMessage.java`](file:///workspaces/checked-backend/src/main/java/com/codegym/locketclone/message/DirectMessage.java) & [`DirectMessageRepository.java`](file:///workspaces/checked-backend/src/main/java/com/codegym/locketclone/message/DirectMessageRepository.java): Tính năng chat bị bỏ dở, không có Controller/Service tương ứng.
- **Khắc phục**: Xóa bỏ các file mồ côi này để giữ codebase sạch sẽ (Clean Architecture).

---

#### 14. Format thông báo lỗi Validation trả về dạng chuỗi thô
- **Vị trí**: [`src/main/java/com/codegym/locketclone/common/exception/GlobalExceptionHandler.java`](file:///workspaces/checked-backend/src/main/java/com/codegym/locketclone/common/exception/GlobalExceptionHandler.java#L41)
- **Vấn đề**:
  ```java
  return ResponseEntity.badRequest().body(new ErrorResponse(400, errors.toString()));
  ```
  - `errors.toString()` trả về chuỗi text thô dạng: `{email=Email không hợp lệ, password=Mật khẩu không được để trống}`.
  - Phía Mobile App (Flutter/React Native) rất khó parse các lỗi này để highlight viền đỏ trên từng ô input tương ứng.
- **Khắc phục**:
  - Mở rộng `ErrorResponse` bổ sung trường `Map<String, String> fieldErrors` hoặc `errors` để trả về JSON object có cấu trúc.

---

#### 15. Chưa tận dụng MapStruct cho `PhotoMapper`
- **Vị trí**: [`src/main/java/com/codegym/locketclone/common/mapper/PhotoMapper.java`](file:///workspaces/checked-backend/src/main/java/com/codegym/locketclone/common/mapper/PhotoMapper.java)
- **Vấn đề**:
  - Dự án đã khai báo `MapStruct` và `lombok-mapstruct-binding` trong `build.gradle` và dùng tốt cho `UserMapper`.
  - Tuy nhiên, `PhotoMapper` lại viết tay thủ công hơn 35 dòng code gán getter/setter.
- **Khắc phục**:
  - Chuyển `PhotoMapper` thành interface có `@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)` để MapStruct tự động sinh code lúc compile.

---

## CHECKLIST HÀNH ĐỘNG KHẮC PHỤC (ACTION ITEMS)

- [ ] **Sửa gấp**: Tối ưu lại query `getYearlyCashflowSummary` thành 1 câu SQL `GROUP BY`.
- [ ] **Sửa gấp**: Thêm đếm số lần sai và giới hạn 5 lần thử cho OTP trong `AuthServiceImpl`.
- [ ] **Sửa gấp**: Cấu hình Testcontainers PostgreSQL trong test suite để kiểm thử chuỗi Flyway migration.
- [ ] **Tái cấu trúc**: Đưa logic upload avatar ra khỏi `UserController`, chuyển vào `UserServiceImpl`.
- [ ] **Bổ sung**: Viết migration `V15` tăng độ dài `avatar_url` lên `VARCHAR(500)` và thêm unique index 2 chiều cho `friendships`.
- [ ] **Cấu hình**: Đưa `CORS_ALLOWED_ORIGINS` ra biến môi trường.
- [ ] **Dọn dẹp**: Xóa các file mồ côi (`VerifyFirebaseTokenRequest`, `FirebaseConfig`, `UserRequest`, `Photo.java` comment rác).
- [ ] **Hiệu năng**: Thêm `@Async` cho việc gửi email SMTP và tích hợp cache cho `loadUserById`.
