BEGIN;

-- 1. Đảm bảo trạng thái ACCEPTED cho tất cả bạn bè mẫu với khanhnkqdev và khanhnkq
INSERT INTO friendships (id, user_id, friend_id, status, created_at)
VALUES
  (gen_random_uuid(), '11111111-1111-1111-1111-111111111111', 'f617a40d-935b-4145-9ca2-ff2f1b9ad85b', 'ACCEPTED', NOW() - INTERVAL '14 days'),
  (gen_random_uuid(), '22222222-2222-2222-2222-222222222222', 'f617a40d-935b-4145-9ca2-ff2f1b9ad85b', 'ACCEPTED', NOW() - INTERVAL '14 days'),
  (gen_random_uuid(), '33333333-3333-3333-3333-333333333333', 'f617a40d-935b-4145-9ca2-ff2f1b9ad85b', 'ACCEPTED', NOW() - INTERVAL '14 days'),
  (gen_random_uuid(), '44444444-4444-4444-4444-444444444444', 'f617a40d-935b-4145-9ca2-ff2f1b9ad85b', 'ACCEPTED', NOW() - INTERVAL '14 days'),
  (gen_random_uuid(), '55555555-5555-5555-5555-555555555555', 'f617a40d-935b-4145-9ca2-ff2f1b9ad85b', 'ACCEPTED', NOW() - INTERVAL '14 days'),
  (gen_random_uuid(), '11111111-1111-1111-1111-111111111111', '4acf8116-a0a8-4b55-a7ce-4b96e2f77ccc', 'ACCEPTED', NOW() - INTERVAL '14 days'),
  (gen_random_uuid(), '22222222-2222-2222-2222-222222222222', '4acf8116-a0a8-4b55-a7ce-4b96e2f77ccc', 'ACCEPTED', NOW() - INTERVAL '14 days'),
  (gen_random_uuid(), '33333333-3333-3333-3333-333333333333', '4acf8116-a0a8-4b55-a7ce-4b96e2f77ccc', 'ACCEPTED', NOW() - INTERVAL '14 days'),
  (gen_random_uuid(), '44444444-4444-4444-4444-444444444444', '4acf8116-a0a8-4b55-a7ce-4b96e2f77ccc', 'ACCEPTED', NOW() - INTERVAL '14 days')
ON CONFLICT DO NOTHING;

UPDATE friendships SET status = 'ACCEPTED'
WHERE (user_id = 'f617a40d-935b-4145-9ca2-ff2f1b9ad85b' OR friend_id = 'f617a40d-935b-4145-9ca2-ff2f1b9ad85b')
  AND status = 'PENDING';

UPDATE friendships SET status = 'ACCEPTED'
WHERE (user_id = '4acf8116-a0a8-4b55-a7ce-4b96e2f77ccc' OR friend_id = '4acf8116-a0a8-4b55-a7ce-4b96e2f77ccc')
  AND status = 'PENDING';

-- 2. Dọn dẹp photos cũ của các bạn bè mẫu (trừ ảnh của chính khanhnkqdev)
DELETE FROM photo_recipients 
WHERE photo_id IN (
  SELECT id FROM photos WHERE sender_id <> 'f617a40d-935b-4145-9ca2-ff2f1b9ad85b'
);

DELETE FROM photos WHERE sender_id <> 'f617a40d-935b-4145-9ca2-ff2f1b9ad85b';

-- 3. Tạo các photo khoảnh khắc cho từng bạn bè

-- [1] Bảo Trần (baotran)
WITH new_p AS (
  INSERT INTO photos (
    id, sender_id, image_url, thumbnail_url, caption, amount,
    transaction_type, category_id, recipient_scope, recipient_count,
    status, mime_type, file_size, width, height,
    taken_at, occurred_at, created_at, location_name, dish_name, calories
  ) VALUES (
    gen_random_uuid(), '77777777-7777-7777-7777-777777777777',
    'https://images.unsplash.com/photo-1582878826629-29b7ad1cdc43?w=800&auto=format&fit=crop&q=80',
    'https://images.unsplash.com/photo-1582878826629-29b7ad1cdc43?w=800&auto=format&fit=crop&q=80',
    '🍜 Phở Thìn Lò Đúc ấm bụng ngày mưa', 75000,
    'EXPENSE', 'c089bcf8-29b0-4272-aa7b-4f06093c9892', 'ALL_FRIENDS', 2,
    'READY', 'image/jpeg', 250000, 1080, 1080,
    NOW() - INTERVAL '2 hours', NOW() - INTERVAL '2 hours', NOW() - INTERVAL '2 hours',
    'Phở Thìn Lò Đúc, Hà Nội', 'Phở bò tái lăn', 620
  ) RETURNING id, created_at
)
INSERT INTO photo_recipients (id, photo_id, recipient_id, created_at)
SELECT gen_random_uuid(), new_p.id, r.uid, new_p.created_at
FROM new_p
CROSS JOIN (VALUES 
  ('f617a40d-935b-4145-9ca2-ff2f1b9ad85b'::uuid),
  ('4acf8116-a0a8-4b55-a7ce-4b96e2f77ccc'::uuid)
) AS r(uid);

