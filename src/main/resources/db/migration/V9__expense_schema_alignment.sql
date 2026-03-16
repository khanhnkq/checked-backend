-- Align expense schema with API contract (categories, budgets, photo expense metadata)

-- 1) Categories: owner-scoped + active/default flags + naming aligned to contract
ALTER TABLE categories ADD COLUMN IF NOT EXISTS user_id UUID REFERENCES users(id) ON DELETE CASCADE;
ALTER TABLE categories ADD COLUMN IF NOT EXISTS is_default BOOLEAN DEFAULT FALSE;
ALTER TABLE categories ADD COLUMN IF NOT EXISTS is_active BOOLEAN DEFAULT TRUE;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'categories'
          AND column_name = 'icon_name'
    ) THEN
        ALTER TABLE categories RENAME COLUMN icon_name TO icon;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'categories'
          AND column_name = 'color_code'
    ) THEN
        ALTER TABLE categories RENAME COLUMN color_code TO color;
    END IF;
END $$;

UPDATE categories SET is_default = FALSE WHERE is_default IS NULL;
UPDATE categories SET is_active = TRUE WHERE is_active IS NULL;

ALTER TABLE categories ALTER COLUMN is_default SET NOT NULL;
ALTER TABLE categories ALTER COLUMN is_active SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_categories_owner_name') THEN
        ALTER TABLE categories ADD CONSTRAINT uk_categories_owner_name UNIQUE (user_id, name);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_categories_owner_active ON categories(user_id, is_active);

-- 2) Budgets: monthly budget by yyyyMM per user
CREATE TABLE IF NOT EXISTS budgets (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    month_key CHAR(6) NOT NULL,
    amount_limit DECIMAL(15, 2) NOT NULL,
    alert_threshold_pct INTEGER NOT NULL DEFAULT 80,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_budgets_user_month UNIQUE (user_id, month_key),
    CONSTRAINT ck_budgets_month_key_format CHECK (month_key ~ '^[0-9]{6}$'),
    CONSTRAINT ck_budgets_amount_limit_positive CHECK (amount_limit > 0),
    CONSTRAINT ck_budgets_alert_threshold_range CHECK (alert_threshold_pct BETWEEN 1 AND 100)
);

CREATE INDEX IF NOT EXISTS idx_budgets_user_month ON budgets(user_id, month_key);

-- 3) Photos: explicit expense note/category support
ALTER TABLE photos ADD COLUMN IF NOT EXISTS note VARCHAR(255);
ALTER TABLE photos ADD COLUMN IF NOT EXISTS category_id UUID;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_photo_category') THEN
        ALTER TABLE photos ADD CONSTRAINT fk_photo_category FOREIGN KEY (category_id) REFERENCES categories(id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_photos_sender_taken ON photos(sender_id, taken_at DESC);
CREATE INDEX IF NOT EXISTS idx_photos_category_taken ON photos(category_id, taken_at DESC);

