-- ============================================================
-- V16: Harden photo URL length, category constraints & friendship indexes
-- ============================================================

-- 1. Tăng độ dài image_url trong bảng photos khớp với Photo.java (@Column length=500)
ALTER TABLE photos ALTER COLUMN image_url TYPE VARCHAR(500);

-- 2. Đảm bảo transaction_type trong categories là NOT NULL
UPDATE categories SET transaction_type = 'EXPENSE' WHERE transaction_type IS NULL;
ALTER TABLE categories ALTER COLUMN transaction_type SET NOT NULL;

-- 3. Tạo index bổ sung cho user_id, friend_id và status phục vụ truy vấn bạn bè 2 chiều
CREATE INDEX IF NOT EXISTS idx_friendships_user_status ON friendships(user_id, status);
CREATE INDEX IF NOT EXISTS idx_friendships_friend_status ON friendships(friend_id, status);

-- 4. Tạo index tối ưu hóa thống kê reaction theo thời gian
CREATE INDEX IF NOT EXISTS idx_photo_reactions_photo_created ON photo_reactions(photo_id, created_at DESC);

-- 5. Seed danh mục mặc định cho giao dịch INCOME (Thu nhập)
INSERT INTO categories (id, name, icon, color, user_id, is_default, is_active, transaction_type)
SELECT uuid_generate_v4(), 'Salary', 'payments', '#66BB6A', NULL, TRUE, TRUE, 'INCOME'
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE user_id IS NULL AND LOWER(name) = 'salary');

INSERT INTO categories (id, name, icon, color, user_id, is_default, is_active, transaction_type)
SELECT uuid_generate_v4(), 'Bonus', 'redeem', '#42A5F5', NULL, TRUE, TRUE, 'INCOME'
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE user_id IS NULL AND LOWER(name) = 'bonus');

INSERT INTO categories (id, name, icon, color, user_id, is_default, is_active, transaction_type)
SELECT uuid_generate_v4(), 'Other Income', 'account_balance_wallet', '#FFA726', NULL, TRUE, TRUE, 'INCOME'
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE user_id IS NULL AND LOWER(name) = 'other income');
