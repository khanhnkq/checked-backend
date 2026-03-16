ALTER TABLE photos ADD COLUMN IF NOT EXISTS public_id VARCHAR(255);
ALTER TABLE photos ADD COLUMN IF NOT EXISTS thumbnail_url VARCHAR(500);
ALTER TABLE photos ADD COLUMN IF NOT EXISTS recipient_scope VARCHAR(30);
ALTER TABLE photos ADD COLUMN IF NOT EXISTS status VARCHAR(20);
ALTER TABLE photos ADD COLUMN IF NOT EXISTS mime_type VARCHAR(100);
ALTER TABLE photos ADD COLUMN IF NOT EXISTS file_size BIGINT;
ALTER TABLE photos ADD COLUMN IF NOT EXISTS width INTEGER;
ALTER TABLE photos ADD COLUMN IF NOT EXISTS height INTEGER;
ALTER TABLE photos ADD COLUMN IF NOT EXISTS recipient_count INTEGER;
ALTER TABLE photos ADD COLUMN IF NOT EXISTS taken_at TIMESTAMP;

UPDATE photos
SET recipient_scope = COALESCE(recipient_scope, 'ALL_FRIENDS'),
    status = COALESCE(status, 'READY'),
    recipient_count = COALESCE(recipient_count, 0);

ALTER TABLE photos ALTER COLUMN recipient_scope SET NOT NULL;
ALTER TABLE photos ALTER COLUMN status SET NOT NULL;
ALTER TABLE photos ALTER COLUMN recipient_count SET NOT NULL;
ALTER TABLE photos ALTER COLUMN recipient_scope SET DEFAULT 'ALL_FRIENDS';
ALTER TABLE photos ALTER COLUMN status SET DEFAULT 'READY';
ALTER TABLE photos ALTER COLUMN recipient_count SET DEFAULT 0;
ALTER TABLE photos ALTER COLUMN amount DROP DEFAULT;

CREATE TABLE IF NOT EXISTS photo_recipients (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    photo_id UUID NOT NULL REFERENCES photos(id) ON DELETE CASCADE,
    recipient_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_photo_recipient UNIQUE (photo_id, recipient_id)
);

CREATE INDEX IF NOT EXISTS idx_photo_recipients_recipient_created ON photo_recipients(recipient_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_photos_scope_created ON photos(recipient_scope, created_at DESC);

