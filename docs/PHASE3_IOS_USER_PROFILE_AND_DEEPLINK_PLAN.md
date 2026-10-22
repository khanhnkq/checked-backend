# 📱 Kế Hoạch Chi Tiết Phase 3: Màn Hình Xem Hồ Sơ Cá Nhân (`UserProfileView`) & Xử Lý DeepLink (iOS)

> **Dự án**: `CheckedApp` (iOS 18+ / Swift 6.0 / SwiftUI)  
> **Thuộc hệ thống**: `checked-ios` kết nối `checked-backend` (Heroku Release `v49`)  
> **Kế thừa từ**: `docs/IOS_PROFILE_FRIEND_INVITATION_ENGINE_PLAN.md` & `docs/PHASE2_IOS_REPOSITORY_CACHE_WEBSOCKET_PLAN.md`  
> **Mục tiêu**: Xây dựng toàn bộ giao diện xem hồ sơ cá nhân của người dùng bất kỳ theo username (`UserProfileView`), tích hợp bộ đệm Stale-While-Revalidate, Sheet danh sách bạn chung (`MutualFriendsSheetView`), nút tương tác động theo trạng thái quan hệ (`RelationshipStatus`) và xử lý DeepLink / Universal Link (`checked://u/{username}` và `https://checked.app/u/{username}`).

---

## 1. Mục Tiêu & Phạm Vi Công Việc

Phase 3 đưa toàn bộ sức mạnh của Backend Phase 1 (Profile công khai/xác thực, Hợp đồng quan hệ động, Bạn chung) và Phase 2 (Bộ đệm đa tầng, Chống bão request) lên giao diện người dùng iOS với trải nghiệm trực quan đẳng cấp Locket / Apple Wallet.

### Các Mục Tiêu Cụ Thể:
1. **Xử Lý DeepLink & Universal Link Liền Mạch**:
   - Nhận diện URL Scheme nội bộ: `checked://u/{username}`.
   - Nhận diện Universal Link web: `https://checked.app/u/{username}`.
   - Khi bấm vào từ iMessage, Zalo, Safari hay Quét QR $\rightarrow$ Mở app tức thì và điều hướng thẳng vào màn hình `UserProfileView(username: String)`.
2. **Giao Diện Hồ Sơ Động Thích Ứng (`UserProfileView`)**:
   - **Hero Section**: Avatar tròn lớn 96x96 NukeUI, Tên hiển thị (24pt Rounded Bold), `@username`, Vương miện Checked GOLD nếu là tài khoản VIP.
   - **Thanh Bạn Chung (Mutual Friends Banner)**: 3 avatar xếp chồng (Overlapping Stack offset -8pt) kèm dòng chữ *"X bạn chung: An, Bình... >"*, chạm vào mở Bottom Sheet `MutualFriendsSheetView`.
   - **Thanh Thao Tác Động (Dynamic Action Button)**: Tự động đổi màu và chức năng theo trạng thái quan hệ:
     - `NONE`: Nút Matcha Glow `D4EB70` to bản **"Thêm bạn bè"** (icon `person.badge.plus.fill`).
     - `PENDING_SENT`: Nút Sand Dust `DFDACD` **"Đã gửi lời mời"** + Nút nhỏ màu đỏ nhạt **"Thu hồi"**.
     - `PENDING_RECEIVED`: Cặp nút đôi: Nút Matcha **"Chấp nhận"** + Nút Sand Dust **"Từ chối"**.
     - `ACCEPTED`: Nút xám sạch **"Bạn bè ✓"**, bấm vào mở ActionSheet: Nhắn tin (mở ChatView 1-1), Hủy kết bạn, Chặn.
     - `BLOCKED`: Nút cảnh báo **"Đã chặn"** kèm nút **"Bỏ chặn"**.
     - `SELF`: Nút **"Chia sẻ mã QR của tôi"** hoặc **"Chỉnh sửa hồ sơ"**.
   - **Menu Tùy Chọn 3 Chấm (`···`)**: Chặn người dùng, Sao chép link hồ sơ, Chia sẻ qua hệ thống ShareSheet.
3. **Triệt Tiêu Co Giật Khung Hình (Zero Layout Shift Skeleton)**:
   - Khi chưa có cache hoặc đang nạp lần đầu: Hiển thị `UserProfileSkeletonView` khớp 1:1 kích thước với Shimmer Oat Milk / Matcha nhẹ nhàng.
