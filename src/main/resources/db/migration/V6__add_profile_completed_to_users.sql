ALTER TABLE users
    ADD COLUMN IF NOT EXISTS profile_completed BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE users
SET profile_completed = TRUE
WHERE display_name IS NOT NULL
  AND BTRIM(display_name) <> '';

