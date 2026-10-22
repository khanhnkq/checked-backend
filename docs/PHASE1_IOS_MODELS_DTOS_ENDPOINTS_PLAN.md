# 📱 Kế Hoạch Chi Tiết Phase 1: Domain Models, DTOs & Network Endpoints (iOS)

> **Dự án**: `CheckedApp` (iOS 18+ / Swift 6.0 / SwiftUI)  
> **Thuộc hệ thống**: `checked-ios` kết nối `checked-backend` (Heroku Release `v49`)  
> **Kế thừa từ**: `docs/IOS_PROFILE_FRIEND_INVITATION_ENGINE_PLAN.md`  
> **Mục tiêu**: Xây dựng toàn bộ nền tảng dữ liệu (Data Foundation) bao gồm Domain Models, Codable DTOs và 16 Network Endpoints chuẩn hóa kết nối với Backend Spring Boot.

---

## 1. Mục Tiêu & Phạm Vi Công Việc

Phase 1 là bước nền tảng quyết định tính toàn vẹn và an toàn kiểu dữ liệu (Type Safety) cho toàn bộ 4 giai đoạn tiếp theo của ứng dụng iOS.

### Các Mục Tiêu Cụ Thể:
1. **Thiết lập Domain Models chuẩn Swift 6**:
   - `RelationshipStatus`: Enum trạng thái quan hệ thời gian thực (`NONE`, `PENDING_SENT`, `PENDING_RECEIVED`, `ACCEPTED`, `BLOCKED`, `SELF`, `UNAUTHENTICATED`).
   - `UserProfileWithRelation`: Thực thể hồ sơ người dùng kèm trạng thái quan hệ, friendshipId và danh sách bạn chung.
   - `MutualFriendPreview`: Thực thể tóm tắt bạn chung (avatar, tên, username).
   - `FriendRequestItem`: Thực thể lời mời kết bạn (2 chiều: đã nhận / đã gửi).
   - `FriendshipQuotaInfo`: Mô hình đo lường hạn ngạch 20 bạn bè (Standard) hoặc 100 bạn bè (Gold).
   - `BlockedUserItem`: Thực thể người dùng bị chặn.
2. **Mở rộng Network DTOs (`FriendshipDTOs.swift`)**:
   - Khai báo đầy đủ các Codable DTOs tương thích 1:1 với Backend JSON contracts (ISO 8601 UTC date decoding, Optional fields an toàn, hỗ trợ Nullable).
   - Hỗ trợ DTOs cho các thao tác hàng loạt (Batch Actions: `BatchFriendRequestActionResponseDTO`).
3. **Mở rộng 16 API Endpoints (`FriendshipEndpoints.swift`)**:
   - Tuân thủ giao thức `APIEndpoint` (tự động đính kèm Bearer JWT, xử lý Path variables, URL Query items, JSON body).
4. **Kiểm tra biên dịch & Không phá vỡ hệ thống hiện có**:
   - Chạy `xcodegen generate` để cập nhật cấu trúc Xcode Project.
   - Biên dịch thành công với `xcodebuild` (0 warnings, 0 concurrency errors).

---

## 2. Bảng Phân Rã Nhiệm Vụ (Task Breakdown)

| Mã Task | Tác vụ cụ thể | Tệp mã nguồn | Phương thức / Thành phần |
|:---:|:---|:---|:---|
| **1.1** | Khai báo Domain Models | `CheckedApp/Domain/Models/FriendshipModels.swift` | `RelationshipStatus`, `UserProfileWithRelation`, `MutualFriendPreview`, `FriendRequestItem`, `FriendshipQuotaInfo`, `BlockedUserItem` |
| **1.2** | Cập nhật Model Bạn bè hiện có | `CheckedApp/Domain/Models/Friend.swift` | Giữ nguyên tương thích cũ, bổ sung helper `nameToDisplay` và `fallbackInitials` |
| **1.3** | Mở rộng Codable DTOs | `CheckedApp/Core/Network/DTOs/FriendshipDTOs.swift` | `UserProfileWithRelationDTO`, `MutualFriendPreviewDTO`, `SendFriendRequestRequestDTO`, `SendFriendRequestResponseDTO`, `FriendRequestItemDTO`, `PendingRequestCountDTO`, `BatchFriendRequestActionResponseDTO` |
| **1.4** | Mở rộng Network Endpoints | `CheckedApp/Core/Network/Endpoints/FriendshipEndpoints.swift` | 16 Endpoints: Profile, QR, Lời mời nhận/gửi, Đếm, Accept/Decline đơn lẻ & hàng loạt, Bạn chung, Block/Unblock |
| **1.5** | Tái tạo Project & Biên dịch | `CheckedApp.xcodeproj` | Chạy `xcodegen generate` và `xcodebuild ... build` |

