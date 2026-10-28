# 🛠️ Plan: Khắc Phục Lỗ Hổng & Hoàn Thiện Module Bạn Bè và Social Share

## Mục tiêu
Khắc phục triệt để lỗ hổng Rate Limiting trên endpoint mời kết bạn, sửa chữa 4 unit tests đang bị hỏng trong `ShareControllerTest`, bổ sung test coverage cho `/invite`, đồng bộ hóa avatar fallback và cấu hình URL theo chuẩn production.

---

## Danh Sách Nhiệm Vụ (Tasks)

- [x] **Task 1: Vá lỗ hổng Rate Limiting cho `/invite` và `/friend/accept`**
  - **File:** `src/main/java/com/codegym/locketclone/security/ratelimit/RateLimiterService.java`
  - **Hành động:** Mở rộng điều kiện nhận diện `AuthEndpointType.SOCIAL_SHARE` trong phương thức `resolveEndpointType` cho các request `GET` và `HEAD` bắt đầu bằng `/invite` và `/friend/accept`.
  - **Verify:** Đã chạy `./gradlew test --tests "com.codegym.locketclone.security.ratelimit.RateLimiterServiceTest"` $\rightarrow$ PASSED.

- [x] **Task 2: Sửa lỗi NullPointerException và đồng bộ hóa assertions trong `ShareControllerTest`**
  - **File:** `src/test/java/com/codegym/locketclone/user/share/ShareControllerTest.java`
  - **Hành động:** 
    - Dùng `ReflectionTestUtils.setField` trong `setUp()` để tiêm `profileBaseUrl` (`https://checked.app/u/`) và `inviteBaseUrl` (`https://checked.app/invite`). Thêm safe fallbacks trong `ShareController`.
    - Cập nhật assertion avatar mặc định từ link cũ sang `ShareController.DEFAULT_AVATAR_URL`.
    - Cập nhật assertion deep link `checked://u/...` thay cho link download store đã gỡ bỏ.
  - **Verify:** 4/4 test cũ đã chuyển từ FAILED sang PASSED.

- [x] **Task 3: Viết bổ sung Unit Tests cho `getInviteSharePage` (`/invite` & `/friend/accept`)**
  - **File:** `src/test/java/com/codegym/locketclone/user/share/ShareControllerTest.java`
  - **Hành động:** Bổ sung mock `FriendInviteLinkRepository` và viết 5 test cases bao quát:
    1. Token rỗng hoặc null $\rightarrow$ trả về HTTP 400 Bad Request.
    2. Token không tồn tại $\rightarrow$ trả về HTTP 404 Not Found.
    3. Token đã bị chủ sở hữu thu hồi (`revokedAt != null`) $\rightarrow$ trả về HTTP 410 Gone.
    4. Token đã hết hạn (`expiresAt.isBefore(now)`) $\rightarrow$ trả về HTTP 410 Gone.
    5. Token hợp lệ $\rightarrow$ trả về HTTP 200 OK với đầy đủ thẻ OpenGraph, Avatar, Mã mời và nút Sao chép.
  - **Verify:** Chạy `./gradlew test --tests "com.codegym.locketclone.user.share.ShareControllerTest"` $\rightarrow$ 9/9 tests PASSED.

- [x] **Task 4: Chuẩn hóa Fallback Avatar và Base URL**
  - **Files:**
    - `src/main/java/com/codegym/locketclone/user/share/ShareController.java`
  - **Hành động:**
    - Khai báo hằng số `public static final String DEFAULT_AVATAR_URL` tập trung trong `ShareController`.
    - Xây dựng 2 hàm `getEffectiveProfileBaseUrl()` và `getEffectiveInviteBaseUrl()` để bảo đảm an toàn chống null và chống thiếu dấu slash `/`.
  - **Verify:** Code sạch, compile không warning.

- [x] **Task 5: Kiểm thử hồi quy toàn diện (Regression Verification)**
  - **Lệnh thực thi:**
    `./gradlew test --tests "com.codegym.locketclone.friendship.*" --tests "com.codegym.locketclone.user.share.*" --tests "com.codegym.locketclone.security.ratelimit.*" --tests "com.codegym.locketclone.user.UserServiceProfileLinkTest"`
  - **Kết quả:** BUILD SUCCESSFUL in 3s, 100% tests PASSED!

---

## Tiêu Chí Hoàn Thành (Done When)
- [x] Tất cả endpoint chia sẻ (`/u/**`, `/invite`, `/friend/accept`) đều được Bucket4j bảo vệ chống spam/DDoS (hỗ trợ cả GET và HEAD).
- [x] `ShareControllerTest` chuyển từ 4 failures sang 0 failures và có độ phủ test toàn diện cho link mời (9/9 passed).
- [x] Không còn lỗi `NullPointerException` khi chạy không có Spring Context.
- [x] Toàn bộ test suite liên quan đến Friendship & Social Share chạy pass 100%.
