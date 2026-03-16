# API Contract - Auth & Profile Flow (Flutter-ready)

Contract này bám theo backend hiện tại của `locket-clone` và khớp với `DioClient` phía Flutter.

## Mục tiêu flow

```text
Register -> Verify OTP -> Login (hoặc dùng token trả về từ Verify) -> Get Me -> Complete Profile -> Home
```

## Base URL

Local:

```text
http://localhost:8080
```

Flutter device/emulator dùng IP LAN của máy backend, ví dụ:

```text
http://192.168.1.60:8080
```

---

# 1. Auth contract tổng quát

## Public endpoints
Các API này **không cần Bearer token**:
- `POST /api/v1/auth/register`
- `POST /api/v1/auth/verify`
- `POST /api/v1/auth/login`

## Protected endpoints
Các API còn lại cần header:

```http
Authorization: Bearer <JWT_TOKEN>
```

## Response model dùng chung

### `JwtResponse`

```json
{
  "token": "string",
  "type": "Bearer",
  "id": "uuid",
  "email": "string",
  "username": "string",
  "isVerified": true,
  "profileCompleted": false,
  "displayName": "string",
  "avatarUrl": "string | null",
  "nextStep": "COMPLETE_PROFILE | HOME"
}
```

### `UserResponse`

```json
{
  "id": "uuid",
  "email": "string",
  "username": "string",
  "firstName": "string | null",
  "lastName": "string | null",
  "displayName": "string",
  "avatarUrl": "string | null",
  "isVerified": true,
  "isGoldMember": false,
  "profileCompleted": true
}
```

### `ErrorResponse`

```json
{
  "timestamp": "yyyy-MM-dd HH:mm:ss",
  "status": 400,
  "message": "string",
  "path": ""
}
```

### `RegisterResponse`

```json
{
  "message": "Đăng ký thành công, vui lòng kiểm tra email để lấy mã OTP",
  "email": "khanhnguyenkim30825@gmail.com",
  "nextStep": "VERIFY_OTP"
}
```

---

# 2. Register

## Endpoint

```http
POST /api/v1/auth/register
Content-Type: application/json
```

## Request body

```json
{
  "email": "khanhnguyenkim30825@gmail.com",
  "username": "khanhnguyenkim30825",
  "password": "SnapWidget@123"
}
```

## Validation
- `email`: bắt buộc, đúng định dạng email
- `username`: bắt buộc, không blank, tối đa 50 ký tự
- `password`: bắt buộc, tối thiểu 6 ký tự

## Backend behavior
- backend normalize `email` về lowercase + trim
- backend normalize `username` về lowercase + trim
- nếu email đã tồn tại nhưng **chưa verify**, backend sẽ refresh OTP và cập nhật lại `username/password`
- nếu email đã tồn tại và **đã verify**, backend trả lỗi `EMAIL_ALREADY_EXISTS`

## Success response

### HTTP 201

```json
{
  "message": "Đăng ký thành công, vui lòng kiểm tra email để lấy mã OTP",
  "email": "khanhnguyenkim30825@gmail.com",
  "nextStep": "VERIFY_OTP"
}
```

## Error responses

### HTTP 400 - email đã được dùng

```json
{
  "timestamp": "2026-03-12 09:20:00",
  "status": 400,
  "message": "Email đã được sử dụng",
  "path": ""
}
```

### HTTP 400 - username đã được dùng

```json
{
  "timestamp": "2026-03-12 09:20:00",
  "status": 400,
  "message": "Username đã được sử dụng",
  "path": ""
}
```

### HTTP 400 - validate fail

```json
{
  "timestamp": "2026-03-12 09:20:00",
  "status": 400,
  "message": "{email=Email không đúng định dạng}",
  "path": ""
}
```

## FE action
- hiển thị toast/snackbar thành công từ `message`
- điều hướng sang màn nhập OTP
- lưu `email` từ response hoặc từ input hiện tại để verify OTP

