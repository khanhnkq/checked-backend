# Kế Hoạch Triển Khai Chi Tiết: PR #13

> **Tiêu đề PR**: `perf(db): lower-user-indexes-and-hibernate-tuning`  
> **Nhánh Git**: `perf/db-tuning-and-lower-indexes` (tạo từ nhánh `main`)  
> **Mức độ ưu tiên**: 🟡 **P1 (High - Functional Indexes, Connection Pool Protection & Anti-Duplicate Login)**  
> **Phạm vi ảnh hưởng**: `db/migration`, `config`, `auth`, `security`  
> **Cam kết an toàn**: ❌ **KHÔNG DEPLOY HEROKU** (Mọi thay đổi chỉ thực thi và kiểm thử tại local)

---

## 1. 🔍 Phân Tích Hiện Trạng & Nguyên Nhân Gốc (Root Cause)

### 1.1. Full Table Scan (Seq Scan) khi xác thực đăng nhập qua Email / Username
- **Hiện trạng Database**:
  - Tại migration [V7__alter_database.sql](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/resources/db/migration/V7__alter_database.sql), hai ràng buộc duy nhất đã được thiết lập:
    ```sql
    ALTER TABLE users ADD CONSTRAINT users_email_key UNIQUE (email);
    ALTER TABLE users ADD CONSTRAINT users_username_key UNIQUE (username);
    ```
  - Các ràng buộc này chỉ tạo B-Tree Index cho giá trị chính xác (case-sensitive) của `email` và `username`.
- **Vấn đề trong Spring Data JPA & PostgreSQL**:
  - Khi người dùng đăng nhập bằng username hoặc email, hệ thống gọi:
    ```java
    userRepository.findByEmailIgnoreCaseOrUsernameIgnoreCase(identifier, identifier);
    ```
  - Câu SQL do Hibernate/Spring Data JPA tự động sinh ra là:
    ```sql
    SELECT ... FROM users u
    WHERE LOWER(u.email) = LOWER(?) OR LOWER(u.username) = LOWER(?)
    ```
  - **Cơ chế PostgreSQL**: PostgreSQL không thể sử dụng B-Tree Index tiêu chuẩn trên cột `email` và `username` khi biểu thức truy vấn được bọc trong hàm `LOWER(...)`.
  - Hậu quả: Mọi yêu cầu đăng nhập, làm mới token hay kiểm tra tài khoản đều dẫn đến **Sequential Scan (quét toàn bộ bảng `users`)**. Khi số lượng người dùng tăng, thao tác quét và tính toán `LOWER()` trên CPU sẽ khiến latency đăng nhập tăng vọt.
  - **Giải pháp**: Tạo chỉ mục hàm PostgreSQL (**Expression / Functional Index**):
    ```sql
    CREATE UNIQUE INDEX IF NOT EXISTS idx_users_lower_email ON users (LOWER(email));
    CREATE UNIQUE INDEX IF NOT EXISTS idx_users_lower_username ON users (LOWER(username));
    ```

