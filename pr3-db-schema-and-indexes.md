# Plan: PR #3 - Sync DB Schema, Indexes & Income Category Seeds

## Goal
Đồng bộ schema cơ sở dữ liệu với JPA Entity (độ dài `image_url` 500 ký tự, `transaction_type NOT NULL`), bổ sung chỉ mục tối ưu truy vấn bạn bè hai chiều và feed ảnh, seed danh mục thu nhập (INCOME) mặc định, và nới lỏng validation avatar DTO lên 500 ký tự.

## Root Cause Analysis
1. **Lệch độ dài `image_url` (DataTruncationException)**:
   - Entity [`Photo.java`](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/java/com/codegym/locketclone/photo/Photo.java) định nghĩa `image_url` có `length = 500`, nhưng migration `V7` hạ cột này xuống `VARCHAR(255)`. Các URL từ Cloud/S3/CDN dài sẽ làm sập lệnh insert.
2. **Thiếu ràng buộc `NOT NULL` cho `categories.transaction_type`**:
   - Entity [`Category.java`](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/java/com/codegym/locketclone/expense/Category.java) đánh dấu `nullable = false`, nhưng DB migration `V13` chưa áp dụng `NOT NULL`.
3. **Thiếu danh mục mẫu cho nguồn thu (INCOME)**:
   - Migration `V11` chỉ seed các danh mục chi tiêu (Food, Transport,...). Người dùng ghi nhận thu nhập không có danh mục hệ thống mặc định.
4. **Thiếu index truy vấn bạn bè 2 chiều**:
   - Truy vấn bạn bè dùng `WHERE (user_id = :id OR friend_id = :id) AND status = 'ACCEPTED'`, nhưng chưa có index trên `(friend_id, status)` và `(user_id, status)`.
5. **Lệch validation DTO avatar**:
   - [`UpdateProfileRequest.java`](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/java/com/codegym/locketclone/user/dto/UpdateProfileRequest.java) và [`UpdatePersonalInfoRequest.java`](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/java/com/codegym/locketclone/user/dto/UpdatePersonalInfoRequest.java) vẫn chặn `avatarUrl` ở `@Size(max = 255)`, trong khi bảng `users.avatar_url` đã được mở rộng lên 500 ký tự từ `V15`.

## Tasks
- [x] Task 1: Tạo và checkout nhánh mới `fix/db-schema-and-indexes` từ `main` → Verify: `git branch --show-current` trả về `fix/db-schema-and-indexes`.
- [x] Task 2: Tạo migration `V16__harden_photo_and_friendship_indexes.sql` đồng bộ photo length, category constraint, index bạn bè & seed INCOME → Verify: File migration cú pháp SQL chuẩn PostgreSQL.
- [x] Task 3: Cập nhật validation `@Size(max = 500)` cho `avatarUrl` trong `UpdateProfileRequest.java` và `UpdatePersonalInfoRequest.java` → Verify: Đọc code xác nhận annotations đổi từ 255 thành 500.
- [x] Task 4: Viết unit tests kiểm tra validation avatarUrl (256-500 chars hợp lệ, > 500 chars vi phạm) → Verify: Chạy test cụ thể qua `./gradlew test --tests *Profile*`.
- [x] Task 5: Chạy `./gradlew test` toàn bộ dự án, cập nhật roadmap, commit và merge thẳng vào `main` local → Verify: Test pass 100%, `main` chứa commit sạch.

## Done When
- [x] File migration `V16` tồn tại và đồng bộ hoàn toàn với JPA entities (`Photo`, `Category`).
- [x] `UpdateProfileRequest` & `UpdatePersonalInfoRequest` chấp nhận avatar URL dài tới 500 ký tự.
- [x] Toàn bộ test suite `./gradlew test` vượt qua (BUILD SUCCESSFUL).
- [x] Nhánh `main` local được cập nhật với commit của PR #3.