---

# 3. Verify OTP

## Endpoint

```http
POST /api/v1/auth/verify
Content-Type: application/json
```

## Request body

```json
{
  "email": "khanhnguyenkim30825@gmail.com",
  "otp": "482910"
}
```

## Validation
- `email`: bắt buộc, đúng định dạng email
- `otp`: bắt buộc, đúng 6 chữ số

## Success response

### HTTP 200

```json
{
  "token": "<JWT_TOKEN>",
  "type": "Bearer",
  "id": "3cda5f64-7ca2-4d6e-9fd1-6b9c2c3b67b8",
  "email": "khanhnguyenkim30825@gmail.com",
  "username": "khanhnguyenkim30825",
  "isVerified": true,
  "profileCompleted": false,
  "displayName": "khanhnguyenkim30825",
  "avatarUrl": null,
  "nextStep": "COMPLETE_PROFILE"
}
```

## FE action
- lưu `token`
- lưu `id` nếu cần
- nếu `nextStep == COMPLETE_PROFILE` thì sang màn hoàn thiện hồ sơ
- nếu `nextStep == HOME` thì vào home luôn

## Error responses

### HTTP 400 - OTP sai

```json
{
  "timestamp": "2026-03-12 09:21:00",
  "status": 400,
  "message": "Mã OTP không chính xác",
  "path": ""
}
```

### HTTP 400 - OTP hết hạn

```json
{
  "timestamp": "2026-03-12 09:21:00",
  "status": 400,
  "message": "Mã OTP đã hết hạn",
  "path": ""
}
```

### HTTP 404 - không tìm thấy người dùng

```json
{
  "timestamp": "2026-03-12 09:21:00",
  "status": 404,
  "message": "Không tìm thấy người dùng",
  "path": ""
}
```

---

# 4. Login

## Endpoint

```http
POST /api/v1/auth/login
Content-Type: application/json
```

## Request body

> `identifier` có thể là `email` hoặc `username`

```json
{
  "identifier": "khanhnguyenkim30825@gmail.com",
  "password": "SnapWidget@123"
}
```

hoặc

```json
{
  "identifier": "khanhnguyenkim30825",
  "password": "SnapWidget@123"
}
```

## Validation
- `identifier`: bắt buộc
- `password`: bắt buộc

## Backend behavior
- backend normalize `identifier` về lowercase + trim
- login **chỉ thành công khi user đã verify email**
- response trả về `nextStep` để FE quyết định vào home hay màn complete profile

## Success response

### HTTP 200

```json
{
  "token": "<JWT_TOKEN>",
  "type": "Bearer",
  "id": "3cda5f64-7ca2-4d6e-9fd1-6b9c2c3b67b8",
  "email": "khanhnguyenkim30825@gmail.com",
  "username": "khanhnguyenkim30825",
  "isVerified": true,
  "profileCompleted": true,
  "displayName": "Khánh Nguyễn Kim",
  "avatarUrl": "https://example.com/avatar.jpg",
  "nextStep": "HOME"
}
```

## Error responses

### HTTP 403 - chưa verify email

```json
{
  "timestamp": "2026-03-12 09:22:00",
  "status": 403,
  "message": "Tài khoản chưa được xác thực email",
  "path": ""
}
```

### HTTP 404 - không tìm thấy người dùng

```json
{
  "timestamp": "2026-03-12 09:22:00",
  "status": 404,
  "message": "Không tìm thấy người dùng",
  "path": ""
}
```

### HTTP 401 - sai mật khẩu / token auth nội bộ không hợp lệ

```json
{
  "timestamp": "2026-03-12 09:22:00",
  "status": 401,
  "message": "Bạn chưa đăng nhập hoặc token không hợp lệ",
  "path": ""
}
```

> Lưu ý: với login sai mật khẩu, backend hiện đang map về message chung `Bạn chưa đăng nhập hoặc token không hợp lệ`.

