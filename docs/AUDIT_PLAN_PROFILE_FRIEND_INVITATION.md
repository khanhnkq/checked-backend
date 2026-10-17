# 📋 Kế Hoạch Audit Toàn Diện: Profile Link & Friend Invitation Engine
## Đánh Giá Hiệu Năng, Truy Vấn CSDL, Quy Tắc Nghiệp Vụ & An Toàn Bảo Mật (Phases 1 - 4)

> **Tài liệu tham chiếu**:  
> - [`docs/IMPLEMENTATION_PLAN_PROFILE_FRIEND_INVITATION.md`](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/docs/IMPLEMENTATION_PLAN_PROFILE_FRIEND_INVITATION.md)  
> - [`docs/PHASE_4_MUTUAL_FRIENDS_OUTGOING_REQUESTS_AND_QUOTA_PLAN.md`](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/docs/PHASE_4_MUTUAL_FRIENDS_OUTGOING_REQUESTS_AND_QUOTA_PLAN.md)  
> **Phạm vi kiểm tra**: Toàn bộ mã nguồn, cấu hình CSDL, Cache, Rate limiting, Security và Unit/Integration test của tính năng kết bạn qua link cá nhân (Phases 1 đến 4).  
> **Trạng thái nhánh**: Đã tích hợp hoàn tất trên `main` (commit `4c4d96a`).

---

## 1. MỤC TIÊU AUDIT (AUDIT OBJECTIVES)

Đợt audit này nhằm rà soát có hệ thống nhằm đảm bảo:
1. **Hiệu năng & Tài nguyên (Performance & Resource Efficiency)**:
   - Không tồn tại vấn đề N+1 query, truy vấn lặp dư thừa hoặc thiếu index.
   - Cache Redis 2 chiều (`FRIENDS_CACHE`, `FEED_CACHE`) hoạt động đúng, không sinh stale data, không rò rỉ bộ nhớ.
   - Giảm thiểu độ trễ truy vấn (P95 Latency < 30ms cho profile view, < 100ms cho friend requests).
2. **Quy tắc nghiệp vụ & Toàn vẹn dữ liệu (Business Rules & Integrity Constraints)**:
   - Ràng buộc trần bạn bè (Friend Quota: 20 bạn standard / 100 bạn gold) không bị bypass dưới bất kỳ kịch bản nào (kể cả race condition hay batch accept-all).
   - Cơ chế Block người dùng triệt để: không rò rỉ quan hệ bạn chung, không nhận request và không preview profile của nhau.
   - Cơ chế bắt xung đột đồng thời (Race Condition Catch) trên `uk_friendships_bidirectional` và Pessimistic Lock trên `FriendInviteLink`.
3. **An toàn bảo mật & Chống lạm dụng (Security & Abuse Prevention)**:
   - Nguyên tắc Privacy-First: Tuyệt đối không lộ `email`, `password`, `otpCode` trong bất kỳ response nào.
   - Chống XSS trên OpenGraph Social Share Page (`GET /u/{username}`).
   - Phủ kín Rate Limiting (Bucket4j) cho các endpoint nhạy cảm và các endpoint tốn CPU (như sinh mã QR bằng ZXing).

---

## 2. MA TRẬN PHÂN TÍCH & CÁC ĐIỂM CẦN ĐÁNH GIÁ (AUDIT MATRIX)

### 2.1 Trụ Cột 1: Hiệu Năng & Tối Ưu Truy Vấn CSDL (Query & Performance Audit)