---

## 3. Chi Tiết Kỹ Thuật Từng Tệp Mã Nguồn

---

### 3.1. `CheckedApp/Domain/Models/FriendshipModels.swift` (Tạo Mới)

Tệp này nằm trong tầng **Domain**, độc lập hoàn toàn với SwiftUI và UIKit (`import Foundation` duy nhất), tuân thủ nghiêm ngặt chuẩn `Sendable`, `Identifiable`, `Codable`, `Hashable`:

```swift
import Foundation

// MARK: - Trạng Thái Quan Hệ Giữa 2 Người Dùng
public enum RelationshipStatus: String, Codable, Sendable, Hashable {
    case none = "NONE"
    case pendingSent = "PENDING_SENT"
    case pendingReceived = "PENDING_RECEIVED"
    case accepted = "ACCEPTED"
    case blocked = "BLOCKED"
    case selfUser = "SELF"
    case unauthenticated = "UNAUTHENTICATED"
    
    public var isFriend: Bool { self == .accepted }
    public var isPending: Bool { self == .pendingSent || self == .pendingReceived }
    public var isBlocked: Bool { self == .blocked }
    public var isSelf: Bool { self == .selfUser }
}

// MARK: - Hồ Sơ Người Dùng Kèm Trạng Thái Quan Hệ Động
public struct UserProfileWithRelation: Identifiable, Codable, Sendable, Hashable {
    public let id: String
    public let username: String
    public let firstName: String?
    public let lastName: String?
    public let displayName: String
    public let avatarUrl: String?
    public let isGoldMember: Bool
    public var relationshipStatus: RelationshipStatus
    public var friendshipId: String?
    public let mutualFriendsCount: Int
    public let mutualFriendsPreview: [MutualFriendPreview]
    
    public init(
        id: String,
        username: String,
        firstName: String? = nil,
        lastName: String? = nil,
        displayName: String,
        avatarUrl: String? = nil,
        isGoldMember: Bool = false,
        relationshipStatus: RelationshipStatus = .none,
        friendshipId: String? = nil,
        mutualFriendsCount: Int = 0,
        mutualFriendsPreview: [MutualFriendPreview] = []
    ) {
        self.id = id
        self.username = username
        self.firstName = firstName
        self.lastName = lastName
        self.displayName = displayName
        self.avatarUrl = avatarUrl
        self.isGoldMember = isGoldMember
        self.relationshipStatus = relationshipStatus
        self.friendshipId = friendshipId
        self.mutualFriendsCount = mutualFriendsCount
        self.mutualFriendsPreview = mutualFriendsPreview
    }
    
    public var fallbackInitials: String {
        if let first = displayName.trimmingCharacters(in: .whitespaces).first {
            return String(first).uppercased()
        }
        return "?"
    }
}

// MARK: - Thông Tin Thu Gọn Bạn Chung (Mutual Friend Preview)
public struct MutualFriendPreview: Identifiable, Codable, Sendable, Hashable {
    public let id: String
    public let username: String
    public let displayName: String
    public let avatarUrl: String?
    
    public init(id: String, username: String, displayName: String, avatarUrl: String? = nil) {
        self.id = id
        self.username = username
        self.displayName = displayName
        self.avatarUrl = avatarUrl
    }
    
    public var fallbackInitials: String {
        if let first = displayName.trimmingCharacters(in: .whitespaces).first {
            return String(first).uppercased()
        }
        return "?"
    }
}

// MARK: - Item Lời Mời Kết Bạn (Áp dụng cho cả Nhận và Gửi)
public struct FriendRequestItem: Identifiable, Codable, Sendable, Hashable {
    public let friendshipId: String
    public let userId: String
    public let username: String
    public let firstName: String?
    public let lastName: String?
    public let displayName: String
    public let avatarUrl: String?
    public let isGoldMember: Bool
    public let mutualFriendsCount: Int
    public let createdAt: Date
    
    public var id: String { friendshipId }
    
    public init(
        friendshipId: String,
        userId: String,
        username: String,
        firstName: String? = nil,
        lastName: String? = nil,
        displayName: String,
        avatarUrl: String? = nil,
        isGoldMember: Bool = false,
        mutualFriendsCount: Int = 0,
        createdAt: Date = Date()
    ) {
        self.friendshipId = friendshipId
        self.userId = userId
        self.username = username
        self.firstName = firstName
        self.lastName = lastName
        self.displayName = displayName
        self.avatarUrl = avatarUrl
        self.isGoldMember = isGoldMember
        self.mutualFriendsCount = mutualFriendsCount
        self.createdAt = createdAt
    }
    
    public var fallbackInitials: String {
        if let first = displayName.trimmingCharacters(in: .whitespaces).first {
            return String(first).uppercased()
        }
        return "?"
    }
}

// MARK: - Thông Tin Hạn Ngạch Bạn Bè Thân Thiết (Quota)
public struct FriendshipQuotaInfo: Sendable, Hashable, Equatable {
    public let currentCount: Int
    public let maxLimit: Int
    public let isGoldMember: Bool
    
    public init(currentCount: Int, maxLimit: Int = 20, isGoldMember: Bool = false) {
        self.currentCount = currentCount
        self.maxLimit = maxLimit
        self.isGoldMember = isGoldMember
    }
    
    public var progress: Double {
        guard maxLimit > 0 else { return 0 }
        return min(1.0, Double(currentCount) / Double(maxLimit))
    }
    
    public var isNearLimit: Bool { currentCount >= maxLimit - 2 && currentCount < maxLimit }
    public var isAtLimit: Bool { currentCount >= maxLimit }
    public var remainingSlots: Int { max(0, maxLimit - currentCount) }
}

// MARK: - Item Người Dùng Bị Chặn
public struct BlockedUserItem: Identifiable, Codable, Sendable, Hashable {
    public let id: String
    public let username: String
    public let displayName: String
    public let avatarUrl: String?
    public let blockedAt: Date?
    
    public init(id: String, username: String, displayName: String, avatarUrl: String? = nil, blockedAt: Date? = nil) {
        self.id = id
        self.username = username
        self.displayName = displayName
        self.avatarUrl = avatarUrl
        self.blockedAt = blockedAt
    }
}
```