## FE action
- `200`: lưu token, điều hướng theo `nextStep`
- `403`: điều hướng về màn OTP verify và giữ lại `email`
- `404`: báo tài khoản không tồn tại
- `401`: báo email/username hoặc mật khẩu không đúng

---

# 5. Get Current User

## Endpoint

```http
GET /api/v1/users/me
Authorization: Bearer <JWT_TOKEN>
```

## Success response

### HTTP 200

```json
{
  "id": "3cda5f64-7ca2-4d6e-9fd1-6b9c2c3b67b8",
  "email": "khanhnguyenkim30825@gmail.com",
  "username": "khanhnguyenkim30825",
  "firstName": "Khánh",
  "lastName": "Nguyễn Kim",
  "displayName": "Khánh Nguyễn Kim",
  "avatarUrl": "https://example.com/avatar.jpg",
  "isVerified": true,
  "isGoldMember": false,
  "profileCompleted": true
}
```

## Error response

### HTTP 401 - token thiếu / sai / hết hạn

```json
{
  "timestamp": "2026-03-12 09:23:00",
  "status": 401,
  "message": "Bạn chưa đăng nhập hoặc token không hợp lệ",
  "path": ""
}
```

## FE action
- gọi sau `verify` hoặc `login` nếu muốn đồng bộ user state đầy đủ
- nếu `profileCompleted == false` thì đi màn complete profile
- nếu `profileCompleted == true` thì vào home

---

# 6. Complete Profile

## Endpoint

```http
PATCH /api/v1/users/me/profile
Authorization: Bearer <JWT_TOKEN>
Content-Type: application/json
```

## Request body

```json
{
  "username": "khanhnguyenkim30825",
  "firstName": "Khánh",
  "lastName": "Nguyễn Kim",
  "avatarUrl": "https://example.com/avatar.jpg"
}
```

## Validation
- `username`: optional nhưng nếu gửi lên thì không được blank, max 50
- `firstName`: optional nhưng nếu gửi lên thì không được blank, max 50
- `lastName`: optional nhưng nếu gửi lên thì không được blank, max 50
- `avatarUrl`: optional nhưng nếu gửi lên thì không được blank, max 255

## Backend behavior
- `username`, `firstName`, `lastName`, `avatarUrl` đều là optional field
- backend trim dữ liệu trước khi lưu
- backend chỉ xem profile là hoàn tất khi có đủ:
  - `username`
  - `firstName`
  - `lastName`
- `avatarUrl` **không bắt buộc** để `profileCompleted = true`
- nếu đổi `username` sang giá trị đã tồn tại, backend hiện trả message `Người dùng đã tồn tại`

## Success response

### HTTP 200

```json
{
  "id": "3cda5f64-7ca2-4d6e-9fd1-6b9c2c3b67b8",
  "email": "khanhnguyenkim30825@gmail.com",
  "username": "khanhnguyenkim30825",
  "firstName": "Khánh",
  "lastName": "Nguyễn Kim",
  "displayName": "Khánh Nguyễn Kim",
  "avatarUrl": "https://example.com/avatar.jpg",
  "isVerified": true,
  "isGoldMember": false,
  "profileCompleted": true
}
```

## Error responses

### HTTP 400 - username trùng

```json
{
  "timestamp": "2026-03-12 09:24:00",
  "status": 400,
  "message": "Người dùng đã tồn tại",
  "path": ""
}
```

### HTTP 400 - request không hợp lệ

```json
{
  "timestamp": "2026-03-12 09:24:00",
  "status": 400,
  "message": "{firstName=First name không được để trống nếu được cung cấp}",
  "path": ""
}
```

### HTTP 401 - token sai / hết hạn

```json
{
  "timestamp": "2026-03-12 09:24:00",
  "status": 401,
  "message": "Bạn chưa đăng nhập hoặc token không hợp lệ",
  "path": ""
}
```

