# 📱 PR-Chat-4: iOS Messaging Implementation Plan (CheckedApp)

> **Mục tiêu**: Xây dựng toàn bộ hệ thống Nhắn tin 1-1 Realtime trên iOS (`CheckedApp` - SwiftUI + Swift 6 + iOS 18.0) kết nối trơn tru với Spring Boot Backend (REST + WebSocket STOMP), hỗ trợ đầy đủ Photo Reply, Typing Indicator, Unread Badge và Message Lifecycle.

---

## 1. Kiến Trúc Tổng Thể Phía iOS (Clean Architecture + @Observable)

```mermaid
graph TD
    subgraph "Presentation Layer (SwiftUI + @Observable)"
        CLV["ConversationListView<br/>(Danh sách hội thoại + Unread Badge)"]
        CV["ChatView<br/>(Giao diện chat 1-1 + Typing + Bubbles)"]
        PDV["PhotoDetailView<br/>(Reply ảnh trực tiếp vào chat)"]
        CVM["ChatViewModel / ConversationListViewModel"]
    end

    subgraph "Domain Layer"
        CR_PROTO["ChatRepositoryProtocol"]
        MODELS["ChatModels<br/>(Conversation, ChatMessage, MessageType)"]
    end

    subgraph "Data & Network Layer"
        CR_IMPL["ChatRepository<br/>(REST API + Local Cache)"]
        WS_MGR["StompWebSocketClient<br/>(URLSessionWebSocketTask + STOMP)"]
        ENDPOINTS["ChatEndpoints<br/>(REST API Client)"]
    end

    subgraph "Core & DI"
        COORD["AppCoordinator<br/>(Route: .chatList, .chat)"]
        CONTAINER["AppContainer<br/>(Inject ChatRepository)"]
    end

    CLV --> CVM
    CV --> CVM
    PDV --> CR_PROTO
    CVM --> CR_PROTO
    CR_PROTO <|.. CR_IMPL
    CR_IMPL --> ENDPOINTS
    CR_IMPL --> WS_MGR
    CONTAINER --> CR_IMPL
    COORD --> CLV
    COORD --> CV
```

---

## 2. Phân Rã Các Phase & Nhiệm Vụ Cụ Thể

### 🧩 Phase 1: Models, DTOs & Native STOMP Client
| # | Tác vụ | File dự kiến | Chi tiết |
|---|---|---|---|
| **1.1** | Domain Models | `Domain/Models/ChatModels.swift` | Khai báo `Conversation`, `ChatMessage`, `MessageType` (`text`, `photoReply`, `revoked`), `PhotoContextInfo`, `ReplyToInfo`, `DeleteMode`. Tuân thủ `Identifiable`, `Codable`, `Sendable`, `Hashable`. |
| **1.2** | DTOs & REST Endpoints | `Core/Network/DTOs/ChatDTOs.swift`<br/>`Core/Network/Endpoints/ChatEndpoints.swift` | DTOs gửi/nhận khớp với backend Java; các endpoint: `getMyConversations`, `getOrCreateConversation`, `getMessages(before, limit)`, `sendMessage`, `markAsRead`, `replyToPhoto`, `getTotalUnreadCount`, `deleteMessage`. |
| **1.3** | Native STOMP WebSocket Client | `Core/Network/StompWebSocketClient.swift` | Dùng thuần `URLSessionWebSocketTask` (Zero-dependency bloat); kết nối `wss://.../ws-chat` kèm Bearer JWT; xử lý frame STOMP `CONNECT`, `SUBSCRIBE`, `SEND`, `MESSAGE`, Heartbeat 10s/10s; Auto-reconnect với Exponential Backoff. |

---

### 🗄️ Phase 2: Data Repository & Dependency Injection
| # | Tác vụ | File dự kiến | Chi tiết |
|---|---|---|---|
| **2.1** | Chat Repository | `Domain/Repositories/ChatRepositoryProtocol.swift`<br/>`Data/Repositories/ChatRepository.swift` | Quản lý state hội thoại, cache tin nhắn offline; cung cấp `AsyncStream` cho tin nhắn mới, read receipts, typing indicator và revoked events. |
| **2.2** | DI Container & Config | `Core/Config/AppEnvironment.swift`<br/>`Core/DI/AppContainer.swift` | Bổ sung `wsChatUrl` trong `AppEnvironment` (tự động chuyển đổi `https://` $\rightarrow$ `wss://`); inject `chatRepository` vào `AppContainer`. |

---