---

### 3.2. `CheckedApp/Core/Network/DTOs/FriendshipDTOs.swift` (Mở Rộng)

Bổ sung các DTOs giao tiếp trực tiếp với Backend Spring Boot:

```swift
import Foundation

// MARK: - DTO Phản Hồi Profile Người Dùng Kèm Quan Hệ
public struct UserProfileWithRelationDTO: Codable, Sendable {
    public let id: String
    public let username: String
    public let firstName: String?
    public let lastName: String?
    public let displayName: String
    public let avatarUrl: String?
    public let isGoldMember: Bool?
    public let relationshipStatus: String
    public let friendshipId: String?
    public let mutualFriendsCount: Int?
    public let mutualFriendsPreview: [MutualFriendPreviewDTO]?
}

// MARK: - DTO Bạn Chung Xem Trước
public struct MutualFriendPreviewDTO: Codable, Sendable {
    public let id: String
    public let username: String
    public let displayName: String
    public let avatarUrl: String?
}

// MARK: - DTO Gửi Lời Mời Kết Bạn
public struct SendFriendRequestRequestDTO: Codable, Sendable {
    public let targetUserId: String
    
    public init(targetUserId: String) {
        self.targetUserId = targetUserId
    }
}

public struct SendFriendRequestResponseDTO: Codable, Sendable {
    public let friendshipId: String
    public let status: String
    public let targetUserId: String
    public let message: String?
}

// MARK: - DTO Lời Mời Kết Bạn (Nhận / Gửi)
public struct FriendRequestItemDTO: Codable, Sendable {
    public let friendshipId: String
    public let userId: String
    public let username: String
    public let firstName: String?
    public let lastName: String?
    public let displayName: String?
    public let avatarUrl: String?
    public let isGoldMember: Bool?
    public let mutualFriendsCount: Int?
    public let createdAt: Date
}

// MARK: - DTO Đếm Số Lượng Lời Mời Chờ Xử Lý
public struct PendingRequestCountDTO: Codable, Sendable {
    public let count: Int
}

// MARK: - DTO Phản Hồi Xử Lý Hàng Loạt (Accept All / Decline All)
public struct BatchFriendRequestActionResponseDTO: Codable, Sendable {
    public let processedCount: Int
    public let message: String?
}
```