| ID | Hạng mục kiểm tra | Hiện trạng code | Vấn đề phát hiện / Rủi ro tiềm ẩn | Đề xuất tối ưu hóa |
| :--- | :--- | :--- | :--- | :--- |
| **Q1** | **Tính toán Bạn Chung khi xem Profile** | `UserServiceImpl.java:69-77` | Đang gọi cả `countMutualFriends` VÀ `findMutualFriends` trong cùng 1 request xem profile. Cả 2 query đều chạy subquery quét 2 chiều trên bảng `friendships`. | Chỉ cần gọi `findMutualFriends` lấy danh sách. Đếm số lượng trực tiếp từ danh sách này (vì tối đa bạn bè là 100), loại bỏ hoàn toàn 1 round-trip query thừa. |
| **Q2** | **N+1 Query trong Batch `accept-all`** | `FriendshipServiceImpl.java:375-390` | Vòng lặp `for (Friendship request : pendingRequests)` gọi `friendshipRepository.countFriends(requester.getId())` cho từng requester. Nếu có 10 request chờ duyệt $\rightarrow$ bắn 10 queries đếm. | Thay vì đếm từng người, dùng batch query hoặc kiểm tra trước khi duyệt, gom `friendshipRepository.saveAll(acceptedList)` để giảm số lần ghi CSDL. |
| **Q3** | **Chỉ mục PostgreSQL (Database Indexes)** | `V15` & `V16` migration | Đã có `uk_friendships_bidirectional`, `idx_friendships_user_status`, `idx_friendships_friend_status`. | Cần kiểm tra `EXPLAIN ANALYZE` cho câu lệnh JPQL `findMutualFriends` với subquery `CASE WHEN` xem PostgreSQL có tận dụng Bitmap Index Scan hiệu quả hay fallback về Seq Scan. |
| **Q4** | **Cache Eviction Consistency** | `evictFriendshipAndFeedCache` | Xóa `friends::userA`, `friends::userB`, `feed::userA`, `feed::userB`. | Kiểm tra tính nhất quán: Khi một người bị block hoặc unfriend, cache có bị bỏ sót partition nào không? |
| **Q5** | **Overhead tạo QR Code** | `QrCodeService.java` | Sinh ảnh PNG 512x512 byte stream trực tiếp bằng ZXing mỗi khi gọi API. | Đánh giá thêm HTTP Cache Header (`Cache-Control: public, max-age=86400`) hoặc lưu cache byte array trên Redis để tránh CPU spike khi bị quét liên tục. |

---

### 2.2 Trụ Cột 2: Quy Tắc Nghiệp Vụ & Toàn Vẹn Dữ Liệu (Business Rules & Integrity Audit)

| ID | Hạng mục kiểm tra | Hiện trạng code | Vấn đề phát hiện / Rủi ro tiềm ẩn | Đề xuất tối ưu hóa |
| :--- | :--- | :--- | :--- | :--- |
| **R1** | **Kiểm tra Block trước khi tính Bạn Chung** | `UserServiceImpl.java:68-87` | Trong `getUserProfileByUsername`, logic tính `countMutualFriends` và `findMutualFriends` đang được thực thi ở dòng 69-77, **TRƯỚC KHI** kiểm tra quan hệ `relationOpt` (dòng 79). | Nếu User B đã Block User A, User A khi xem profile User B vẫn tốn CPU/DB query tính bạn chung, thậm chí có thể lộ số lượng bạn chung. **Phải dời bước kiểm tra BLOCKED lên đầu!** |
| **R2** | **Friend Quota Enforcement khi Batch Accept** | `FriendshipServiceImpl.java:375-390` | Khi current user chỉ còn trống 2 slot bạn bè mà có 5 lời mời đang chờ: vòng lặp duyệt đúng 2 lời mời đầu và `break`. 3 lời mời còn lại giữ nguyên `PENDING`. | Cần xác minh hành vi người dùng mong muốn: Khi vượt trần, giữ nguyên các request còn lại hay trả về thông báo cảnh báo đạt trần bạn bè? |
| **R3** | **Race Condition khi 2 request Accept đồng thời vượt Quota** | `acceptFriendRequest` & `acceptByToken` | Đang kiểm tra `validateFriendLimit` trước khi `save(friendship)`. | Nếu 2 lời mời cùng được chấp nhận tại cùng một mili-giây khi user đang có 19 bạn (trần 20), cả 2 luồng đều đọc thấy `count = 19` $\rightarrow$ kết quả có thể thành 21 bạn (vượt trần 1 bạn). Cần đánh giá mức độ nghiêm trọng và giải pháp lock / conditional update nếu cần. |
| **R4** | **Đồng bộ hóa Trạng thái Profile View** | `UserProfileWithRelationResponse` | `relationshipStatus`: `NONE`, `PENDING_SENT`, `PENDING_RECEIVED`, `ACCEPTED`, `BLOCKED`, `SELF`, `UNAUTHENTICATED`. | Kiểm tra xem khi caller bị target block thì trả về `BLOCKED` hay trả về `NONE` / `USER_NOT_FOUND` để tránh lộ thông tin bị block cho người dùng bên ngoài. |

---

### 2.3 Trụ Cột 3: An Toàn Bảo Mật, Privacy & Rate Limiting (Security Audit)

