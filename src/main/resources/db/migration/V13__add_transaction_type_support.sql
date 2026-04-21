-- Add transaction type (INCOME/EXPENSE) support for cashflow tracking

-- 1) Add transaction_type to photos table
ALTER TABLE photos ADD COLUMN IF NOT EXISTS transaction_type VARCHAR(20) DEFAULT 'EXPENSE' NOT NULL;

-- Set valid enum constraint
ALTER TABLE photos ADD CONSTRAINT ck_photos_transaction_type CHECK (transaction_type IN ('INCOME', 'EXPENSE'));

-- 2) Add occurred_at timestamp for accurate month calculation (fallback to taken_at)
ALTER TABLE photos ADD COLUMN IF NOT EXISTS occurred_at TIMESTAMP;

-- Populate occurred_at from taken_at or created_at
UPDATE photos SET occurred_at = COALESCE(taken_at, created_at) WHERE occurred_at IS NULL;

-- Make it NOT NULL after backfill
ALTER TABLE photos ALTER COLUMN occurred_at SET NOT NULL;

-- 3) Add transaction_type to categories (for classification)
ALTER TABLE categories ADD COLUMN IF NOT EXISTS transaction_type VARCHAR(20) DEFAULT 'EXPENSE';

ALTER TABLE categories ADD CONSTRAINT ck_categories_transaction_type CHECK (transaction_type IN ('INCOME', 'EXPENSE'));

-- 4) Create index for efficient querying by type and month
CREATE INDEX IF NOT EXISTS idx_photos_sender_type_occurred ON photos(sender_id, transaction_type, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_photos_type_month_key ON photos(sender_id, transaction_type, EXTRACT(YEAR FROM occurred_at), EXTRACT(MONTH FROM occurred_at));
CREATE INDEX IF NOT EXISTS idx_categories_owner_type_active ON categories(user_id, transaction_type, is_active);