-- [2] Yến Vũ (yenvh)
WITH new_p AS (
  INSERT INTO photos (
    id, sender_id, image_url, thumbnail_url, caption, amount,
    transaction_type, category_id, recipient_scope, recipient_count,
    status, mime_type, file_size, width, height,
    taken_at, occurred_at, created_at, location_name, dish_name, calories
  ) VALUES (
    gen_random_uuid(), '88888888-8888-8888-8888-888888888888',
    'https://images.unsplash.com/photo-1578985545062-69928b1d9587?w=800&auto=format&fit=crop&q=80',
    'https://images.unsplash.com/photo-1578985545062-69928b1d9587?w=800&auto=format&fit=crop&q=80',
    '🍰 Trà chiều cùng hội bạn thân', 135000,
    'EXPENSE', 'c089bcf8-29b0-4272-aa7b-4f06093c9892', 'ALL_FRIENDS', 2,
    'READY', 'image/jpeg', 250000, 1080, 1080,
    NOW() - INTERVAL '5 hours', NOW() - INTERVAL '5 hours', NOW() - INTERVAL '5 hours',
    'The Coffee House, Q.1', 'Bánh Tiramisu & Trà Đào', 480
  ) RETURNING id, created_at
)
INSERT INTO photo_recipients (id, photo_id, recipient_id, created_at)
SELECT gen_random_uuid(), new_p.id, r.uid, new_p.created_at
FROM new_p
CROSS JOIN (VALUES 
  ('f617a40d-935b-4145-9ca2-ff2f1b9ad85b'::uuid),
  ('4acf8116-a0a8-4b55-a7ce-4b96e2f77ccc'::uuid)
) AS r(uid);

-- [3] Kiệt Đặng (kietdang)
WITH new_p AS (
  INSERT INTO photos (
    id, sender_id, image_url, thumbnail_url, caption, amount,
    transaction_type, category_id, recipient_scope, recipient_count,
    status, mime_type, file_size, width, height,
    taken_at, occurred_at, created_at, location_name, dish_name, calories
  ) VALUES (
    gen_random_uuid(), '99999999-9999-9999-9999-999999999999',
    'https://images.unsplash.com/photo-1513104890138-7c749659a591?w=800&auto=format&fit=crop&q=80',
    'https://images.unsplash.com/photo-1513104890138-7c749659a591?w=800&auto=format&fit=crop&q=80',
    '🍕 Pizza 4Ps phô mai béo ngậy', 320000,
    'EXPENSE', 'c089bcf8-29b0-4272-aa7b-4f06093c9892', 'ALL_FRIENDS', 2,
    'READY', 'image/jpeg', 250000, 1080, 1080,
    NOW() - INTERVAL '9 hours', NOW() - INTERVAL '9 hours', NOW() - INTERVAL '9 hours',
    'Pizza 4Ps Bến Thành', 'Pizza 4 Cheese', 850
  ) RETURNING id, created_at
)
INSERT INTO photo_recipients (id, photo_id, recipient_id, created_at)
SELECT gen_random_uuid(), new_p.id, r.uid, new_p.created_at
FROM new_p
CROSS JOIN (VALUES 
  ('f617a40d-935b-4145-9ca2-ff2f1b9ad85b'::uuid),
  ('4acf8116-a0a8-4b55-a7ce-4b96e2f77ccc'::uuid)
) AS r(uid);

