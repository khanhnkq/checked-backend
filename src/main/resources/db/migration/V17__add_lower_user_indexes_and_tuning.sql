-- ============================================================
-- V17: Add Functional Lower Indexes on Users and Photo Feed Index
-- ============================================================

-- 1. Functional Unique Indexes trên LOWER(email) và LOWER(username)
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_lower_email ON users (LOWER(email));
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_lower_username ON users (LOWER(username));

-- 2. Partial Index cho Feed ảnh chưa bị xóa sắp xếp theo thời gian tạo
CREATE INDEX IF NOT EXISTS idx_photos_feed_status_created ON photos (created_at DESC) WHERE status <> 'DELETED';