4. **Phản Hồi Xúc Giác & Chuyển Động (Haptic & Motion)**:
   - Toàn bộ nút bấm áp dụng `.buttonStyle(PressedScaleButtonStyle())` (nảy nhẹ khi chạm).
   - Rung `UIImpactFeedbackGenerator(style: .medium)` khi gửi lời mời hoặc chấp nhận thành công.

---

## 2. Bảng Phân Rã Nhiệm Vụ (Task Breakdown)

| Mã Task | Tác vụ cụ thể | Tệp mã nguồn | Phương thức / Thành phần |
|:---:|:---|:---|:---|
| **3.1** | Cấu hình Route & URL Scheme | `CheckedApp/App/AppRoute.swift`<br/>`CheckedApp/App/AppCoordinator.swift`<br/>`Config/Info.plist`<br/>`project.yml` | Bổ sung `.userProfile(username: String)`, phương thức `openUserProfile()`, `handleDeepLink()`, đăng ký `CFBundleURLTypes` cho scheme `checked` |
| **3.2** | Điều hướng Root & DeepLink Handler | `CheckedApp/App/AuthGateView.swift`<br/>`CheckedApp/App/CheckedApp.swift` | Bổ sung `.onOpenURL` tại root window và điều hướng navigation destination |
| **3.3** | Xây dựng ViewModel Hồ Sơ | `CheckedApp/Features/Friendship/ViewModels/UserProfileViewModel.swift` | Quản lý UI state, nạp profile qua Repository, gửi/hủy/chấp nhận/từ chối lời mời, chặn/bỏ chặn, xử lý lỗi `MAX_FRIENDS_REACHED` |
| **3.4** | Xây dựng Khung Xương Skeleton | `CheckedApp/Features/Friendship/Views/UserProfileSkeletonView.swift` | Khung xương Shimmer cho Hero Avatar, Text, Mutual Friends và Action Buttons |
| **3.5** | Xây dựng Sheet Danh Sách Bạn Chung | `CheckedApp/Features/Friendship/Views/MutualFriendsSheetView.swift` | Bottom Sheet cuộn danh sách bạn chung, avatar NukeUI, chuyển sang profile bạn chung |
| **3.6** | Xây dựng Giao Diện Chính Hồ Sơ | `CheckedApp/Features/Friendship/Views/UserProfileView.swift` | Màn hình hoàn chỉnh với Hero, Mutual Banner, Dynamic Action Buttons, Context Menu |
| **3.7** | Đồng bộ Project & Kiểm tra Biên dịch | `CheckedApp.xcodeproj` | Chạy `xcodegen generate` và `xcodebuild` đạt `BUILD SUCCEEDED` |

---

## 3. Bản Vẽ Phác Thảo Giao Diện (UI Wireframes)

### 3.1. Các Trạng Thái Mối Quan Hệ Trên `UserProfileView`

```
1. TRƯỜNG HỢP CHƯA KẾT BẠN (NONE):
+-------------------------------------------------------+
|  ( < )                                          ( ··· )|
|                     +-----------+                     |
|                     |  ( 96x96) | [👑 GOLD]           |
|                     |   AVATAR  |                     |
|                     +-----------+                     |
|                    Trần Quốc Bảo                      |
|                      @baotran                         |
|         +---------------------------------------+     |
|         |  (O)(O)(O)  4 bạn chung: An, Bình... >|     |
|         +---------------------------------------+     |
|         +---------------------------------------+     |
|         |          [ THÊM BẠN BÈ  + ]           |     |
|         |      (Nút Matcha Glow D4EB70)         |     |
|         +---------------------------------------+     |
+-------------------------------------------------------+

2. TRƯỜNG HỢP ĐÃ GỬI LỜI MỜI (PENDING_SENT):
+-------------------------------------------------------+
|  +-----------------------+ +-----------------------+  |
|  |   Đã gửi lời mời ...  | |     [ Thu hồi ✕ ]     |  |
|  |   (Nền Sand Dust)     | |     (Nền đỏ nhạt)     |  |
|  +-----------------------+ +-----------------------+  |
+-------------------------------------------------------+

3. TRƯỜNG HỢP ĐỐI PHƯƠNG ĐÃ GỬI LỜI MỜI (PENDING_RECEIVED):
+-------------------------------------------------------+
|  +-----------------------+ +-----------------------+  |
|  |    [ Chấp nhận ✓ ]    | |      [ Từ chối ✕ ]    |  |
|  |    (Nền Matcha Glow)  | |      (Nền Sand Dust)  |  |
|  +-----------------------+ +-----------------------+  |
+-------------------------------------------------------+

4. TRƯỜNG HỢP ĐÃ LÀ BẠN BÈ (ACCEPTED):
+-------------------------------------------------------+
|  +-------------------------------------------------+  |
|  |                 [ BẠN BÈ  ✓ ]                   |  |
|  | (Chạm vào mở menu: Nhắn tin / Hủy kết bạn/ Chặn)|  |
|  +-------------------------------------------------+  |
+-------------------------------------------------------+
```

