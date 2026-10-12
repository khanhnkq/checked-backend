-- ============================================================
-- V18: Add Date of Birth (dob) Column to Users Table
-- ============================================================

ALTER TABLE users ADD COLUMN IF NOT EXISTS dob DATE;
