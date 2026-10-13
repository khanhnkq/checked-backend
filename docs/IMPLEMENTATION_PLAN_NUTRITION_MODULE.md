# Kế Hoạch Tinh Gọn: Module Quản Lý Calo (Calorie Tracking Module)

> **Dự án**: Checked (Spring Boot Backend + iOS Native SwiftUI)  
> **Định hướng**: Tối giản 100% (Minimalist Calorie-Only) — Không macro phức tạp, không rườm rà  
> **Tác giả**: Nguyen Kim Quoc Khanh  
> **Ngày lập**: 12/10/2026

---

## 1. Tôn Chỉ Thiết Kế (Design Philosophy)

* **Chỉ cần Calo (kcal)**: Loại bỏ triệt để Protein, Carbs, Fat, Fiber, Nước. Người dùng chỉ cần biết:
  1. Mục tiêu calo mỗi ngày (Ví dụ: `2,000 kcal`).
  2. Hôm nay đã nạp bao nhiêu calo (từ các món ăn đã chụp/ăn).
  3. Còn lại bao nhiêu calo (để giữ dáng / không tăng cân).
* **Bài post cực sạch**: Trên ảnh khoảnh khắc của bạn bè chỉ hiển thị đúng **1 nhãn Calo duy nhất** (`🔥 520 kcal`), không đè nhiều tag che lấp ảnh.
* **Tự động hóa từ AI Camera**: Chụp ảnh món ăn → Gemini AI ước tính calo → Người dùng duyệt → Vừa đăng feed cho bạn bè vừa tự động cộng vào Calo trong ngày.
* **Tốc độ 0ms (Stale-While-Revalidate)**: Toàn bộ dữ liệu hiển thị tức thì, hỗ trợ offline hoàn toàn.

---

## 2. Thiết Kế Cơ Sở Dữ Liệu Tinh Gọn (Backend Database Schema)

### Migration Flyway: `V20__create_calorie_module.sql`

```sql
-- ============================================================
-- V20: Module Quản Lý Calo Tối Giản (Calorie Goals & Logs)
-- ============================================================

-- 1. Bảng mục tiêu Calo hàng ngày của người dùng
CREATE TABLE IF NOT EXISTS calorie_goals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    daily_target_calories INT NOT NULL DEFAULT 2000,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_calorie_target_positive CHECK (daily_target_calories > 0)
);

CREATE INDEX IF NOT EXISTS idx_calorie_goals_user ON calorie_goals(user_id);

-- 2. Bảng nhật ký Calo bữa ăn (Calorie Logs)
CREATE TABLE IF NOT EXISTS calorie_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    photo_id UUID REFERENCES photos(id) ON DELETE SET NULL, -- Liên kết ảnh chụp nếu có
    calories INT NOT NULL,
    meal_type VARCHAR(20) NOT NULL DEFAULT 'SNACK', -- 'BREAKFAST', 'LUNCH', 'DINNER', 'SNACK'
    dish_name VARCHAR(150),
    note VARCHAR(150),
    logged_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_calorie_logs_calories CHECK (calories > 0),
    CONSTRAINT chk_calorie_logs_type CHECK (meal_type IN ('BREAKFAST', 'LUNCH', 'DINNER', 'SNACK'))
);

CREATE INDEX IF NOT EXISTS idx_calorie_logs_user_logged ON calorie_logs(user_id, logged_at DESC);
CREATE INDEX IF NOT EXISTS idx_calorie_logs_photo ON calorie_logs(photo_id);
```

---

## 3. Kiến Trúc Backend (`checked-backend`)

### 3.1. Cấu trúc Package
```
com.codegym.locketclone.calorie
├── CalorieGoal.java              // Entity mục tiêu calo ngày
├── CalorieLog.java               // Entity nhật ký calo (từ ảnh chụp hoặc thêm tay)
├── MealType.java                 // Enum: BREAKFAST, LUNCH, DINNER, SNACK
├── CalorieGoalRepository.java
├── CalorieLogRepository.java
├── CalorieService.java
├── CalorieServiceImpl.java
├── CalorieController.java
└── dto
    ├── CalorieGoalRequestDTO.java
    ├── CalorieGoalResponseDTO.java
    ├── CreateCalorieLogRequestDTO.java
    ├── CalorieLogItemDTO.java
    ├── DailyCalorieSummaryDTO.java
    └── MonthlyCalorieSummaryDTO.java
```

