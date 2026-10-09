# Plan: PR #4 - Optimize Photo Upload TX, Fix Cache Evict & Enforce UserPrincipal Verification

## Goal
Giải phóng kết nối database trong quá trình upload S3 mạng, dọn dẹp file mồ côi khi lưu DB lỗi, sửa lỗi Spring AOP self-invocation làm mất tác dụng xóa cache người dùng, siết chặt xác thực người dùng trong `UserPrincipal`, và bổ sung `@Transactional(readOnly = true)` cho các truy vấn đọc.

## Root Cause Analysis
1. **Khóa kết nối DB trong lúc upload mạng S3 (DB Connection Starvation)**:
   - Trong [`PhotoServiceImpl.java#uploadPhoto`](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/java/com/codegym/locketclone/photo/PhotoServiceImpl.java), annotation `@Transactional` bao trùm toàn bộ hàm. Lệnh `storageService.uploadPhoto(file)` (xử lý resize ảnh + upload 2 file qua HTTP tới S3) chạy khi đang giữ kết nối database, làm nghẽn HikariCP connection pool khi lưu lượng cao.
   - Nếu lưu database thất bại sau khi đã upload file, file trên S3 trở thành "mồ côi" (orphan file) gây tốn dung lượng lưu trữ và chi phí.
2. **Lỗi Spring AOP Proxy Self-Invocation làm hỏng Cache Eviction**:
   - [`UserServiceImpl.java#updatePersonalInfo`](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/java/com/codegym/locketclone/user/UserServiceImpl.java) gọi nội bộ `this.updateCurrentUserProfile(...)`. Do gọi trực tiếp trên instance `this` mà không thông qua Spring Proxy, annotation `@CacheEvict(value = "users", key = "#userId")` không bao giờ được kích hoạt, khiến cache người dùng bị cũ (stale cache).
3. **`UserPrincipal.isEnabled()` bị hardcode `true`**:
   - [`UserPrincipal.java`](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/java/com/codegym/locketclone/security/service/UserPrincipal.java) ghi đè `isEnabled() { return true; }` mà không gắn trạng thái `user.getIsVerified()`. Kẻ dùng token của tài khoản chưa xác thực vẫn vượt qua `JwtAuthenticationFilter`.
4. **Thiếu `@Transactional(readOnly = true)` cho các phương thức đọc**:
   - Các truy vấn đọc trong `PhotoServiceImpl` và `ExpenseServiceImpl` đang dùng `@Transactional` mặc định (read-write), khiến Hibernate phải bật Dirty Checking tốn CPU/RAM và ngăn cơ sở dữ liệu tối ưu hóa khóa đọc.

## Tasks
- [x] Task 1: Tạo và checkout nhánh mới `perf/upload-tx-and-cache-evict` từ `main` → Verify: `git branch --show-current` trả về `perf/upload-tx-and-cache-evict`.
- [x] Task 2: Đưa `uploadPhoto` ra ngoài transaction lớn, bọc giao dịch nhỏ cho metadata và dọn dẹp file S3 khi lỗi DB trong `PhotoServiceImpl.java` → Verify: Đọc code xác nhận S3 upload nằm ngoài transaction và có khối catch gọi `storageService.deleteFile`.
- [x] Task 3: Thêm `@CacheEvict(value = "users", key = "#userId")` lên `updatePersonalInfo` trong `UserServiceImpl.java` → Verify: Đọc code xác nhận annotation `@CacheEvict` hiện diện trên phương thức.
- [x] Task 4: Gắn trường `isVerified` vào `UserPrincipal.java` và cập nhật `isEnabled()` trả về `isVerified` → Verify: `UserPrincipal.build(user)` truyền `user.getIsVerified()` và `isEnabled()` trả về giá trị này.
- [x] Task 5: Bổ sung `@Transactional(readOnly = true)` cho các hàm đọc trong `PhotoServiceImpl.java` và `ExpenseServiceImpl.java` → Verify: Import đúng `org.springframework.transaction.annotation.Transactional(readOnly = true)`.
- [x] Task 6: Viết unit tests kiểm thử dọn dẹp file S3 khi lỗi DB và `UserPrincipal.isEnabled()` → Verify: Chạy test `./gradlew test --tests *PhotoService*` và `./gradlew test --tests *UserPrincipal*`.
- [x] Task 7: Chạy toàn bộ test suite `./gradlew test`, cập nhật roadmap, commit và merge thẳng vào `main` local → Verify: `./gradlew test` thành công 100%, commit sạch trên `main`.

## Done When
- [x] Upload ảnh không còn giữ DB connection trong lúc gọi S3.
- [x] Lỗi lưu DB sau upload ảnh tự động xóa file trên S3.
- [x] `updatePersonalInfo` kích hoạt xóa cache `users`.
- [x] `UserPrincipal.isEnabled()` phản ánh chính xác `isVerified`.
- [x] Toàn bộ test suite `./gradlew test` vượt qua (BUILD SUCCESSFUL).
- [x] Nhánh `main` local được cập nhật với commit của PR #4.
