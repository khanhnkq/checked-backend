# Hướng Dẫn Tích Hợp API Contract Tự Động Dành Cho Frontend (Web & Mobile)

> **Tài liệu**: Hướng dẫn sử dụng OpenAPI 3.0 / Swagger UI và các bộ công cụ tự động sinh mã nguồn (Code-Gen TypeScript / Dart) từ API Contract của `checked-backend`.  
> **Phiên bản**: 1.0.0  

---

## 1. 🌐 ĐỊA CHỈ TRUY CẬP TRỰC TIẾP (INTERACTIVE SWAGGER UI)

Khi Backend đang chạy (`./gradlew bootRun` hoặc Docker):

| Giao thức | Đường dẫn | Mục đích |
| :--- | :--- | :--- |
| **Swagger UI** | [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html) | Giao diện tương tác, tra cứu API, xem schema DTOs và test trực tiếp |
| **OpenAPI 3 JSON** | [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs) | File schema JSON chuẩn OpenAPI 3.1 phục vụ import công cụ tự động |
| **OpenAPI 3 YAML** | [http://localhost:8080/v3/api-docs.yaml](http://localhost:8080/v3/api-docs.yaml) | File schema YAML chuẩn OpenAPI 3.1 |

### 🔑 Hướng dẫn Test API có bảo mật (JWT) trên Swagger UI:
1. Gọi API `POST /api/v1/auth/login` (hoặc `/api/v1/auth/verify`) để lấy `accessToken`.
2. Bấm nút **Authorize 🔓** ở góc trên cùng bên phải giao diện Swagger UI.
3. Dán chuỗi Token vào ô `Value` rồi bấm **Authorize**.
4. Toàn bộ các API yêu cầu xác thực sẽ tự động đính kèm header `Authorization: Bearer <token>`.

---

## 2. 📂 FILE CONTRACT TĨNH (OFFLINE CONTRACT)

Hai file contract đã được trích xuất sẵn trong repository tại thư mục `docs/`:
- `docs/openapi.json` (33 KB)
- `docs/openapi.yaml` (43 KB)

Frontend có thể dùng trực tiếp 2 file này mà không cần chờ Backend khởi động.

---

## 3. 🤖 TỰ ĐỘNG SINH CODE CLIENT & TYPES (CODE-GEN)

### 🔹 Cách 1: Cho React / Next.js / Vue (TypeScript + Axios / Fetch / TanStack Query)

#### Dùng `openapi-typescript` (Sinh Types cực nhẹ):
```bash
# Cài đặt
npm install -D openapi-typescript

# Sinh toàn bộ TypeScript interfaces từ file contract
npx openapi-typescript docs/openapi.yaml -o src/types/api-schema.d.ts
```

#### Dùng `orval` (Sinh cả Types lẫn TanStack React Query Hooks):
```bash
npm install -D orval
```
Tạo file `orval.config.js`:
```javascript
module.exports = {
  locketApi: {
    input: './docs/openapi.yaml',
    output: {
      mode: 'tags-split',
      target: 'src/api/generated',
      schemas: 'src/api/model',
      client: 'react-query', // hoặc 'axios' / 'fetch'
    },
  },
};
```
Chạy lệnh gen:
```bash
npx orval
```

---

### 🔹 Cách 2: Cho Mobile (Flutter / Dart)

Dùng `@openapitools/openapi-generator-cli` với generator `dart-dio`:
```bash
npx @openapitools/openapi-generator-cli generate \
  -i docs/openapi.yaml \
  -g dart-dio \
  -o lib/api/generated/ \
  --additional-properties=pubName=locket_api_client
```
Lệnh trên sẽ tự động sinh đầy đủ:
- Models (DTO classes có `fromJson`, `toJson`).
- API Clients (Dio networking với interceptors hỗ trợ JWT token).

---

## 4. 🗺️ TỔNG HỢP CÁC NHÓM API (TAGS & ENDPOINTS)

### 1. `Auth` (Xác thực người dùng)
- `POST /api/v1/auth/register`: Đăng ký tài khoản (email, username, password). Gửi mã OTP 6 chữ số bất đồng bộ qua email.
- `POST /api/v1/auth/verify`: Xác minh mã OTP. Chống brute-force: tối đa 5 lần thử sai, sau đó khóa và xóa mã.
- `POST /api/v1/auth/login`: Đăng nhập, nhận Access Token JWT.

### 2. `Users` (Hồ sơ & Avatar Garage S3)
- `GET /api/v1/users/me`: Lấy thông tin tài khoản đang đăng nhập.
- `PATCH /api/v1/users/me/profile`: Cập nhật hồ sơ bước onboarding (họ tên, username).
- `PATCH /api/v1/users/me/settings/personal-info`: Cập nhật thông tin cá nhân trong Cài đặt.
- `PATCH /api/v1/users/me/settings/avatar`: Upload ảnh đại diện mới (`multipart/form-data`). Hệ thống tự động nén về 500x500 square và upload lên Garage S3.
- `GET /api/v1/users/{id}`: Xem thông tin user theo ID.

### 3. `Photos` (Chia sẻ ảnh khoảnh khắc & Chi tiêu)
- `POST /api/v1/photos`: Upload ảnh khoảnh khắc (`multipart/form-data`):
  - Tham số: `file`, `caption`, `amount`, `transactionType` (`EXPENSE` / `INCOME`), `note`, `categoryId`, `recipientScope` (`ALL_FRIENDS` / `SELECTED_FRIENDS`), `recipientIds`, `takenAt`.
  - Hệ thống tự động nén ảnh gốc (tối đa 1920px) và sinh ảnh thumbnail vuông (320x320) lưu lên Garage S3.
- `GET /api/v1/photos/my-photos`: Danh sách ảnh của tôi (phân trang Page).
- `GET /api/v1/photos/feed`: Feed ảnh bạn bè (phân trang Slice cuộn vô tận). Hỗ trợ lọc theo `friendId`.
- `GET /api/v1/photos/{photoId}`: Xem chi tiết 1 ảnh.
- `PATCH /api/v1/photos/{photoId}/transaction`: Sửa toàn bộ thông tin tài chính/caption của ảnh.
- `PATCH /api/v1/photos/{photoId}/expense`: Sửa nhanh số tiền/danh mục/ghi chú.
- `DELETE /api/v1/photos/{photoId}`: Đánh dấu xóa ảnh và tự động xóa file gốc + thumbnail trên Garage S3.
- `PUT /api/v1/photos/{photoId}/reactions/me`: Thả cảm xúc (`LIKE`, `LOVE`, `HAHA`, `WOW`, `SAD`, `ANGRY`).
- `DELETE /api/v1/photos/{photoId}/reactions/me`: Xóa cảm xúc đã thả.
- `GET /api/v1/photos/{photoId}/reactions/summary`: Xem thống kê số lượng từng loại cảm xúc và người thả.

### 4. `Expenses & Finance` (Quản lý chi tiêu & Dòng tiền)
- `GET /api/v1/expense/categories`: Danh sách danh mục thu/chi.
- `POST /api/v1/expense/categories`: Tạo danh mục tùy chỉnh mới.
- `PATCH /api/v1/expense/categories/{categoryId}`: Đổi tên/icon danh mục.
- `GET /api/v1/expense/budgets/{monthKey}` & `PUT /api/v1/expense/budgets/{monthKey}`: Quản lý ngân sách tháng (monthKey format `YYYYMM`, ví dụ `202610`).
- `GET /api/v1/expense/entries`: Danh sách chi tiêu theo tháng (phân trang).
- `GET /api/v1/expense/entries/by-period`: Lọc giao dịch linh hoạt theo `DAY`, `WEEK`, `MONTH`, `YEAR`.
- `GET /api/v1/expense/summary`: Báo cáo chi tiêu tháng (tổng tiền, ngân sách còn lại, tỷ lệ dùng).
- `GET /api/v1/expense/cashflow`: Dòng tiền tháng (tổng thu, tổng chi, số dư ròng).
- `GET /api/v1/expense/summary/yearly`: Dòng tiền 12 tháng trong năm (`year=2026`, đã tối ưu 1 query gom nhóm).
- `GET /api/v1/expense/categories/top`: Top N danh mục chi tiêu nhiều nhất.
- `GET /api/v1/expense/savings-goals/{monthKey}` & `PUT`: Mục tiêu tiết kiệm tháng.

### 5. `Friendships` & `Friend Invites` (Kết bạn bảo mật)
- `GET /api/v1/friendships`: Danh sách tất cả bạn bè hiện tại.
- `POST /api/v1/friend-invite-links`: Tạo link mời kết bạn mới (kèm token, TTL và giới hạn lượt dùng).
- `GET /api/v1/friend-invite-links/current`: Lấy link mời hiện tại.
- `DELETE /api/v1/friend-invite-links/current`: Hủy link mời hiện tại.
- `POST /api/v1/friend-invite-links/accept`: Chấp nhận kết bạn qua link mời (chống race condition bằng database pessimistic lock).

---

## 5. 🖼️ QUY CHUẨN URL HÌNH ẢNH GARAGE S3
Frontend đọc ảnh trực tiếp qua URL công khai do Backend trả về:
- **Ảnh khoảnh khắc gốc**: `http://localhost:3902/locket-photos/photos/YYYY/MM/<uuid>.jpg`
- **Ảnh thumbnail (Widget/Grid)**: `http://localhost:3902/locket-photos/photos/YYYY/MM/<uuid>_thumb.jpg`
- **Ảnh đại diện avatar**: `http://localhost:3902/locket-photos/avatars/<uuid>.jpg`