### 3.2. Bottom Sheet Danh Sách Bạn Chung (`MutualFriendsSheetView`)

```
+-------------------------------------------------------+
|                       [ ---- ]                        |
|  Bạn chung (4)                                  ( X ) |
|                                                       |
|  +-------------------------------------------------+  |
|  | (Avatar) Nguyễn Hoàng An                           |  |
|  |          @hoangan                               |  |
|  +-------------------------------------------------+  |
|  +-------------------------------------------------+  |
|  | (Avatar) Lê Thị Bình                               |  |
|  |          @binhle                                |  |
|  +-------------------------------------------------+  |
+-------------------------------------------------------+
```

---

## 4. Chi Tiết Kỹ Thuật Từng Tệp Mã Nguồn

---

### 4.1. Cấu Hình URL Scheme (`project.yml` & `Config/Info.plist`)
Đăng ký URL Type `checked` để iOS nhận diện DeepLink:
```yaml
# Trong project.yml
targets:
  CheckedApp:
    ...
    info:
      path: Config/Info.plist
      properties:
        CFBundleURLTypes:
          - CFBundleURLName: com.khanhnkq.checked
            CFBundleURLSchemes:
              - checked
```

Trong `Config/Info.plist`:
```xml
<key>CFBundleURLTypes</key>
<array>
    <dict>
        <key>CFBundleTypeRole</key>
        <string>Editor</string>
        <key>CFBundleURLName</key>
        <string>com.khanhnkq.checked</string>
        <key>CFBundleURLSchemes</key>
        <array>
            <string>checked</string>
        </array>
    </dict>
</array>
```

---

### 4.2. Mở Rộng Định Tuyến (`AppRoute.swift` & `AppCoordinator.swift`)

Trong `AppRoute.swift`:
```swift
public enum AppRoute: Hashable, Sendable {
    ...
    case userProfile(username: String)
    case mutualFriends(targetUserId: String, targetName: String)
}
```

Trong `AppCoordinator.swift`:
```swift
// Bổ sung xử lý DeepLink & Mở Profile
public func handleDeepLink(url: URL) {
    // 1. URL Scheme: checked://u/{username}
    if url.scheme == "checked", url.host == "u" {
        let username = url.path.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        if !username.isEmpty {
            openUserProfile(username: username)
        }
    }
    // 2. Universal Link: https://checked.app/u/{username}
    else if url.host == "checked.app", url.pathComponents.count >= 3, url.pathComponents[1] == "u" {
        let username = url.pathComponents[2]
        openUserProfile(username: username)
    }
}

public func openUserProfile(username: String) {
    path.append(AppRoute.userProfile(username: username))
}
```

---

### 4.3. ViewModel Hồ Sơ Cá Nhân (`UserProfileViewModel.swift`)

Áp dụng `@Observable` macro và `@MainActor` của Swift 6:

