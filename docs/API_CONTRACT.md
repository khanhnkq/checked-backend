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
