# Plan: PR #2 - Prevent Friend Invite Link Burn & Handle Concurrency

## Goal
Khắc phục lỗ hổng cạn kiệt lượt dùng (burn quota) link kết bạn khi bạn bè cũ gọi lại endpoint accept, và xử lý mượt mà xung đột tranh chấp (race condition) trên ràng buộc `uk_friendships_bidirectional`.

## Root Cause Analysis
1. **Quota Burning (Lỗ hổng cạn kiệt link)**:
   - Trong `FriendInviteLinkServiceImpl.java#acceptByToken`, dù 2 user đã là bạn (`findAcceptedBetweenUsers` trả về kết quả), code vẫn tiếp tục thực thi `link.setUsedCount(usedCount + 1)` và lưu lại link.
   - Hậu quả: Một người bạn cũ có thể spam accept 50 lần khiến link bị khóa (`FRIEND_INVITE_LINK_MAX_USES_REACHED`), chặn toàn bộ người dùng mới kết bạn.
2. **Race Condition (Xung đột đồng thời)**:
   - Khi 2 user bấm link mời của nhau cùng lúc hoặc 1 user gửi 2 request song song, cả 2 luồng đều kiểm tra chưa có bạn bè và cùng thực hiện insert `Friendship`.
   - Luồng thứ 2 sẽ chạm ràng buộc unique hai chiều `uk_friendships_bidirectional`, văng ngoại lệ `DataIntegrityViolationException` (HTTP 409 / 500) thay vì hoàn tất kết bạn.

## Tasks
- [x] Task 1: Check out nhánh mới `fix/friend-invite-concurrency` từ branch chuẩn → Verify: `git branch --show-current` trả về đúng tên nhánh.
- [x] Task 2: Cập nhật `acceptByToken` trả về sớm khi đã là bạn bè, không tăng `usedCount` → Verify: Đọc code kiểm tra nhánh `existingFriendship.isPresent()` return ngay lập tức trước khi gọi `setUsedCount`.
- [x] Task 3: Cập nhật `acceptExistingOrCreate` trả về candidate nếu đã `ACCEPTED` và xử lý an toàn xung đột insert (`DataIntegrityViolationException`) → Verify: Không còn ném `FRIEND_ALREADY_EXISTS` khi candidate đã accepted, bắt ngoại lệ constraint vi phạm để lấy quan hệ hiện có.
- [x] Task 4: Viết unit tests trong `FriendInviteLinkServiceImplTest.java` cho kịch bản bạn bè cũ gọi lại link (không tăng count) và xung đột đồng thời → Verify: Chạy test cụ thể qua `./gradlew test --tests *FriendInviteLink*`.
- [x] Task 5: Chạy toàn bộ test suite dự án và cập nhật checklist trong `docs/PULL_REQUESTS_ROADMAP.md` → Verify: `./gradlew test` thành công 100%.

## Done When
- [x] B gọi accept link của A lần thứ 2: HTTP 200, `usedCount` của link giữ nguyên không tăng.
- [x] Hai request kết bạn đồng thời không gây lỗi HTTP 409/500, cả hai đều nhận kết quả bạn bè thành công.
- [x] 100% test suite `./gradlew test` vượt qua (BUILD SUCCESSFUL).