```swift
import SwiftUI

@Observable
@MainActor
public final class UserProfileViewModel {
    public var profile: UserProfileWithRelation?
    public var isLoading: Bool = false
    public var isActionInProgress: Bool = false
    public var errorMessage: String?
    public var showGoldUpsellAlert: Bool = false
    public var showActionSheet: Bool = false
    public var showBlockConfirmAlert: Bool = false
    public var showMutualFriendsSheet: Bool = false
    
    private let username: String
    private let friendshipRepository: any FriendshipRepositoryProtocol
    
    public init(username: String, friendshipRepository: any FriendshipRepositoryProtocol) {
        self.username = username
        self.friendshipRepository = friendshipRepository
    }
    
    public func loadProfile() async {
        // Đọc cache L1/L2 hiển thị ngay lập tức (0ms)
        if let cached = friendshipRepository.getCachedUserProfile(username: username) {
            self.profile = cached
        } else {
            self.isLoading = true
        }
        
        do {
            let freshProfile = try await friendshipRepository.getUserProfile(username: username)
            self.profile = freshProfile
            self.isLoading = false
        } catch {
            self.isLoading = false
            self.errorMessage = error.localizedDescription
        }
    }
    
    public func sendFriendRequest() async {
        guard let targetId = profile?.id, !isActionInProgress else { return }
        isActionInProgress = true
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        
        // Optimistic UI update
        profile?.relationshipStatus = .pendingSent
        
        do {
            let res = try await friendshipRepository.sendFriendRequest(targetUserId: targetId)
            if res.status == "ACCEPTED" {
                profile?.relationshipStatus = .accepted
                profile?.friendshipId = res.friendshipId
            }
            isActionInProgress = false
        } catch {
            isActionInProgress = false
            // Rollback
            profile?.relationshipStatus = .none
            if let netErr = error as? NetworkError, case .serverError(_, let msg) = netErr, msg?.contains("MAX_FRIENDS_REACHED") == true {
                showGoldUpsellAlert = true
            } else {
                errorMessage = error.localizedDescription
            }
        }
    }
    
    public func acceptRequest() async {
        guard let friendshipId = profile?.friendshipId, !isActionInProgress else { return }
        isActionInProgress = true
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        profile?.relationshipStatus = .accepted
        
        do {
            try await friendshipRepository.acceptFriendRequest(friendshipId: friendshipId)
            isActionInProgress = false
        } catch {
            isActionInProgress = false
            profile?.relationshipStatus = .pendingReceived
            if let netErr = error as? NetworkError, case .serverError(_, let msg) = netErr, msg?.contains("MAX_FRIENDS_REACHED") == true {
                showGoldUpsellAlert = true
            } else {
                errorMessage = error.localizedDescription
            }
        }
    }
    
    public func declineRequest() async {
        guard let friendshipId = profile?.friendshipId, !isActionInProgress else { return }
        isActionInProgress = true
        profile?.relationshipStatus = .none
        
        do {
            try await friendshipRepository.declineFriendRequest(friendshipId: friendshipId)
            isActionInProgress = false
        } catch {
            isActionInProgress = false
            profile?.relationshipStatus = .pendingReceived
            errorMessage = error.localizedDescription
        }
    }
    
    public func cancelSentRequest() async {
        guard let friendshipId = profile?.friendshipId, !isActionInProgress else { return }
        isActionInProgress = true
        profile?.relationshipStatus = .none
        
        do {
            try await friendshipRepository.cancelSentRequest(friendshipId: friendshipId)
            isActionInProgress = false
        } catch {
            isActionInProgress = false
            profile?.relationshipStatus = .pendingSent
            errorMessage = error.localizedDescription
        }
    }
    
    public func blockUser() async {
        guard let userId = profile?.id else { return }
        isActionInProgress = true
        profile?.relationshipStatus = .blocked
        
        do {
            try await friendshipRepository.blockUser(userId: userId)
            isActionInProgress = false
        } catch {
            isActionInProgress = false
            errorMessage = error.localizedDescription
        }
    }
    
    public func unblockUser() async {
        guard let userId = profile?.id else { return }
        isActionInProgress = true
        profile?.relationshipStatus = .none
        
        do {
            try await friendshipRepository.unblockUser(userId: userId)
            isActionInProgress = false
        } catch {
            isActionInProgress = false
            profile?.relationshipStatus = .blocked
            errorMessage = error.localizedDescription
        }
    }
}
```

---

### 4.4. Khung Xương Skeleton (`UserProfileSkeletonView.swift`)
Thiết kế khung xương khớp chính xác 1:1 với bố cục `UserProfileView`:
- Vòng tròn avatar `SkeletonCircle(size: 96)`.
- Thanh text tên `SkeletonBone(width: 160, height: 24, cornerRadius: 6)`.
- Thanh text username `SkeletonBone(width: 100, height: 16, cornerRadius: 4)`.
- Khung bạn chung `SkeletonBone(height: 52, cornerRadius: 16)`.
- Khung nút hành động `SkeletonBone(height: 50, cornerRadius: 14)`.
- Phủ modifier `.skeletonShimmer()` ánh sáng quét Oat Milk/Matcha.

---