### 3.2. Tự Động Phân Loại Bữa Ăn Theo Giờ (Meal Type Auto-Detect)
Khi chụp ảnh đồ ăn, hệ thống tự động gán loại bữa ăn theo thời điểm chụp:
* `05:00 - 10:29`: **Bữa sáng** (`BREAKFAST`)
* `10:30 - 14:29`: **Bữa trưa** (`LUNCH`)
* `14:30 - 16:59`: **Bữa phụ chiều** (`SNACK`)
* `17:00 - 21:29`: **Bữa tối** (`DINNER`)
* `21:30 - 04:59`: **Ăn khuya / Ăn vặt** (`SNACK`)

### 3.3. Tự Động Tạo `CalorieLog` Khi Đăng Bài Có Calo
Trong `PhotoServiceImpl.java`:
* Khi người dùng upload ảnh qua `/api/v1/photos` có trường `calories` (được Gemini AI nhận diện hoặc chỉnh sửa):
  * Tự động lưu 1 dòng vào `calorie_logs` với `photo_id = photo.getId()`, `calories = photo.getCalories()`, `dish_name = photo.getDishName()`.

---

## 4. Đặc Tả REST APIs (`/api/v1/calories/...`)

### 4.1. Mục Tiêu Calo (Calorie Goal)
* **`GET /api/v1/calories/goal`**: Lấy mục tiêu calo hàng ngày (Mặc định 2,000 kcal).
* **`PUT /api/v1/calories/goal`**: Cập nhật mục tiêu:
  ```json
  { "dailyTargetCalories": 1800 }
  ```

### 4.2. Tổng Quan Calo Ngày (Daily Summary)
* **`GET /api/v1/calories/daily?date=2026-10-12`**:
  ```json
  {
    "date": "2026-10-12",
    "dailyTargetCalories": 2000,
    "consumedCalories": 1450,
    "remainingCalories": 550,
    "isExceeded": false,
    "percentage": 72,
    "meals": [
      {
        "id": "uuid-1",
        "mealType": "BREAKFAST",
        "dishName": "Bánh mì ốp la",
        "calories": 350,
        "thumbnailUrl": "https://.../banh_mi.jpg",
        "loggedAt": "2026-10-12T07:30:00"
      },
      {
        "id": "uuid-2",
        "mealType": "LUNCH",
        "dishName": "Cơm tấm sườn",
        "calories": 750,
        "thumbnailUrl": "https://.../com_tam.jpg",
        "loggedAt": "2026-10-12T12:15:00"
      },
      {
        "id": "uuid-3",
        "mealType": "DINNER",
        "dishName": "Salad ức gà",
        "calories": 350,
        "thumbnailUrl": null,
        "loggedAt": "2026-10-12T18:45:00"
      }
    ]
  }
  ```

### 4.3. Xu Hướng Calo Tháng (Monthly Calorie Trend)
* **`GET /api/v1/calories/summary?month=202610`**:
  ```json
  {
    "monthKey": "202610",
    "dailyTargetCalories": 2000,
    "averageDailyCalories": 1780,
    "daysOnTrack": 11,
    "days": [
      { "day": 1, "calories": 1850 },
      { "day": 2, "calories": 1920 },
      { "day": 3, "calories": 2150 }
    ]
  }
  ```

### 4.4. Log Calo Nhanh & Xóa Bản Ghi
* **`POST /api/v1/calories/logs`**: Dành cho trường hợp ăn nhưng quên chụp ảnh.
  ```json
  {
    "calories": 250,
    "mealType": "SNACK",
    "dishName": "Trà sữa ít đường"
  }
  ```
* **`DELETE /api/v1/calories/logs/{id}`**: Xóa một bản ghi calo.

---

## 5. Thiết Kế Bố Trí Calo Trên Bài Post & Màn Hình iOS

