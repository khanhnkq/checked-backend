-- ==========================================
-- 1. Chuẩn bị helper cho UUID và migrate USERS sang schema mới
-- ==========================================
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

ALTER TABLE users ADD COLUMN IF NOT EXISTS email VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS username VARCHAR(50);
ALTER TABLE users ADD COLUMN IF NOT EXISTS first_name VARCHAR(50);
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_name VARCHAR(50);
ALTER TABLE users ADD COLUMN IF NOT EXISTS otp_code VARCHAR(6);
ALTER TABLE users ADD COLUMN IF NOT EXISTS otp_expires_at TIMESTAMP;
ALTER TABLE users ADD COLUMN IF NOT EXISTS is_gold_member BOOLEAN DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS is_verified BOOLEAN DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_url VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS password VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

UPDATE users
SET email = LOWER('user_' || SUBSTRING(REPLACE(id::text, '-', '') FROM 1 FOR 12) || '@placeholder.local')
WHERE email IS NULL OR BTRIM(email) = '';

UPDATE users
SET username = LOWER('user_' || SUBSTRING(REPLACE(id::text, '-', '') FROM 1 FOR 12))
WHERE username IS NULL OR BTRIM(username) = '';

UPDATE users
SET first_name = LEFT(BTRIM(display_name), 50)
WHERE (first_name IS NULL OR BTRIM(first_name) = '')
  AND display_name IS NOT NULL
  AND BTRIM(display_name) <> '';

UPDATE users
SET password = 'LOCKET_PENDING_PASSWORD'
WHERE password IS NULL OR BTRIM(password) = '';

ALTER TABLE users ALTER COLUMN avatar_url TYPE VARCHAR(255);

UPDATE users
SET is_verified = FALSE
WHERE is_verified IS NULL;

UPDATE users
SET is_gold_member = FALSE
WHERE is_gold_member IS NULL;

ALTER TABLE users ALTER COLUMN email SET NOT NULL;
ALTER TABLE users ALTER COLUMN username SET NOT NULL;
ALTER TABLE users ALTER COLUMN password SET NOT NULL;
ALTER TABLE users ALTER COLUMN is_verified SET NOT NULL;
ALTER TABLE users ALTER COLUMN is_gold_member SET NOT NULL;
ALTER TABLE users ALTER COLUMN is_verified SET DEFAULT FALSE;
ALTER TABLE users ALTER COLUMN is_gold_member SET DEFAULT FALSE;
ALTER TABLE users ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'users_email_key') THEN
        ALTER TABLE users ADD CONSTRAINT users_email_key UNIQUE (email);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'users_username_key') THEN
        ALTER TABLE users ADD CONSTRAINT users_username_key UNIQUE (username);
    END IF;
END $$;

ALTER TABLE users DROP COLUMN IF EXISTS phone_number;
ALTER TABLE users DROP COLUMN IF EXISTS display_name;
ALTER TABLE users DROP COLUMN IF EXISTS fcm_token;
ALTER TABLE users DROP COLUMN IF EXISTS updated_at;
ALTER TABLE users DROP COLUMN IF EXISTS profile_completed;

-- ==========================================
-- 2. Chuyển FRIENDSHIPS từ composite key cũ sang schema mới
-- ==========================================
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'friendships'
          AND column_name = 'user_id_1'
    ) THEN
        ALTER TABLE friendships RENAME TO friendships_legacy;
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS friendships (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    friend_id UUID NOT NULL REFERENCES users(id),
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_friendship UNIQUE (user_id, friend_id)
);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_name = 'friendships_legacy'
    ) THEN
        INSERT INTO friendships (id, user_id, friend_id, status, created_at)
        SELECT uuid_generate_v4(), fl.user_id_1, fl.user_id_2, fl.status, fl.created_at
        FROM friendships_legacy fl
        ON CONFLICT (user_id, friend_id) DO NOTHING;

        DROP TABLE friendships_legacy;
    END IF;
END $$;

-- ==========================================
-- 3. Categories / Photos theo schema mới
-- ==========================================
CREATE TABLE IF NOT EXISTS categories (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    icon_name VARCHAR(50),
    color_code VARCHAR(20)
);

ALTER TABLE photos ALTER COLUMN image_url TYPE VARCHAR(255);
ALTER TABLE photos ALTER COLUMN caption TYPE TEXT;
ALTER TABLE photos ADD COLUMN IF NOT EXISTS amount DECIMAL(15, 2) DEFAULT 0.00;
ALTER TABLE photos ADD COLUMN IF NOT EXISTS category_id UUID;
ALTER TABLE photos ADD COLUMN IF NOT EXISTS is_private BOOLEAN DEFAULT FALSE;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_photo_category') THEN
        ALTER TABLE photos ADD CONSTRAINT fk_photo_category FOREIGN KEY (category_id) REFERENCES categories(id);
    END IF;
END $$;

-- ==========================================
-- 4. Bảng mới: reactions + direct messages
-- ==========================================
CREATE TABLE IF NOT EXISTS photo_reactions (
    id UUID PRIMARY KEY,
    photo_id UUID NOT NULL REFERENCES photos(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id),
    reaction_type VARCHAR(20) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_user_photo_reaction UNIQUE (photo_id, user_id)
);

CREATE TABLE IF NOT EXISTS direct_messages (
    id UUID PRIMARY KEY,
    sender_id UUID NOT NULL REFERENCES users(id),
    receiver_id UUID NOT NULL REFERENCES users(id),
    photo_id UUID REFERENCES photos(id) ON DELETE SET NULL,
    content TEXT NOT NULL,
    is_read BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ==========================================
-- 5. Dọn bảng legacy không còn dùng + index mới
-- ==========================================
DROP TABLE IF EXISTS photo_receivers;
DROP TABLE IF EXISTS budgets;

CREATE INDEX IF NOT EXISTS idx_photos_sender_created ON photos(sender_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_direct_messages_participants ON direct_messages(sender_id, receiver_id, created_at DESC);
