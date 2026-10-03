-- ============================================================
-- V15: Harden schema, increase avatar length & bidirectional friendship index
-- ============================================================

-- 1. Thêm cột đếm số lần nhập sai OTP vào bảng users
ALTER TABLE users ADD COLUMN IF NOT EXISTS otp_failed_attempts INTEGER NOT NULL DEFAULT 0;

-- 2. Tăng độ dài cột avatar_url lên 500 ký tự (tránh DataTruncationException với S3 / CDN dài)
ALTER TABLE users ALTER COLUMN avatar_url TYPE VARCHAR(500);

-- 3. Tạo chỉ mục Unique chống trùng lặp kết bạn hai chiều (A, B) và (B, A)
CREATE UNIQUE INDEX IF NOT EXISTS uk_friendships_bidirectional
    ON friendships (LEAST(user_id, friend_id), GREATEST(user_id, friend_id));