### 5.1. Bố Trí Trên Ảnh Post (Cực Gọn, Không Che Ảnh)
Thay vì xếp chồng nhiều tầng tag làm nát bức ảnh, ta áp dụng quy tắc **1 Nhãn Calo Duy Nhất**:
* **Vị trí**: Góc trên bên trái ảnh.
* **Quy cách**: Viên nhộng siêu nhỏ (Compact Pill), nền đen mờ 50% hoặc Surface trắng bo tròn, chữ đen/trắng 12pt đậm:
  ```
  ┌──────────────────────────────────────────────┐
  │ [ 🔥 520 kcal ]                              │
  │                                              │
  │                                              │
  │                 (ẢNH MÓN ĂN)                 │
  │                                              │
  │                                              │
  │ ┌──────────────────────────────────────────┐ │
  │ │  Phở bò tái nạm lúc trưa                 │ │
  │ └──────────────────────────────────────────┘ │
  └──────────────────────────────────────────────┘
  ```
* Nếu bài post có cả tiền hóa đơn: Gộp thành 1 dòng duy nhất: `[ 💳 45k • 🔥 520 kcal ]`. Tuyệt đối không tạo 2 hàng tag riêng lẻ.

---

### 5.2. Màn Hình Calorie Dashboard (`CalorieDashboardView.swift`)
Phong cách **100% Flat** trên nền **Oat Milk Canvas** (`AppColors.canvas`), thẻ nền trắng bo tròn 24pt:

1. **Thẻ Hero Calorie Card (Giống Thẻ Ngân Sách)**:
   * **Số lớn (34pt Heavy)**: `1,450 kcal` (Tổng calo đã nạp hôm nay).
   * **Thanh tiến độ**:
     * Chạy màu xanh Matcha Glow (`AppColors.primary`) khi đang trong hạn mức.
     * Chuyển sang màu đỏ (`AppColors.error`) khi vượt mục tiêu.
   * **Thông số phụ**: *Còn 550 kcal (Mục tiêu 2,000 kcal)*.
   * Chạm trực tiếp vào thanh để mở sheet thiết lập mục tiêu calo.
2. **4 Hàng Bữa Ăn Trong Ngày**:
   * 🌅 **Sáng**: Liệt kê các món sáng kèm thumbnail ảnh vuông (nếu có) + số calo.
   * ☀️ **Trưa**: Món trưa + số calo.
   * ☕ **Phụ**: Món phụ + số calo.
   * 🌙 **Tối**: Món tối + số calo.
   * Nút `+` nhỏ ở góc mỗi bữa để nhập nhanh calo nếu không có ảnh.
3. **Biểu đồ cột tháng (31 ngày)**:
   * 31 cột ngày thanh mảnh, hiển thị chiều cao theo calo nạp vào.
   * 1 đường vạch kẻ ngang thể hiện mốc mục tiêu (`Target Line 2,000 kcal`). Cột nào vượt mốc đổi màu cam/đỏ, cột đạt chuẩn màu xám nhạt/Matcha.

---

### 5.3. Tích Hợp Trên Trang Cá Nhân (`ProfileView.swift`)
* Thêm một thẻ **Calo hôm nay** (dưới thẻ Ngân sách):
  * Icon lửa cam `flame.fill`.
  * Text: *1,450 / 2,000 kcal* kèm thanh tiến độ mini.
  * Chạm vào thẻ chuyển mượt mà sang `CalorieDashboardView`.

---

## 6. Lộ Trình Triển Khai Từng Bước

| Bước | Module | Nhiệm vụ |
| :---: | :---: | :--- |
| **B1** | **Backend DB** | Viết Flyway migration `V20__create_calorie_module.sql` (bảng `calorie_goals`, `calorie_logs`). |
| **B2** | **Backend Logic** | Viết Entity, Repository, Service và Controller `/api/v1/calories/*`. |
| **B3** | **Backend Hook** | Hook trong `PhotoServiceImpl`: Tự động tạo `calorie_logs` khi upload ảnh có `calories > 0`. |
| **B4** | **iOS Data** | Viết `CalorieRepositoryProtocol`, `CalorieRepository` kèm Stale-While-Revalidate Caching 0ms. |
| **B5** | **iOS Post Clean** | Tinh gọn tag trên ảnh trong `ImagePreviewView` và `HistoryPhotoGridView`: Chỉ còn 1 tag Calo duy nhất. |
| **B6** | **iOS Dashboard** | Dựng `CalorieDashboardView` (Hero Calorie Bar, 4 Bữa ăn, Biểu đồ cột tháng) và gắn vào `ProfileView`. |
| **B7** | **Test iPad** | Chạy `./run_ipad.sh` biên dịch thực tế, kiểm tra độ mượt 0ms và chế độ offline. |
