# Plan: PR #6 - Enforce JWT Fail-fast, Block Image Decompression Bomb & Mask Friend Email Privacy

## Goal
Loại bỏ hoàn toàn fallback key mặc định của JWT để ứng dụng dừng ngay khi thiếu bí mật môi trường, ngăn chặn tấn công cạn kiệt RAM (Image Decompression Bomb DoS) khi upload ảnh, bảo vệ quyền riêng tư cá nhân bằng cách ẩn email trong API bạn bè và lời mời kết bạn, đồng thời siết chặt tiêu chuẩn mật khẩu đăng ký.

---

## Root Cause & Phân Tích Kỹ Thuật

1. **Hardcoded Fallback JWT Secret (CWE-798 / OWASP A02)**:
   - Trong [`application.yml`](src/main/resources/application.yml), cấu hình `jwtSecret: ${JWT_SECRET:dGVzdC1qd3Qtc2VjcmV0LXRlc3Qtand0LXNlY3JldC10ZXN0LWp3dC1zZWNyZXQ=}` cung cấp giá trị mặc định công khai. Nếu môi trường deploy quên set biến `JWT_SECRET`, kẻ tấn công có thể dùng secret này ký JWT mạo danh bất kỳ tài khoản nào.
   - Cần bắt buộc `jwtSecret: ${JWT_SECRET}` và kiểm tra tính hợp lệ của key lúc boot ứng dụng (Fail-fast).

2. **Nguy cơ Image Decompression Bomb (Pixel Flood DoS / OWASP A10)**:
   - Trong [`ImageProcessingService.java`](src/main/java/com/codegym/locketclone/storage/image/ImageProcessingService.java), `Thumbnails.of(file.getInputStream())` giải nén trực tiếp vào Heap RAM. Một file JPEG chỉ 5-10MB nhưng có header kích thước $40.000 \times 40.000$ pixels sẽ tiêu tốn $\approx 6.4$ GB RAM, gây ra `OutOfMemoryError` và crash JVM.
   - Cần đọc metadata header kích thước qua `ImageReader` trước khi giải nén, từ chối ngay lập tức nếu vượt quá giới hạn an toàn ($8192 \times 8192$ px).

3. **Rò rỉ Email Bạn bè (Information Disclosure / OWASP A01)**:
   - Endpoint `GET /api/v1/friendships` và `POST /api/v1/friend-invite-links/accept` đang trả về `UserResponse` chứa trường `email`.
   - Trong ứng dụng mạng xã hội chia sẻ khoảnh khắc, email là thông tin định danh cá nhân nhạy cảm (PII). Cần tạo DTO chuyên biệt `FriendProfileResponse` chỉ bao gồm các thông tin hiển thị cần thiết (`id`, `username`, `firstName`, `lastName`, `displayName`, `avatarUrl`, `isGoldMember`), loại bỏ `email`.

4. **Chính Sách Mật Khẩu Yếu (Weak Password Policy / OWASP A07)**:
   - [`RegisterRequest.java`](src/main/java/com/codegym/locketclone/auth/dto/RegisterRequest.java) chỉ yêu cầu mật khẩu tối thiểu 6 ký tự (`@Size(min = 6)`), cho phép người dùng đặt các mật khẩu dễ đoán (`123456`, `abcdef`).
   - Cần nâng mức tối thiểu lên 8 ký tự và bổ sung ràng buộc chứa ít nhất một chữ số.

---

## Danh Sách Công Việc (Tasks)

- [x] **Task 1: Tạo nhánh Git `fix/security-jwt-image-and-privacy` từ `main`**
  - Verify: Chạy `git checkout -b fix/security-jwt-image-and-privacy` và xác nhận `git branch --show-current`.

- [x] **Task 2: Bắt buộc `JWT_SECRET` (Fail-fast) và kiểm tra tính hợp lệ lúc khởi động**
  - Bỏ fallback Base64 mặc định trong `src/main/resources/application.yml` (`jwtSecret: ${JWT_SECRET}`).
  - Trong `src/main/java/com/codegym/locketclone/security/jwt/JwtUtils.java`, thêm validation kiểm tra `jwtSecret` không rỗng, giải mã Base64 thành công và độ dài key $\ge 256$ bits (32 bytes). Ném `IllegalStateException` nếu không hợp lệ.
  - Đảm bảo `src/test/resources/application.yml` vẫn giữ mock secret phục vụ test suite.
  - Verify: Chạy test `./gradlew test --tests *JwtUtils*`.

