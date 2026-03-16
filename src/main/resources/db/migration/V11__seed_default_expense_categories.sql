-- Seed a small default category set for expense tracking.
INSERT INTO categories (id, name, icon, color, user_id, is_default, is_active)
SELECT uuid_generate_v4(), 'Food', 'restaurant', '#FF8A65', NULL, TRUE, TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM categories WHERE user_id IS NULL AND LOWER(name) = 'food'
);

INSERT INTO categories (id, name, icon, color, user_id, is_default, is_active)
SELECT uuid_generate_v4(), 'Transport', 'directions_car', '#4DB6AC', NULL, TRUE, TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM categories WHERE user_id IS NULL AND LOWER(name) = 'transport'
);

INSERT INTO categories (id, name, icon, color, user_id, is_default, is_active)
SELECT uuid_generate_v4(), 'Shopping', 'shopping_bag', '#9575CD', NULL, TRUE, TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM categories WHERE user_id IS NULL AND LOWER(name) = 'shopping'
);

INSERT INTO categories (id, name, icon, color, user_id, is_default, is_active)
SELECT uuid_generate_v4(), 'Bills', 'receipt_long', '#64B5F6', NULL, TRUE, TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM categories WHERE user_id IS NULL AND LOWER(name) = 'bills'
);

INSERT INTO categories (id, name, icon, color, user_id, is_default, is_active)
SELECT uuid_generate_v4(), 'Other', 'category', '#90A4AE', NULL, TRUE, TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM categories WHERE user_id IS NULL AND LOWER(name) = 'other'
);

