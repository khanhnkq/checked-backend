-- Thêm avatar_url và đảm bảo password nullable
ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_url VARCHAR(500);
ALTER TABLE users ALTER COLUMN password DROP NOT NULL;