- [x] **Task 3: Chặn Image Decompression Bomb trong `ImageProcessingService.java`**
  - Viết hàm `validateImageDimensions(MultipartFile file)` sử dụng `ImageReader` đọc metadata header mà không nạp toàn bộ ảnh vào bộ nhớ Heap.
  - Từ chối file nếu chiều rộng hoặc chiều cao $> 8192$ pixels bằng cách ném `AppException(ErrorCode.INVALID_PHOTO_FILE)`.
  - Viết unit test kiểm tra từ chối ảnh quá khổ trong `ImageProcessingServiceTest.java`.
  - Verify: Chạy `./gradlew test --tests *ImageProcessingService*`.

- [x] **Task 4: Tạo DTO `FriendProfileResponse` & Ẩn Email trong API bạn bè và lời mời kết bạn**
  - Tạo record `src/main/java/com/codegym/locketclone/friendship/dto/FriendProfileResponse.java` không chứa trường `email`.
  - Thêm phương thức ánh xạ `FriendProfileResponse toFriendProfileResponse(User user)` vào `UserMapper.java`.
  - Cập nhật `FriendshipService.java` và `FriendshipServiceImpl.java`: `getAllFriends` trả về `List<FriendProfileResponse>`.
  - Cập nhật `FriendshipController.java`: `getMyFriends` trả về `ResponseEntity<List<FriendProfileResponse>>`.
  - Cập nhật `AcceptFriendInviteLinkResponse.java`: thay trường `UserResponse owner` thành `FriendProfileResponse owner`.
  - Cập nhật `FriendInviteLinkServiceImpl.java#acceptByToken`.
  - Verify: Đọc code xác nhận không còn trường `email` trong response của bạn bè.

- [x] **Task 5: Siết chặt độ phức tạp mật khẩu trong `RegisterRequest.java`**
  - Nâng giới hạn `@Size(min = 8, max = 72, message = "Mật khẩu phải từ 8 đến 72 ký tự")`.
  - Bổ sung `@Pattern(regexp = "^(?=.*[0-9]).*$", message = "Mật khẩu phải chứa ít nhất một chữ số")`.
  - Cập nhật các test case đăng ký trong `RegisterRequestValidationTest.java` và `AuthServiceImplTest.java`.
  - Verify: Chạy `./gradlew test --tests *Register*`.

- [x] **Task 6: Cập nhật WebMvc Controller Tests và Service Tests**
  - Cập nhật assertions trong `FriendshipControllerTest.java` sang `FriendProfileResponse`.
  - Cập nhật assertions trong `FriendInviteLinkControllerTest.java` và `FriendInviteLinkServiceImplTest.java`.
  - Verify: Chạy `./gradlew test --tests *Friendship*` và `./gradlew test --tests *FriendInvite*`.

- [x] **Task 7: Chạy toàn bộ Test Suite, cập nhật Roadmap, Commit & Merge vào `main`**
  - Chạy `./gradlew clean test bootJar`.
  - Đánh dấu hoàn thành PR #6 trong `docs/PULL_REQUESTS_ROADMAP.md`.
  - Commit với thông điệp: `fix(security): enforce jwt failfast, block image bomb and mask friend email`.
  - Merge vào `main` và kiểm tra `git log -n 1`.
  - Verify: Build 100% SUCCESSFUL, không có hồi quy.

---

## Done When
- [x] Ứng dụng fail-fast ngay khi khởi động nếu biến môi trường `JWT_SECRET` bị thiếu.
- [x] Ảnh có kích thước pixel bất thường ($> 8192\text{px}$) bị chặn từ chối ngay từ header, không tốn Heap RAM.
- [x] Danh sách bạn bè (`/api/v1/friendships`) và chấp nhận lời mời (`/api/v1/friend-invite-links/accept`) không còn rò rỉ `email` cá nhân.
- [x] Mật khẩu đăng ký bắt buộc từ 8 ký tự và chứa ít nhất một chữ số.
- [x] Toàn bộ test suite `./gradlew test` chạy pass 100%.
- [x] Nhánh `main` chứa commit hoàn tất của PR #6.