-- [4] Thảo Phan (thanhthao)
WITH new_p AS (
  INSERT INTO photos (
    id, sender_id, image_url, thumbnail_url, caption, amount,
    transaction_type, category_id, recipient_scope, recipient_count,
    status, mime_type, file_size, width, height,
    taken_at, occurred_at, created_at, location_name, dish_name, calories
  ) VALUES (
    gen_random_uuid(), 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    'https://images.unsplash.com/photo-1536256263959-770b48d82b0a?w=800&auto=format&fit=crop&q=80',
    'https://images.unsplash.com/photo-1536256263959-770b48d82b0a?w=800&auto=format&fit=crop&q=80',
    '🍵 Matcha latte sáng sớm nạp năng lượng', 65000,
    'EXPENSE', 'c089bcf8-29b0-4272-aa7b-4f06093c9892', 'ALL_FRIENDS', 2,
    'READY', 'image/jpeg', 250000, 1080, 1080,
    NOW() - INTERVAL '14 hours', NOW() - INTERVAL '14 hours', NOW() - INTERVAL '14 hours',
    'Rang Rang Coffee, Thảo Điền', 'Matcha Latte Yến Mạch', 210
  ) RETURNING id, created_at
)
INSERT INTO photo_recipients (id, photo_id, recipient_id, created_at)
SELECT gen_random_uuid(), new_p.id, r.uid, new_p.created_at
FROM new_p
CROSS JOIN (VALUES 
  ('f617a40d-935b-4145-9ca2-ff2f1b9ad85b'::uuid),
  ('4acf8116-a0a8-4b55-a7ce-4b96e2f77ccc'::uuid)
) AS r(uid);

-- [5] Duy Quang (duyquang)
WITH new_p AS (
  INSERT INTO photos (
    id, sender_id, image_url, thumbnail_url, caption, amount,
    transaction_type, category_id, recipient_scope, recipient_count,
    status, mime_type, file_size, width, height,
    taken_at, occurred_at, created_at, location_name, dish_name, calories
  ) VALUES (
    gen_random_uuid(), 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
    'https://images.unsplash.com/photo-1579871494447-9811cf80d66c?w=800&auto=format&fit=crop&q=80',
    'https://images.unsplash.com/photo-1579871494447-9811cf80d66c?w=800&auto=format&fit=crop&q=80',
    '🍣 Sushi tươi ngon cuối tuần', 450000,
    'EXPENSE', 'c089bcf8-29b0-4272-aa7b-4f06093c9892', 'ALL_FRIENDS', 2,
    'READY', 'image/jpeg', 250000, 1080, 1080,
    NOW() - INTERVAL '20 hours', NOW() - INTERVAL '20 hours', NOW() - INTERVAL '20 hours',
    'Sushi Hokkaido Sachi, Q.1', 'Sashimi cá hồi & Nigiri', 520
  ) RETURNING id, created_at
)
INSERT INTO photo_recipients (id, photo_id, recipient_id, created_at)
SELECT gen_random_uuid(), new_p.id, r.uid, new_p.created_at
FROM new_p
CROSS JOIN (VALUES 
  ('f617a40d-935b-4145-9ca2-ff2f1b9ad85b'::uuid),
  ('4acf8116-a0a8-4b55-a7ce-4b96e2f77ccc'::uuid)
) AS r(uid);

-- [6] Linh Nguyễn (linhnguyen)
WITH new_p AS (
  INSERT INTO photos (
    id, sender_id, image_url, thumbnail_url, caption, amount,
    transaction_type, category_id, recipient_scope, recipient_count,
    status, mime_type, file_size, width, height,
    taken_at, occurred_at, created_at, location_name, dish_name, calories
  ) VALUES (
    '11111111-aaaa-1111-aaaa-111111111111', '11111111-1111-1111-1111-111111111111',
    'https://images.unsplash.com/photo-1554118811-1e0d58224f24?w=800&auto=format&fit=crop&q=80',
    'https://images.unsplash.com/photo-1554118811-1e0d58224f24?w=800&auto=format&fit=crop&q=80',
    '☕ Cafe sáng cuối tuần chill thật sự', 45000,
    'EXPENSE', 'c089bcf8-29b0-4272-aa7b-4f06093c9892', 'ALL_FRIENDS', 2,
    'READY', 'image/jpeg', 250000, 1080, 1080,
    NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day',
    'Là Việt Coffee, Tú Xương', 'Bạc xỉu đá', 180
  ) RETURNING id, created_at
)
INSERT INTO photo_recipients (id, photo_id, recipient_id, created_at)
SELECT gen_random_uuid(), new_p.id, r.uid, new_p.created_at
FROM new_p
CROSS JOIN (VALUES 
  ('f617a40d-935b-4145-9ca2-ff2f1b9ad85b'::uuid),
  ('4acf8116-a0a8-4b55-a7ce-4b96e2f77ccc'::uuid)
) AS r(uid);

