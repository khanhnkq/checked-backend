-- ============================================================
-- V19: Add location/GPS/store, dish name, and calories to photos table
-- ============================================================

ALTER TABLE photos ADD COLUMN IF NOT EXISTS location_name VARCHAR(255);
ALTER TABLE photos ADD COLUMN IF NOT EXISTS latitude DECIMAL(10, 7);
ALTER TABLE photos ADD COLUMN IF NOT EXISTS longitude DECIMAL(10, 7);
ALTER TABLE photos ADD COLUMN IF NOT EXISTS dish_name VARCHAR(150);
ALTER TABLE photos ADD COLUMN IF NOT EXISTS calories INTEGER;