---

### 3.3. `CheckedApp/Core/Network/Endpoints/FriendshipEndpoints.swift` (Mở Rộng 16 Endpoints)

Triển khai đầy đủ 16 endpoints chuẩn `APIEndpoint`:

```swift
import Foundation

// MARK: 1. Xem Profile kèm Quan hệ (GET /api/v1/users/profile/{username})
public struct GetUserProfileWithRelationEndpoint: APIEndpoint {
    public let path: String
    public let method = HTTPMethod.get
    public let headers: [String: String]? = nil
    public let body: Data? = nil
    public let queryItems: [URLQueryItem]? = nil
    
    public init(username: String) {
        self.path = "users/profile/\(username)"
    }
}

// MARK: 2. Tải Ảnh QR Cá Nhân (GET /api/v1/users/profile/{username}/qr)
public struct GetUserProfileQrEndpoint: APIEndpoint {
    public let path: String
    public let method = HTTPMethod.get
    public let headers: [String: String]? = ["Accept": "image/png"]
    public let body: Data? = nil
    public let queryItems: [URLQueryItem]? = nil
    
    public init(username: String) {
        self.path = "users/profile/\(username)/qr"
    }
}

// MARK: 3. Gửi Lời Mời Kết Bạn (POST /api/v1/friendships/requests)
public struct SendFriendRequestEndpoint: APIEndpoint {
    public let path = "friendships/requests"
    public let method = HTTPMethod.post
    public let headers: [String: String]? = nil
    public let body: Data?
    public let queryItems: [URLQueryItem]? = nil
    
    public init(targetUserId: String) {
        let req = SendFriendRequestRequestDTO(targetUserId: targetUserId)
        self.body = try? JSONEncoder().encode(req)
    }
}

// MARK: 4. Danh Sách Lời Mời Đã Nhận (GET /api/v1/friendships/requests/received)
public struct GetReceivedFriendRequestsEndpoint: APIEndpoint {
    public let path = "friendships/requests/received"
    public let method = HTTPMethod.get
    public let headers: [String: String]? = nil
    public let body: Data? = nil
    public let queryItems: [URLQueryItem]?
    
    public init(page: Int = 0, size: Int = 20) {
        self.queryItems = [
            URLQueryItem(name: "page", value: "\(page)"),
            URLQueryItem(name: "size", value: "\(size)")
        ]
    }
}

// MARK: 5. Đếm Lời Mời Đã Nhận (GET /api/v1/friendships/requests/received/count)
public struct GetReceivedRequestsCountEndpoint: APIEndpoint {
    public let path = "friendships/requests/received/count"
    public let method = HTTPMethod.get
    public let headers: [String: String]? = nil
    public let body: Data? = nil
    public let queryItems: [URLQueryItem]? = nil
    
    public init() {}
}

// MARK: 6. Danh Sách Lời Mời Đã Gửi (GET /api/v1/friendships/requests/sent)
public struct GetSentFriendRequestsEndpoint: APIEndpoint {
    public let path = "friendships/requests/sent"
    public let method = HTTPMethod.get
    public let headers: [String: String]? = nil
    public let body: Data? = nil
    public let queryItems: [URLQueryItem]?
    
    public init(page: Int = 0, size: Int = 20) {
        self.queryItems = [
            URLQueryItem(name: "page", value: "\(page)"),
            URLQueryItem(name: "size", value: "\(size)")
        ]
    }
}

// MARK: 7. Đếm Lời Mời Đã Gửi (GET /api/v1/friendships/requests/sent/count)
public struct GetSentRequestsCountEndpoint: APIEndpoint {
    public let path = "friendships/requests/sent/count"
    public let method = HTTPMethod.get
    public let headers: [String: String]? = nil
    public let body: Data? = nil
    public let queryItems: [URLQueryItem]? = nil
    
    public init() {}
}

// MARK: 8. Chấp Nhận Lời Mời (PUT /api/v1/friendships/requests/{id}/accept)
public struct AcceptFriendRequestEndpoint: APIEndpoint {
    public let path: String
    public let method = HTTPMethod.put
    public let headers: [String: String]? = nil
    public let body: Data? = nil
    public let queryItems: [URLQueryItem]? = nil
    
    public init(friendshipId: String) {
        self.path = "friendships/requests/\(friendshipId)/accept"
    }
}

// MARK: 9. Từ Chối Lời Mời (DELETE /api/v1/friendships/requests/{id}/decline)
public struct DeclineFriendRequestEndpoint: APIEndpoint {
    public let path: String
    public let method = HTTPMethod.delete
    public let headers: [String: String]? = nil
    public let body: Data? = nil
    public let queryItems: [URLQueryItem]? = nil
    
    public init(friendshipId: String) {
        self.path = "friendships/requests/\(friendshipId)/decline"
    }
}

// MARK: 10. Thu Hồi Lời Mời Đã Gửi (DELETE /api/v1/friendships/requests/{id}/cancel)
public struct CancelSentFriendRequestEndpoint: APIEndpoint {
    public let path: String
    public let method = HTTPMethod.delete
    public let headers: [String: String]? = nil
    public let body: Data? = nil
    public let queryItems: [URLQueryItem]? = nil
    
    public init(friendshipId: String) {
        self.path = "friendships/requests/\(friendshipId)/cancel"
    }
}

// MARK: 11. Chấp Nhận Toàn Bộ Lời Mời (PUT /api/v1/friendships/requests/accept-all)
public struct AcceptAllFriendRequestsEndpoint: APIEndpoint {
    public let path = "friendships/requests/accept-all"
    public let method = HTTPMethod.put
    public let headers: [String: String]? = nil
    public let body: Data? = nil
    public let queryItems: [URLQueryItem]? = nil
    
    public init() {}
}

// MARK: 12. Từ Chối Toàn Bộ Lời Mời (PUT /api/v1/friendships/requests/decline-all)
public struct DeclineAllFriendRequestsEndpoint: APIEndpoint {
    public let path = "friendships/requests/decline-all"
    public let method = HTTPMethod.put
    public let headers: [String: String]? = nil
    public let body: Data? = nil
    public let queryItems: [URLQueryItem]? = nil
    
    public init() {}
}

// MARK: 13. Danh Sách Bạn Chung (GET /api/v1/friendships/{targetUserId}/mutual)
public struct GetMutualFriendsEndpoint: APIEndpoint {
    public let path: String
    public let method = HTTPMethod.get
    public let headers: [String: String]? = nil
    public let body: Data? = nil
    public let queryItems: [URLQueryItem]?
    
    public init(targetUserId: String, page: Int = 0, size: Int = 20) {
        self.path = "friendships/\(targetUserId)/mutual"
        self.queryItems = [
            URLQueryItem(name: "page", value: "\(page)"),
            URLQueryItem(name: "size", value: "\(size)")
        ]
    }
}

// MARK: 14. Chặn Người Dùng (POST /api/v1/friendships/{userId}/block)
public struct BlockUserEndpoint: APIEndpoint {
    public let path: String
    public let method = HTTPMethod.post
    public let headers: [String: String]? = nil
    public let body: Data? = nil
    public let queryItems: [URLQueryItem]? = nil
    
    public init(userId: String) {
        self.path = "friendships/\(userId)/block"
    }
}

// MARK: 15. Bỏ Chặn Người Dùng (DELETE /api/v1/friendships/{userId}/block)
public struct UnblockUserEndpoint: APIEndpoint {
    public let path: String
    public let method = HTTPMethod.delete
    public let headers: [String: String]? = nil
    public let body: Data? = nil
    public let queryItems: [URLQueryItem]? = nil
    
    public init(userId: String) {
        self.path = "friendships/\(userId)/block"
    }
}

// MARK: 16. Danh Sách Đang Bị Chặn (GET /api/v1/friendships/blocked)
public struct GetBlockedUsersEndpoint: APIEndpoint {
    public let path = "friendships/blocked"
    public let method = HTTPMethod.get
    public let headers: [String: String]? = nil
    public let body: Data? = nil
    public let queryItems: [URLQueryItem]? = nil
    
    public init() {}
}
```