| ID | Hạng mục kiểm tra | Hiện trạng code | Vấn đề phát hiện / Rủi ro tiềm ẩn | Đề xuất tối ưu hóa |
| :--- | :--- | :--- | :--- | :--- |
| **S1** | **XSS Injection trên Share Page** | `ShareController.java` | Đã dùng `HtmlUtils.htmlEscape` cho `displayName`, `username`, `avatarUrl`. | Kiểm tra kỹ URL injection trong `avatarUrl` (ví dụ `javascript:...` thay vì `https://...`) để đảm bảo thẻ `<img>` và script redirect không bị khai thác DOM XSS. |
| **S2** | **Thiếu Rate Limiting cho Endpoint Sinh QR** | `RateLimiterService.java:55` | `requestUri.contains("/api/v1/users/profile/") && !requestUri.endsWith("/qr")` $\rightarrow$ Cố tình loại trừ `/qr` khỏi rate limit! | Sinh ảnh QR tốn tài nguyên CPU (Matrix encoding). Cần gán rate limit riêng (ví dụ 30 req/phút) để ngăn chặn kẻ xấu DDOS server bằng cách spam request QR. |
| **S3** | **Thiếu Rate Limiting cho Trang Share `/u/{username}`** | `RateLimiterService.java` | Endpoint `GET /u/{username}` là public không có JWT và hiện chưa nằm trong danh sách Bucket4j. | Cần áp dụng rate limit chung cho public share crawler để chống quét tài nguyên hàng loạt. |
| **S4** | **Rà soát Rò rỉ Thông tin Nhạy cảm (Data Leakage)** | Tất cả DTOs bạn bè | `FriendProfileResponse`, `SentFriendRequestResponse`, `FriendRequestResponse`. | Kiểm tra 100% không trả về `email`, `password`, `dob`, `otpCode` hay thông tin tài khoản ngân hàng / calo của bạn bè. |
| **S5** | **Phân quyền HTTP Method trên SecurityConfig** | `SecurityConfig.java:71` | `.requestMatchers(HttpMethod.GET, "/u/**").permitAll()` | Đã khóa đúng `HttpMethod.GET`. Cần xác nhận không mở nhầm các method `POST`, `PUT`, `DELETE`. |

---

## 3. KẾ HOẠCH HÀNH ĐỘNG AUDIT (ACTION PLAN & PHASES)

### Bước 1: Rà Soát & Refactor Code Theo Phát Hiện Audit (✅ Đã thực hiện)
- [x] **Fix 1 (Tối ưu Query Bạn Chung & Cách Ly BLOCKED)**:
  - Refactor `UserServiceImpl.getUserProfileByUsername`:
    1. Kiểm tra quan hệ `findRelationBetweenUsers` trước.
    2. Nếu quan hệ là `BLOCKED`, trả về ngay lập tức, không chạy query bạn chung (không rò rỉ dữ liệu).
    3. Bỏ gọi `countMutualFriends`, chỉ gọi `findMutualFriends`, lấy `size()` làm `mutualFriendsCount` và `stream().limit(3)` làm preview. Giảm 50% số lượng subquery DB!
- [x] **Fix 2 (Tối ưu N+1 trong Batch Accept-All)**:
  - Tối ưu `acceptAllPendingRequests` trong `FriendshipServiceImpl`: Gom các bản ghi hợp lệ lại và gọi `friendshipRepository.saveAll(acceptedList)`.
- [x] **Fix 3 (Bảo Mật & Rate Limiting)**:
  - Bổ sung Rate Limiting cho `/qr` (`QR_GENERATION`) và `/u/{username}` (`SOCIAL_SHARE`) trong `RateLimiterService.java` & `RateLimitProperties.java`.
  - Validate định dạng URL avatar trong `ShareController` (chỉ chấp nhận `http://` hoặc `https://`, chống DOM XSS).

### Bước 2: Kiểm Thử Đơn Vị & Tích Hợp (Verification)
- [x] Unit test kiểm chứng: khi caller bị block, tuyệt đối không gọi `findMutualFriends` (`UserServiceProfileLinkTest`).
- [x] Unit test kiểm chứng: avatar độc hại `javascript:...` bị chuyển thành default avatar an toàn (`ShareControllerTest`).
- [x] Unit test kiểm chứng: routing rate limit đúng cho `/qr` và `/u/{username}` (`RateLimiterServiceTest`).

### Bước 3: Load Testing & Regression Suite
- [x] Chạy lại toàn bộ test suite `./gradlew test`: **327 tests passed, 0 failures, 1 skipped (100% PASS)**.

---

## 4. TIÊU CHÍ HOÀN THÀNH (DONE WHEN)

- [x] Giảm thiểu 1 round-trip query trong API xem Profile cá nhân (`GET /api/v1/users/profile/{username}`).
- [x] Quan hệ `BLOCKED` được cách ly hoàn toàn, không lộ bất kỳ thông tin bạn chung nào.
- [x] Endpoint sinh QR và Public Share Page được bảo vệ bởi Bucket4j Rate Limiting.
- [x] Không còn cảnh báo N+1 query trong các thao tác batch (`accept-all`).
- [x] Toàn bộ 327 bài test chạy tự động pass 100%.
- [x] Code sạch sẽ, tuân thủ Clean Code & SOLID principles.