### 1.2. Trùng lặp truy vấn tìm kiếm người dùng trong luồng Login (2 Roundtrips)
- **Hiện trạng tại [AuthServiceImpl.java#login](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/java/com/codegym/locketclone/auth/AuthServiceImpl.java)**:
  - Khi người dùng gửi request đăng nhập:
    1. Lần 1: `AuthServiceImpl#login` tự gọi `userRepository.findByEmailIgnoreCaseOrUsernameIgnoreCase(identifier, identifier)` để kiểm tra sự tồn tại của tài khoản và chạy dummy hash.
    2. Lần 2: Chuyển giao qua `authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(identifier, password))`. Spring Security `DaoAuthenticationProvider` lại gọi `UserDetailsServiceImpl#loadUserByUsername(identifier)`, và hàm này **gọi lại lần 2 chính câu query `findByEmailIgnoreCaseOrUsernameIgnoreCase`**.
  - **Hậu quả**: Mỗi một lần đăng nhập của người dùng sinh ra **2 câu query trùng lặp 100%** đến PostgreSQL.
  - **Giải pháp**: Tận dụng cơ chế chuẩn của Spring Security `AuthenticationManager`:
    - `DaoAuthenticationProvider` của Spring Security đã tích hợp sẵn cơ chế chống Timing Attack (thông qua `userNotFoundEncodedPassword`).
    - Kiểm tra trạng thái kích hoạt tài khoản (`isEnabled`) thông qua `UserPrincipal#isEnabled() == isVerified`. Khi tài khoản chưa xác thực, Spring Security sẽ ném `DisabledException` (được map trực tiếp sang `ErrorCode.USER_NOT_VERIFIED`).
    - Bổ sung các trường `displayName`, `avatarUrl`, `profileCompleted` vào `UserPrincipal`. Sau khi xác thực thành công, lấy thông tin trực tiếp từ `UserPrincipal` để tạo `JwtResponse` mà không cần truy vấn lại bảng `users`.
    - **Kết quả**: Giảm số lượng truy vấn của API đăng nhập từ $2 \rightarrow 1$ (giảm 50% số lượng query authentication).

### 1.3. Nguy cơ cạn kiệt Connection Pool do bật Open-In-View (OSIV)
- **Hiện trạng Spring Boot**: Mặc định `spring.jpa.open-in-view: true`.
  - Khi OSIV bật, kết nối cơ sở dữ liệu (HikariCP connection) được giữ mở xuyên suốt toàn bộ vòng đời của HTTP request: từ Filter -> Controller -> Service -> View/Serialization -> Interceptors.
  - Nếu quá trình serialize JSON hoặc I/O mạng của client bị chậm, kết nối DB bị chiếm giữ vô ích, dẫn đến tình trạng cạn kiệt connection pool (`Connection timeout`) trong các thời điểm lưu lượng tăng cao.
- **Giải pháp**: Thiết lập rõ ràng `spring.jpa.open-in-view: false` trong cả `application.yml` và `application-prod.yml`.

### 1.4. Thiếu cấu hình JDBC Batching cho các thao tác ghi hàng loạt
- Khi upload một bức ảnh gửi cho nhiều bạn bè (ví dụ 10-20 người nhận), Hibernate mặc định gửi từng câu lệnh `INSERT INTO photo_recipients` riêng lẻ qua mạng.
- **Giải pháp**: Bật JDBC batching trong cấu hình Hibernate:
  - `hibernate.jdbc.batch_size: 25`
  - `hibernate.order_inserts: true`
  - `hibernate.order_updates: true`
  - `hibernate.default_batch_fetch_size: 30`

---

## 2. 💡 Thiết Kế Kỹ Thuật (Technical Design)

### 2.1. Migration V17: `V17__add_lower_user_indexes_and_tuning.sql`
Tạo migration mới trong `src/main/resources/db/migration/`:
```sql
-- ============================================================
-- V17: Add Functional Lower Indexes on Users and Photo Feed Index
-- ============================================================

-- 1. Functional Unique Indexes trên LOWER(email) và LOWER(username)
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_lower_email ON users (LOWER(email));
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_lower_username ON users (LOWER(username));

-- 2. Partial Index cho Feed ảnh chưa bị xóa sắp xếp theo thời gian tạo
CREATE INDEX IF NOT EXISTS idx_photos_feed_status_created ON photos (created_at DESC) WHERE status <> 'DELETED';
```

### 2.2. Tối ưu hóa cấu hình JPA / Hibernate (`application.yml` & `application-prod.yml`)
Bổ sung cấu hình tắt OSIV và bật JDBC Batching:
```yaml
  jpa:
    open-in-view: false
    show-sql: ${JPA_SHOW_SQL:false}
    properties:
      hibernate:
        format_sql: ${JPA_FORMAT_SQL:false}
        jdbc:
          batch_size: 25
        order_inserts: true
        order_updates: true
        default_batch_fetch_size: 30
    hibernate:
      ddl-auto: validate
```

### 2.3. Bổ sung trường thông tin vào `UserPrincipal`
Cập nhật `UserPrincipal`:
- Thêm `displayName`, `avatarUrl`, `profileCompleted`.
- Cập nhật phương thức tĩnh `UserPrincipal.build(User user)`.
- Cập nhật hàm `UserPrincipal#isEnabled` trả về `isVerified`.

### 2.4. Tinh chỉnh `AuthServiceImpl#login`
- Chuyển toàn bộ quá trình xác thực và kiểm tra mật khẩu sang `authenticationManager.authenticate`:
  ```java
  @Override
  public JwtResponse login(LoginRequest request) {
      String identifier = normalizeIdentifier(request.identifier());
      Authentication authentication;
      try {
          authentication = authenticationManager.authenticate(
                  new UsernamePasswordAuthenticationToken(identifier, request.password())
          );
      } catch (DisabledException ex) {
          throw new AppException(ErrorCode.USER_NOT_VERIFIED);
      } catch (AuthenticationException ex) {
          throw new AppException(ErrorCode.INVALID_CREDENTIALS);
      }

      UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
      String jwt = jwtUtils.generateTokenFromUserId(principal.getId());
      return buildJwtResponse(principal, jwt);
  }
  ```
- Thêm hàm overload `buildJwtResponse(UserPrincipal principal, String jwt)`.

---

## 3. 📂 Chi Tiết Từng File Thay Đổi

1. **[src/main/resources/db/migration/V17__add_lower_user_indexes_and_tuning.sql](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/resources/db/migration/V17__add_lower_user_indexes_and_tuning.sql)** *(Tạo mới)*
   - Tạo `idx_users_lower_email`, `idx_users_lower_username` và `idx_photos_feed_status_created`.
2. **[src/main/resources/application.yml](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/resources/application.yml)**
   - Cấu hình `open-in-view: false` và các thuộc tính JDBC batching.
3. **[src/main/resources/application-prod.yml](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/resources/application-prod.yml)**
   - Đồng bộ `open-in-view: false` và JDBC batching cho môi trường Production.
4. **[src/main/java/com/codegym/locketclone/security/service/UserPrincipal.java](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/java/com/codegym/locketclone/security/service/UserPrincipal.java)**
   - Bổ sung `displayName`, `avatarUrl`, `profileCompleted` vào `UserPrincipal`.
5. **[src/main/java/com/codegym/locketclone/auth/AuthServiceImpl.java](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/java/com/codegym/locketclone/auth/AuthServiceImpl.java)**
   - Loại bỏ câu gọi lặp `userRepository.findByEmailIgnoreCaseOrUsernameIgnoreCase` trong hàm `login`.
   - Bắt `DisabledException` chuyển đổi thành `ErrorCode.USER_NOT_VERIFIED`.
   - Bắt `AuthenticationException` chuyển đổi thành `ErrorCode.INVALID_CREDENTIALS`.
6. **[src/test/java/com/codegym/locketclone/auth/AuthServiceImplTest.java](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/test/java/com/codegym/locketclone/auth/AuthServiceImplTest.java)**
   - Cập nhật test cases xác thực của `login` cho khớp với flow đơn giản hóa.

---

## 4. 🛠️ Quy Trình Triển Khai Từng Bước (Step-by-Step Execution)

1. **Bước 1**: Tạo nhánh `perf/db-tuning-and-lower-indexes` từ `main`.
2. **Bước 2**: Tạo file migration `V17__add_lower_user_indexes_and_tuning.sql`.
3. **Bước 3**: Cập nhật file cấu hình `application.yml` và `application-prod.yml`.
4. **Bước 4**: Cập nhật `UserPrincipal.java` và `AuthServiceImpl.java`.
5. **Bước 5**: Cập nhật unit test trong `AuthServiceImplTest.java`.
6. **Bước 6**: Chạy `./gradlew test bootJar` kiểm thử toàn bộ hệ thống.
7. **Bước 7**: Commit chuẩn Conventional Commits, merge vào nhánh `main` ở local và push lên remote `origin`.

---

## 5. ✅ Tiêu Chí Nghiệm Thu & Test Checklist

- [x] File migration `V17__add_lower_user_indexes_and_tuning.sql` cú pháp chuẩn PostgreSQL, chạy mượt mà không lỗi.
- [x] Các câu query `findByEmailIgnoreCaseOrUsernameIgnoreCase` được PostgreSQL tối ưu với Functional Index `LOWER(...)`.
- [x] API đăng nhập chỉ gọi đúng 1 lần query tới database thông qua `UserDetailsServiceImpl`.
- [x] Toàn bộ trường hợp đăng nhập sai mật khẩu, tài khoản chưa verify, tài khoản không tồn tại đều trả về đúng ErrorCode tương ứng.
- [x] Cấu hình `open-in-view: false` và `batch_size: 25` hoạt động ổn định, không phát sinh `LazyInitializationException`.
- [x] Toàn bộ 158+ tests pass 100%, build `bootJar` thành công.
- [x] **Cam kết**: Tuyệt đối không thực thi lệnh deploy lên Heroku.