---

## 4. Ma Trận Ánh Xạ Hợp Đồng (Contract Mapping Matrix)

Bảng đối chiếu đảm bảo không có bất kỳ sai lệch nào giữa Backend Java Record/DTO và iOS Swift Struct:

| Backend Java Class (Spring Boot) | Thuộc tính JSON Backend | iOS Swift Model / DTO | Kiểu dữ liệu Swift | Ghi chú tương thích |
|:---|:---|:---|:---|:---|
| `UserProfileWithRelationResponse` | `id` | `UserProfileWithRelationDTO` | `String` | UUID string |
| | `username` | | `String` | Unique username |
| | `firstName`, `lastName` | | `String?` | Nullable |
| | `displayName` | | `String` | Fallback thông minh từ backend |
| | `avatarUrl` | | `String?` | Sanitize URL (`http(s)://`) |
| | `isGoldMember` | | `Bool?` | Defaults to `false` |
| | `relationshipStatus` | | `String` | Parse sang `RelationshipStatus` |
| | `friendshipId` | | `String?` | UUID nếu có quan hệ |
| | `mutualFriendsCount` | | `Int?` | Số lượng bạn chung |
| | `mutualFriendsPreview` | | `[MutualFriendPreviewDTO]?`| Tối đa 3 bạn chung |
| `SendFriendRequestResponse` | `friendshipId` | `SendFriendRequestResponseDTO`| `String` | UUID |
| | `status` | | `String` | `"PENDING"` hoặc `"ACCEPTED"` |
| `PendingFriendRequestResponse` | `friendshipId` | `FriendRequestItemDTO` | `String` | ID của bản ghi Friendship |
| | `userId` | | `String` | ID người gửi/nhận |
| | `createdAt` | | `Date` | Parse từ ISO 8601 UTC string |
| `BatchActionResponse` | `processedCount` | `BatchFriendRequestActionResponseDTO` | `Int` | Số lượng bản ghi đã xử lý |