## FE action
- sau khi update thành công, cập nhật local state từ response hoặc gọi lại `GET /api/v1/users/me`
- nếu `profileCompleted == true` thì đi `HomeScreen`

---

# 7. FE state machine đề xuất

## Auth states
- `unauthenticated`
- `otpPending`
- `authenticatedProfileIncomplete`
- `authenticatedReady`

## Rule mapping
- register thành công -> `otpPending`
- verify/login thành công + `nextStep == COMPLETE_PROFILE` -> `authenticatedProfileIncomplete`
- verify/login thành công + `nextStep == HOME` -> `authenticatedReady`
- `GET /users/me` trả `profileCompleted == false` -> `authenticatedProfileIncomplete`
- `GET /users/me` trả `profileCompleted == true` -> `authenticatedReady`

---

# 8. Mapping cho Flutter `DioClient`

Đoạn client hiện tại đang đúng hướng với backend:
- public endpoint list đúng: `/api/v1/auth/login`, `/api/v1/auth/register`, `/api/v1/auth/verify`
- protected endpoint sẽ tự gắn `Authorization: Bearer <token>`
- khi gặp `401` ở protected API thì có thể clear local auth state

## Lưu ý quan trọng cho FE
- register response là **JSON object**, không còn là plain string
- dùng `response.data['message']`, `response.data['email']`, `response.data['nextStep']`
- **không clear token khi gặp `403` ở login**; đây là case `USER_NOT_VERIFIED`, cần đưa user về màn OTP
- login request dùng field `identifier`, không phải `email`
- `nextStep` là field quan trọng nhất sau verify/login để điều hướng màn hình
- nếu app gọi `GET /users/me` khi app khởi động và bị `401`, hãy logout local và về login screen

## Public endpoint list khuyến nghị

```dart
final publicEndpoints = [
  '/api/v1/auth/login',
  '/api/v1/auth/register',
  '/api/v1/auth/verify',
];
```

---

# 9. DTO gợi ý cho Flutter

## RegisterRequest

```json
{
  "email": "string",
  "username": "string",
  "password": "string"
}
```

## RegisterResponse

```json
{
  "message": "string",
  "email": "string",
  "nextStep": "VERIFY_OTP"
}
```

## VerifyOtpRequest

```json
{
  "email": "string",
  "otp": "string"
}
```

## LoginRequest

```json
{
  "identifier": "string",
  "password": "string"
}
```

---

# 10. Ready-to-test examples

## Register

```http
POST /api/v1/auth/register
```

```json
{
  "email": "khanhnguyenkim30825@gmail.com",
  "username": "khanhnguyenkim30825",
  "password": "SnapWidget@123"
}
```

## Verify

```http
POST /api/v1/auth/verify
```

```json
{
  "email": "khanhnguyenkim30825@gmail.com",
  "otp": "482910"
}
```

## Login

```http
POST /api/v1/auth/login
```

```json
{
  "identifier": "khanhnguyenkim30825@gmail.com",
  "password": "SnapWidget@123"
}
```

## Complete Profile

```http
PATCH /api/v1/users/me/profile
Authorization: Bearer <JWT_TOKEN>
```

```json
{
  "username": "khanhnguyenkim30825",
  "firstName": "Khánh",
  "lastName": "Nguyễn Kim",
  "avatarUrl": "https://example.com/avatar.jpg"
}
```

---

# 11. Photo send flow

## Mục tiêu flow

```text
Login -> Capture Photo -> Choose audience (ALL_FRIENDS | SELECTED_FRIENDS) -> Optional amount -> Send -> Feed/My Photos
```

## Protected endpoints

Tất cả API ảnh đều cần:

```http
Authorization: Bearer <JWT_TOKEN>
```

## `POST /api/v1/photos`

### Content-Type

```http
multipart/form-data
```