### 4.5. Sheet Bạn Chung (`MutualFriendsSheetView.swift`)
- Đọc danh sách bạn chung qua `friendshipRepository.getMutualFriends(targetUserId:page:size:)`.
- Danh sách dạng cuộn mượt mà:
  - Avatar tròn NukeUI 44x44.
  - Tên hiển thị + `@username`.
  - Bấm vào item bất kỳ sẽ đóng sheet và điều hướng mở `UserProfileView` của bạn chung đó.

---

### 4.6. Màn Hình Chính (`UserProfileView.swift`)
- Tích hợp toàn bộ components:
  - Nền kem `AppColors.canvas.ignoresSafeArea()`.
  - Thanh Navigation Bar trong suốt, nút Back tròn `44x44` + Menu nút 3 chấm.
  - Xếp chồng 3 Avatar bạn chung (Overlapping HStack với offset âm -8pt và viền trắng 2pt).
  - Khối nút hành động động (Dynamic Action Buttons) chuyển đổi mượt mà theo `RelationshipStatus`.
  - Alert thông báo khi chạm trần bạn bè (`showGoldUpsellAlert`).

---

## 5. Kế Hoạch Xác Minh & Ma Trận Kiểm Thử (Verification Plan)

### 5.1. Kiểm Tra Cấu Trúc Dự Án & Biên Dịch Xcode
```bash
cd /Users/nguyenkimquockhanh/Desktop/checked-ios
xcodegen generate
xcodebuild -project CheckedApp.xcodeproj -scheme CheckedApp -destination 'generic/platform=iOS' CODE_SIGNING_ALLOWED=NO build
```
Yêu cầu: **`BUILD SUCCEEDED`** (0 warnings, 0 concurrency errors).

### 5.2. Ma Trận Kiểm Thử Kịch Bản Nghiệp Vụ

| Kịch bản kiểm thử | Thao tác thực hiện | Kết quả mong đợi |
|:---|:---|:---|
| **1. Mở Deep Link qua Terminal** | `xcrun simctl openurl booted checked://u/khanhnkq` | Ứng dụng tự động điều hướng mở `UserProfileView` của `khanhnkq` |
| **2. Xem Profile người lạ (`NONE`)** | Mở profile người chưa kết bạn | Nút Matcha Glow "Thêm bạn bè". Bấm vào đổi ngay sang "Đã gửi lời mời" |
| **3. Thu hồi lời mời (`PENDING_SENT`)** | Bấm nút "Thu hồi ✕" | Lời mời bị hủy, nút quay về "Thêm bạn bè" |
| **4. Chấp nhận lời mời (`PENDING_RECEIVED`)** | Bấm nút "Chấp nhận ✓" | Nút đổi sang "Bạn bè ✓", danh sách bạn bè tăng 1 |
| **5. Từ chối lời mời (`PENDING_RECEIVED`)** | Bấm nút "Từ chối ✕" | Lời mời bị từ chối, nút quay về "Thêm bạn bè" |
| **6. Menu Bạn bè (`ACCEPTED`)** | Bấm nút "Bạn bè ✓" | Mở ActionSheet: Nhắn tin (mở ChatView 1-1), Hủy kết bạn, Chặn |
| **7. Xem Sheet Bạn Chung** | Chạm vào thanh "X bạn chung >" | Trồi lên Bottom Sheet danh sách bạn chung, cuộn mượt mà |
| **8. Chạm trần Quota (20 bạn)** | Gửi lời mời khi đã đủ 20 bạn | Xuất hiện Dialog cảnh báo `MAX_FRIENDS_REACHED` + nút nâng cấp GOLD |
| **9. Chặn & Bỏ chặn** | Chọn "Chặn" từ menu 3 chấm | Trạng thái chuyển thành "Đã chặn", có nút "Bỏ chặn" |

---

## 6. Tiêu Chí Hoàn Thành (Definition of Done - DoD)

- [ ] URL Scheme `checked://u/{username}` và `https://checked.app/u/{username}` mở chính xác profile.
- [ ] Màn hình `UserProfileView` hỗ trợ 100% các trạng thái: `NONE`, `PENDING_SENT`, `PENDING_RECEIVED`, `ACCEPTED`, `BLOCKED`, `SELF`.
- [ ] Khung xương `UserProfileSkeletonView` hoạt động mượt mà, Zero Layout Shift.
- [ ] Bottom Sheet `MutualFriendsSheetView` hiển thị chuẩn xác avatar và tên bạn chung.
- [ ] Chạy `xcodegen generate` và `xcodebuild` đạt **`BUILD SUCCEEDED`**.
