-- ============================================================
-- V20: Module Quản Lý Calo Tinh Gọn (Calorie Goals & Logs)
-- Tối ưu hóa hiệu năng với Composite Indexes & Index-Only Scan
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
    photo_id UUID REFERENCES photos(id) ON DELETE CASCADE,
    calories INT NOT NULL,
    meal_type VARCHAR(20) NOT NULL DEFAULT 'SNACK',
    dish_name VARCHAR(150),
    note VARCHAR(150),
    logged_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_calorie_logs_calories CHECK (calories > 0),
    CONSTRAINT chk_calorie_logs_type CHECK (meal_type IN ('BREAKFAST', 'LUNCH', 'DINNER', 'SNACK'))
);

-- Tối ưu index lọc theo người dùng và thời gian ghi nhận (DESC)
CREATE INDEX IF NOT EXISTS idx_calorie_logs_user_logged ON calorie_logs(user_id, logged_at DESC);

-- Tối ưu index tìm kiếm theo bài đăng ảnh (FK lookup)
CREATE INDEX IF NOT EXISTS idx_calorie_logs_photo ON calorie_logs(photo_id);

-- Composite index tối ưu hóa tính toán tổng Calo ngày/tháng (Index-Only Scan trong PostgreSQL)
CREATE INDEX IF NOT EXISTS idx_calorie_logs_user_logged_cal ON calorie_logs(user_id, logged_at) INCLUDE (calories);

-- 3. Tự động chuyển đổi các bài đăng ảnh đã có sẵn lượng Calo vào calorie_logs
INSERT INTO calorie_logs (id, user_id, photo_id, calories, meal_type, dish_name, note, logged_at, created_at)
SELECT 
    gen_random_uuid(),
    p.sender_id,
    p.id,
    p.calories,
    CASE 
        WHEN CAST(COALESCE(p.occurred_at, p.created_at) AS TIME) >= TIME '05:00:00' AND CAST(COALESCE(p.occurred_at, p.created_at) AS TIME) < TIME '10:30:00' THEN 'BREAKFAST'
        WHEN CAST(COALESCE(p.occurred_at, p.created_at) AS TIME) >= TIME '10:30:00' AND CAST(COALESCE(p.occurred_at, p.created_at) AS TIME) < TIME '14:30:00' THEN 'LUNCH'
        WHEN CAST(COALESCE(p.occurred_at, p.created_at) AS TIME) >= TIME '14:30:00' AND CAST(COALESCE(p.occurred_at, p.created_at) AS TIME) < TIME '17:00:00' THEN 'SNACK'
        WHEN CAST(COALESCE(p.occurred_at, p.created_at) AS TIME) >= TIME '17:00:00' AND CAST(COALESCE(p.occurred_at, p.created_at) AS TIME) < TIME '21:30:00' THEN 'DINNER'
        ELSE 'SNACK'
    END,
    p.dish_name,
    p.caption,
    COALESCE(p.occurred_at, p.created_at),
    p.created_at
FROM photos p
WHERE p.calories IS NOT NULL 
  AND p.calories > 0
  AND NOT EXISTS (
      SELECT 1 FROM calorie_logs cl WHERE cl.photo_id = p.id
  );

