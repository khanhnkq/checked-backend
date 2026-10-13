# Kế Hoạch Chi Tiết PR #2: Cache User Identity & Danh Sách Bạn Bè

## Goal
Tích hợp Redis Caching cho Phân vùng 1 (User Identity & Friendship) nhằm triệt tiêu truy vấn Neon DB lặp lại trong mỗi HTTP request qua JWT Filter và API lấy danh sách bạn bè, đồng thời đảm bảo nhất quán dữ liệu bằng Cache Eviction tự động khi cập nhật profile hoặc kết bạn mới.

## Tasks
- [ ] Task 1: Nâng cấp cấu hình Serializer trong `CacheConfig.java` sang `DefaultTyping.EVERYTHING` + `JsonTypeInfo.As.WRAPPER_ARRAY` → Verify: Hỗ trợ an toàn root `List<FriendProfileResponse>` và `UserPrincipal` không bị lỗi ClassCast/MismatchedInput.
- [ ] Task 2: Gắn `@Cacheable(value = "friends", key = "#userId")` vào `FriendshipServiceImpl.getAllFriends` → Verify: Đọc danh sách bạn bè lần 2 không query DB, lấy trực tiếp từ Redis cache TTL 20 phút.
- [ ] Task 3: Bổ sung Cache Eviction 2 chiều trong `FriendInviteLinkServiceImpl.acceptByToken` → Verify: Khi kết bạn thành công, xóa sạch key cache `friends::{ownerId}` và `friends::{currentUserId}`.
- [ ] Task 4: Kiểm tra và hoàn thiện `@CacheEvict(value = "users", key = "#userId")` trong `UserServiceImpl` → Verify: Khi cập nhật profile/avatar, key `users::{userId}` lập tức bị xóa khỏi Redis để JWT filter nạp dữ liệu mới nhất.
- [ ] Task 5: Viết bộ Integration Test `UserAndFriendshipCacheTest.java` → Verify: Kiểm thử hit cache, deserialization của List record, và eviction cả 2 phân vùng chạy pass 100% trên Upstash Redis TLS.
- [ ] Task 6: Kiểm tra toàn bộ test suite dự án qua `./gradlew test` → Verify: Tất cả test suite (202+ tests) pass 100%, không phát sinh hồi quy.

## Done When
- [ ] Nhánh Git `feat/cache-partition-user-and-friendship` được tạo và sẵn sàng commit.
- [ ] Endpoint `GET /api/v1/friendships` được cache trong Redis với TTL 20 phút.
- [ ] Endpoint `loadUserById` (JWT Filter) tận dụng cache `users` TTL 30 phút.
- [ ] Cache được invalidate tự động khi kết bạn (`acceptByToken`) hoặc sửa thông tin cá nhân.
- [ ] Integration test xác thực cả 4 luồng trên môi trường Upstash Redis Cloud thực tế.

## Notes
- **Tránh bẫy Jackson Root Array**: Spring Data Redis mặc định không parse được `List<Record>` ở root nếu dùng `As.PROPERTY`. Bắt buộc dùng `WRAPPER_ARRAY` kết hợp `DefaultTyping.EVERYTHING` đã được chứng minh qua test.
- **Evict 2 chiều**: Khi User A chấp nhận kết bạn với User B qua link invite, cả A và B đều thay đổi danh sách bạn bè, do đó phải xóa cache `friends` của CẢ HAI users.