-- [7] Huy Đặng (huydang)
WITH new_p AS (
  INSERT INTO photos (
    id, sender_id, image_url, thumbnail_url, caption, amount,
    transaction_type, category_id, recipient_scope, recipient_count,
    status, mime_type, file_size, width, height,
    taken_at, occurred_at, created_at, location_name, dish_name, calories
  ) VALUES (
    '22222222-aaaa-2222-aaaa-222222222222', '22222222-2222-2222-2222-222222222222',
    'https://images.unsplash.com/photo-1568605117036-5fe5e7bab0b7?w=800&auto=format&fit=crop&q=80',
    'https://images.unsplash.com/photo-1568605117036-5fe5e7bab0b7?w=800&auto=format&fit=crop&q=80',
    '🚗 Rửa xe chuẩn bị lượn phố', 120000,
    'EXPENSE', 'd4b4fe78-0c08-4cdb-98d1-c37b46d056c3', 'ALL_FRIENDS', 2,
    'READY', 'image/jpeg', 250000, 1080, 1080,
    NOW() - INTERVAL '1 day 4 hours', NOW() - INTERVAL '1 day 4 hours', NOW() - INTERVAL '1 day 4 hours',
    'Car Wash Center, Q.7', NULL, 0
  ) RETURNING id, created_at
)
INSERT INTO photo_recipients (id, photo_id, recipient_id, created_at)
SELECT gen_random_uuid(), new_p.id, r.uid, new_p.created_at
FROM new_p
CROSS JOIN (VALUES 
  ('f617a40d-935b-4145-9ca2-ff2f1b9ad85b'::uuid),
  ('4acf8116-a0a8-4b55-a7ce-4b96e2f77ccc'::uuid)
) AS r(uid);

-- [8] Mai Trần (maitran)
WITH new_p AS (
  INSERT INTO photos (
    id, sender_id, image_url, thumbnail_url, caption, amount,
    transaction_type, category_id, recipient_scope, recipient_count,
    status, mime_type, file_size, width, height,
    taken_at, occurred_at, created_at, location_name, dish_name, calories
  ) VALUES (
    '33333333-aaaa-3333-aaaa-333333333333', '33333333-3333-3333-3333-333333333333',
    'https://images.unsplash.com/photo-1483985988355-763728e1935b?w=800&auto=format&fit=crop&q=80',
    'https://images.unsplash.com/photo-1483985988355-763728e1935b?w=800&auto=format&fit=crop&q=80',
    '🛍️ Săn sale đồ thu đông nè mọi người', 350000,
    'EXPENSE', 'c6db1d63-d0a7-479e-a723-a764b1c42bbf', 'ALL_FRIENDS', 2,
    'READY', 'image/jpeg', 250000, 1080, 1080,
    NOW() - INTERVAL '1 day 8 hours', NOW() - INTERVAL '1 day 8 hours', NOW() - INTERVAL '1 day 8 hours',
    'Takashimaya Saigon', NULL, 0
  ) RETURNING id, created_at
)
INSERT INTO photo_recipients (id, photo_id, recipient_id, created_at)
SELECT gen_random_uuid(), new_p.id, r.uid, new_p.created_at
FROM new_p
CROSS JOIN (VALUES 
  ('f617a40d-935b-4145-9ca2-ff2f1b9ad85b'::uuid),
  ('4acf8116-a0a8-4b55-a7ce-4b96e2f77ccc'::uuid)
) AS r(uid);

-- [9] Hoàng Lê (hoangle)
WITH new_p AS (
  INSERT INTO photos (
    id, sender_id, image_url, thumbnail_url, caption, amount,
    transaction_type, category_id, recipient_scope, recipient_count,
    status, mime_type, file_size, width, height,
    taken_at, occurred_at, created_at, location_name, dish_name, calories
  ) VALUES (
    '44444444-aaaa-4444-aaaa-444444444444', '44444444-4444-4444-4444-444444444444',
    'https://images.unsplash.com/photo-1504674900247-0877df9cc836?w=800&auto=format&fit=crop&q=80',
    'https://images.unsplash.com/photo-1504674900247-0877df9cc836?w=800&auto=format&fit=crop&q=80',
    '🥩 Đi ăn mừng dự án thành công!', 520000,
    'EXPENSE', 'c089bcf8-29b0-4272-aa7b-4f06093c9892', 'ALL_FRIENDS', 2,
    'READY', 'image/jpeg', 250000, 1080, 1080,
    NOW() - INTERVAL '1 day 16 hours', NOW() - INTERVAL '1 day 16 hours', NOW() - INTERVAL '1 day 16 hours',
    'El Gaucho Steakhouse, Q.1', 'Bò Wagyu Nướng Đá', 750
  ) RETURNING id, created_at
)
INSERT INTO photo_recipients (id, photo_id, recipient_id, created_at)
SELECT gen_random_uuid(), new_p.id, r.uid, new_p.created_at
FROM new_p
CROSS JOIN (VALUES 
  ('f617a40d-935b-4145-9ca2-ff2f1b9ad85b'::uuid),
  ('4acf8116-a0a8-4b55-a7ce-4b96e2f77ccc'::uuid)
) AS r(uid);

COMMIT;