---

## 5. Kế Hoạch Xác Minh & Kiểm Thử (Verification Plan)

### Bước 1: Tạo tệp mã nguồn
Tạo các tệp tương ứng trong `checked-ios`:
1. `CheckedApp/Domain/Models/FriendshipModels.swift`
2. Cập nhật `CheckedApp/Core/Network/DTOs/FriendshipDTOs.swift`
3. Cập nhật `CheckedApp/Core/Network/Endpoints/FriendshipEndpoints.swift`

### Bước 2: Đồng bộ cấu trúc Xcode với `xcodegen`
```bash
cd /Users/nguyenkimquockhanh/Desktop/checked-ios
xcodegen generate
```
Yêu cầu: `Saved project to CheckedApp.xcodeproj`.

### Bước 3: Kiểm tra biên dịch Xcode (`xcodebuild`)
```bash
xcodebuild -project CheckedApp.xcodeproj -scheme CheckedApp -destination 'generic/platform=iOS' CODE_SIGNING_ALLOWED=NO build
```
Yêu cầu bắt buộc: **`** BUILD SUCCEEDED **`** (0 warnings, 0 syntax errors, 0 concurrency issues).

---

## 6. Tiêu Chí Hoàn Thành (Definition of Done - DoD)

- [ ] Toàn bộ 6 Domain Models được tạo mới với đầy đủ helper properties (`isFriend`, `progress`, `fallbackInitials`).
- [ ] Toàn bộ 7 Network DTOs mới được khai báo và conform `Codable & Sendable`.
- [ ] Toàn bộ 16 Network Endpoints được khai báo chính xác theo đặc tả OpenAPI backend Heroku.
- [ ] Chạy `xcodegen generate` cập nhật project xcode thành công.
- [ ] Chạy `xcodebuild` đạt **`BUILD SUCCEEDED`** mà không làm ảnh hưởng đến bất kỳ tính năng hiện có nào của app.