### Form fields
- `file`: bắt buộc, chỉ nhận `image/jpeg | image/png | image/webp`
- `caption`: optional
- `amount`: optional, số >= 0
- `recipientScope`: optional, `ALL_FRIENDS | SELECTED_FRIENDS`
- `audienceMode`: optional alias của `recipientScope` cho FE cũ
- `recipientIds`: optional list UUID, bắt buộc khi `recipientScope=SELECTED_FRIENDS`
- `takenAt`: optional, ISO datetime

### Rule
- nếu không truyền `recipientScope`/`audienceMode`, backend mặc định `ALL_FRIENDS`
- backend luôn tự thêm chính sender vào danh sách recipients
- `ALL_FRIENDS`: backend tự lấy toàn bộ bạn bè `ACCEPTED`, sau đó cộng thêm sender; nếu hiện chưa có bạn bè thì request vẫn thành công với `recipientCount = 1`
- `SELECTED_FRIENDS`: chỉ chấp nhận `recipientIds` thuộc tập bạn bè `ACCEPTED`; sender vẫn được thêm tự động dù FE không truyền lên

### Success response

```json
{
  "id": "photo-uuid",
  "senderId": "sender-uuid",
  "senderDisplayName": "Khánh Nguyễn Kim",
  "senderAvatarUrl": null,
  "imageUrl": "https://res.cloudinary.com/.../photo.jpg",
  "thumbnailUrl": "https://res.cloudinary.com/.../photo.jpg",
  "caption": "Cafe sáng",
  "amount": 45000,
  "recipientScope": "ALL_FRIENDS",
  "recipientCount": 3,
  "status": "READY",
  "mimeType": "image/jpeg",
  "fileSize": 12345,
  "width": 1080,
  "height": 1920,
  "takenAt": "2026-03-12T10:30:00",
  "createdAt": "2026-03-12T10:30:01"
}
```

### Error responses
- `400`: `Ảnh tải lên không hợp lệ`
- `400`: `Số tiền phải lớn hơn hoặc bằng 0`
- `400`: `Vui lòng chọn ít nhất một người nhận`
- `400`: `Danh sách người nhận không hợp lệ hoặc chưa là bạn bè`
- `401`: token thiếu/sai

## `GET /api/v1/photos/feed`

Trả ảnh mà current user là recipient. Vì sender luôn là recipient, user cũng sẽ thấy chính ảnh mình vừa gửi trong feed nếu app dùng endpoint này.

## `GET /api/v1/photos/{photoId}`

Trả chi tiết của một ảnh nếu current user có quyền xem ảnh đó.

### Rule truy cập
- cho phép nếu current user là `sender`
- cho phép nếu current user nằm trong `photo_recipients`
- nếu ảnh không tồn tại, đã bị `DELETED`, hoặc current user không có quyền xem thì backend trả `404` để tránh lộ sự tồn tại của ảnh

### Success response

```json
{
  "id": "photo-uuid",
  "senderId": "sender-uuid",
  "senderDisplayName": "Khánh Nguyễn Kim",
  "senderAvatarUrl": null,
  "imageUrl": "https://res.cloudinary.com/.../photo.jpg",
  "thumbnailUrl": "https://res.cloudinary.com/.../photo.jpg",
  "caption": "Cafe sáng",
  "amount": 45000,
  "recipientScope": "SELECTED_FRIENDS",
  "recipientCount": 2,
  "status": "READY",
  "mimeType": "image/jpeg",
  "fileSize": 12345,
  "width": 1080,
  "height": 1920,
  "takenAt": "2026-03-12T10:30:00",
  "createdAt": "2026-03-12T10:30:01"
}
```

### Error responses
- `401`: token thiếu/sai
- `404`: `Không tìm thấy ảnh`

## `GET /api/v1/photos/my-photos`
## `GET /api/v1/photos/me`

Hai endpoint này cùng trả lịch sử ảnh user đã gửi.

### Feed/My Photos response item