### 🧭 Phase 3: Navigation Routing & Badge Tích Hợp
| # | Tác vụ | File dự kiến | Chi tiết |
|---|---|---|---|
| **3.1** | Định tuyến Navigation | `App/AppRoute.swift`<br/>`App/AppCoordinator.swift` | Thêm route `.chatList` và `.chat(conversationId: String?, friend: Friend)`; method `coordinator.openChat(with: friend)`. |
| **3.2** | Unread Badge TopBar | `Shared/Components/HomeTopBar.swift`<br/>`Features/Home/Views/CameraViewfinder.swift` | Chuyển hành động nút `message.fill` từ `.friendship` sang `.chatList`; hiển thị chấm đỏ Unread Badge khi có tin nhắn chưa đọc. |

---

### 💬 Phase 4: Giao Diện Danh Sách Trò Chuyện (Conversation List)
| # | Tác vụ | File dự kiến | Chi tiết |
|---|---|---|---|
| **4.1** | Màn hình danh sách hội thoại | `Features/Chat/Views/ConversationListView.swift`<br/>`Features/Chat/ViewModels/ConversationListViewModel.swift` | Giao diện Dark mode sang trọng; hiển thị danh sách hội thoại, avatar tròn NukeUI, tên bạn bè, thời gian gửi tương đối, tóm tắt tin cuối (text / photo reply / tin đã thu hồi), badge số tin chưa đọc màu Matcha Glow (`AppColors.primary`); kéo để làm mới (Pull-to-refresh). |

---

### 📨 Phase 5: Giao Diện Cuộc Trò Chuyện 1-1 (ChatView & Components)
| # | Tác vụ | File dự kiến | Chi tiết |
|---|---|---|---|
| **5.1** | Bong bóng tin nhắn | `Features/Chat/Views/ChatBubble.swift` | Bong bóng tin nhắn Matcha Glow (bên phải - của tôi) và Surface Dark (bên trái - của bạn); Card preview cho `PHOTO_REPLY` (ảnh vuông bo góc + tên chủ ảnh); Card trích dẫn tin nhắn (`replyTo`); hiển thị tin đã thu hồi; trạng thái đã xem (`isRead`). |
| **5.2** | Thanh nhập tin & Typing | `Features/Chat/Views/ChatInputBar.swift` | TextField bo góc Capsule, nút gửi Matcha tròn; thanh preview trích dẫn khi đang Reply kèm nút `x`; tự động gửi STOMP `/app/chat.typing` khi gõ phím (debounce 2.5s để tắt). |
| **5.3** | Màn hình Chat & Logic | `Features/Chat/Views/ChatView.swift`<br/>`Features/Chat/ViewModels/ChatViewModel.swift` | Phân trang cuộn ngược (Cursor-based pagination) tải thêm tin cũ khi cuộn lên đỉnh; Typing indicator động (3 chấm nhảy); Menu giữ tin nhắn (Context Menu / Long Press): Trả lời, Xóa ở phía tôi (`FOR_ME`), Thu hồi (`FOR_EVERYONE`); tự động `markAsRead` khi mở hội thoại. |

---

### 📷 Phase 6: Tích Hợp Photo Reply Từ Màn Hình Xem Ảnh
| # | Tác vụ | File dự kiến | Chi tiết |
|---|---|---|---|
| **6.1** | Gửi phản hồi ảnh từ Feed | `Features/Home/Views/HistoryPhotoGridView.swift` | Thay thế hàm mock `sendComment()` tại dòng 1102 bằng `container.chatRepository.replyToPhoto(photoId, content)`; hiển thị Toast phản hồi "Đã gửi tin nhắn đến [Tên bạn] ✨"; bấm vào chuyển thẳng sang cuộc trò chuyện 1-1. |

---

## 3. Tiêu Chí Hoàn Thành (Done When)
- [ ] Bấm icon Chat trên Camera Top Bar mở được danh sách các cuộc hội thoại.
- [ ] Gửi và nhận tin nhắn realtime 2 chiều qua WebSocket STOMP với độ trễ < 100ms.
- [ ] Typing indicator hiển thị mượt mà khi người bên kia đang soạn tin nhắn.
- [ ] Người dùng xem ảnh trên Feed/History phản hồi tin nhắn trực tiếp vào luồng chat 1-1.
- [ ] Thu hồi tin nhắn cập nhật tức thời trên cả 2 thiết bị thành "Tin nhắn đã được thu hồi".
- [ ] Toàn bộ code compile thành công với Swift 6 (Zero concurrency warning) trên Xcode 16.
