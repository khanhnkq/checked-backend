-- 1. Tạo bảng Danh mục chi tiêu (Categories)
CREATE TABLE categories (
                            id UUID PRIMARY KEY,
                            name VARCHAR(100) NOT NULL,
                            icon_name VARCHAR(50),      -- Để load icon Kawaii (vd: burger, cart)
                            color_code VARCHAR(20)      -- Màu nền Solid (vd: #FFD700)
);

-- 2. Tạo bảng Ngân sách hàng tháng (Budgets)
CREATE TABLE budgets (
                         id UUID PRIMARY KEY,
                         user_id UUID NOT NULL,
                         month INT NOT NULL,
                         year INT NOT NULL,
                         limit_amount DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
                         CONSTRAINT fk_budget_user FOREIGN KEY (user_id) REFERENCES users(id),
                         CONSTRAINT unique_user_month_year UNIQUE (user_id, month, year) -- Mỗi user chỉ có 1 ngân sách/tháng
);

-- 3. Nâng cấp bảng Photos hiện tại
ALTER TABLE photos
    ADD COLUMN amount DECIMAL(15, 2) DEFAULT 0.00,
    ADD COLUMN is_private BOOLEAN DEFAULT FALSE,
    ADD COLUMN category_id UUID,
    ADD CONSTRAINT fk_photo_category FOREIGN KEY (category_id) REFERENCES categories(id);

-- 4. Đánh Index để truy vấn Lịch và Thống kê siêu tốc
CREATE INDEX idx_photos_sender_created ON photos(sender_id, created_at);
CREATE INDEX idx_photos_category ON photos(category_id);

ALTER  TABLE users ADD COLUMN is_verified BOOLEAN DEFAULT FALSE;