```json
{
  "id": "photo-uuid",
  "senderId": "sender-uuid",
  "senderDisplayName": "Khánh Nguyễn Kim",
  "senderAvatarUrl": null,
  "imageUrl": "https://res.cloudinary.com/.../photo.jpg",
  "thumbnailUrl": "https://res.cloudinary.com/.../photo.jpg",
  "caption": "Cafe sáng",
  "amount": 45000,
  "recipientScope": "SELECTED_FRIENDS",
  "recipientCount": 2,
  "status": "READY",
  "mimeType": "image/jpeg",
  "fileSize": 12345,
  "width": 1080,
  "height": 1920,
  "takenAt": "2026-03-12T10:30:00",
  "createdAt": "2026-03-12T10:30:01"
}
```

## `PATCH /api/v1/photos/{photoId}/expense`

Cho phép chủ ảnh cập nhật metadata chi tiêu sau khi đã upload.

### Request

```http
PATCH /api/v1/photos/{photoId}/expense
Authorization: Bearer <JWT_TOKEN>
Content-Type: application/json
```

```json
{
  "amount": 65000,
  "note": "Lunch with team",
  "categoryId": "0f663a5a-9a62-45a8-9f48-afe8ed3ca5ec"
}
```

### Rule
- chỉ chủ ảnh mới được cập nhật
- `amount` nếu gửi lên phải >= 0
- `categoryId` phải là category đang active và user có quyền dùng (default hoặc own)

---

# 12. Expense module (expanded)

Base path: `/api/v1/expense`

## `GET /api/v1/expense/categories`

Trả danh sách category đang active gồm:
- category hệ thống mặc định (`isDefault=true`, `user_id=null`)
- category riêng của current user (`isDefault=false`)

### Response item

```json
{
  "id": "uuid",
  "name": "Food",
  "icon": "restaurant",
  "color": "#FF8A65",
  "isDefault": true,
  "isActive": true
}
```

## `POST /api/v1/expense/categories`

Tạo category cá nhân.

```json
{
  "name": "Coffee",
  "icon": "coffee",
  "color": "#795548"
}
```

## `PATCH /api/v1/expense/categories/{categoryId}`

Update category cá nhân (không update category mặc định hệ thống).

```json
{
  "name": "Cafe",
  "icon": "coffee",
  "color": "#6D4C41",
  "isActive": true
}
```

## `GET /api/v1/expense/budgets/{monthKey}`

`monthKey` theo định dạng `yyyyMM`, ví dụ `202603`.

```json
{
  "monthKey": "202603",
  "amountLimit": 5000000,
  "alertThresholdPct": 80,
  "spent": 1250000,
  "remaining": 3750000,
  "exceeded": false
}
```

Nếu chưa set budget, `amountLimit/alertThresholdPct/remaining` sẽ là `null`.

## `PUT /api/v1/expense/budgets/{monthKey}`

```json
{
  "amountLimit": 5000000,
  "alertThresholdPct": 80
}
```

## `GET /api/v1/expense/entries?monthKey=202603`

Trả page các khoản chi (dữ liệu lấy từ photo có `amount > 0`).

### Response item

```json
{
  "photoId": "uuid",
  "imageUrl": "https://...",
  "thumbnailUrl": "https://...",
  "amount": 65000,
  "note": "Lunch with team",
  "categoryId": "uuid",
  "categoryName": "Food",
  "takenAt": "2026-03-16T12:30:00",
  "createdAt": "2026-03-16T12:30:02"
}
```

## `GET /api/v1/expense/summary?monthKey=202603`

```json
{
  "monthKey": "202603",
  "totalSpent": 1250000,
  "budgetLimit": 5000000,
  "remaining": 3750000,
  "budgetExceeded": false,
  "percentUsed": 25,
  "byCategory": [
    {
      "categoryId": "uuid",
      "categoryName": "Food",
      "totalAmount": 650000
    }
  ]
}
```

