import os
import uuid
import datetime
import psycopg2

DB_URL = "postgresql://postgres.qxcbcjcrhslsarpsbtod:0703586224Kh%40nh@aws-0-ap-southeast-1.pooler.supabase.com:5432/postgres?sslmode=require"

TARGET_DEV_ID = "f617a40d-935b-4145-9ca2-ff2f1b9ad85b" # khanhnkqdev
TARGET_MAIN_ID = "4acf8116-a0a8-4b55-a7ce-4b96e2f77ccc" # khanhnkq

# Danh mục mặc định
CAT_FOOD = "c089bcf8-29b0-4272-aa7b-4f06093c9892"
CAT_TRANSPORT = "d4b4fe78-0c08-4cdb-98d1-c37b46d056c3"
CAT_SHOPPING = "c6db1d63-d0a7-479e-a723-a764b1c42bbf"
CAT_OTHER = "8a3ed94b-5a87-45e9-be4a-de3d8e022028"

SAMPLE_FRIENDS_PHOTOS = [
    # 1. Bảo Trần
    {
        "user_id": "77777777-7777-7777-7777-777777777777",
        "username": "baotran",
        "photos": [
            {
                "caption": "🍜 Phở Thìn Lò Đúc ấm bụng ngày mưa",
                "image_url": "https://images.unsplash.com/photo-1582878826629-29b7ad1cdc43?w=800&auto=format&fit=crop&q=80",
                "amount": 75000,
                "category_id": CAT_FOOD,
                "dish_name": "Phở bò tái lăn",
                "calories": 620,
                "location_name": "Phở Thìn Lò Đúc, Hà Nội",
                "hours_ago": 2.5
            }
        ]
    },
    # 2. Yến Vũ
    {
        "user_id": "88888888-8888-8888-8888-888888888888",
        "username": "yenvh",
        "photos": [
            {
                "caption": "🍰 Trà chiều cùng hội bạn thân",
                "image_url": "https://images.unsplash.com/photo-1578985545062-69928b1d9587?w=800&auto=format&fit=crop&q=80",
                "amount": 135000,
                "category_id": CAT_FOOD,
                "dish_name": "Bánh Tiramisu & Trà Đào",
                "calories": 480,
                "location_name": "The Coffee House, Q.1",
                "hours_ago": 6.0
            }
        ]
    },
    # 3. Kiệt Đặng
    {
        "user_id": "99999999-9999-9999-9999-999999999999",
        "username": "kietdang",
        "photos": [
            {
                "caption": "🍕 Pizza 4P's phô mai béo ngậy",
                "image_url": "https://images.unsplash.com/photo-1513104890138-7c749659a591?w=800&auto=format&fit=crop&q=80",
                "amount": 320000,
                "category_id": CAT_FOOD,
                "dish_name": "Pizza 4 Cheese",
                "calories": 850,
                "location_name": "Pizza 4P's Bến Thành",
                "hours_ago": 12.0
            }
        ]
    },
    # 4. Thảo Phan
    {
        "user_id": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
        "username": "thanhthao",
        "photos": [
            {
                "caption": "☕ Match latte sáng sớm nạp năng lượng",
                "image_url": "https://images.unsplash.com/photo-1536256263959-770b48d82b0a?w=800&auto=format&fit=crop&q=80",
                "amount": 65000,
                "category_id": CAT_FOOD,
                "dish_name": "Matcha Latte Yến Mạch",
                "calories": 210,
                "location_name": "Rang Rang Coffee, Thảo Điền",
                "hours_ago": 18.0
            }
        ]
    },
    # 5. Duy Quang
    {
        "user_id": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
        "username": "duyquang",
        "photos": [
            {
                "caption": "🍣 Sushi tươi ngon cuối tuần",
                "image_url": "https://images.unsplash.com/photo-1579871494447-9811cf80d66c?w=800&auto=format&fit=crop&q=80",
                "amount": 450000,
                "category_id": CAT_FOOD,
                "dish_name": "Sashimi cá hồi & Nigiri",
                "calories": 520,
                "location_name": "Sushi Hokkaido Sachi, Q.1",
                "hours_ago": 26.0
            }
        ]
    },
    # 6. Trâm Ngọc
    {
        "user_id": "cccccccc-cccc-cccc-cccc-cccccccccccc",
        "username": "ngoctram",
        "photos": [
            {
                "caption": "🧋 Trà sữa trân châu hoàng kim chill chill",
                "image_url": "https://images.unsplash.com/photo-1558857563-b37cf5a2cb7f?w=800&auto=format&fit=crop&q=80",
                "amount": 55000,
                "category_id": CAT_FOOD,
                "dish_name": "Trà sữa trân châu",
                "calories": 390,
                "location_name": "Koi Thé, Lê Lợi",
                "hours_ago": 36.0
            }
        ]
    },
    # 7. Tuấn Anh
    {
        "user_id": "dddddddd-dddd-dddd-dddd-dddddddddddd",
        "username": "tuananh",
        "photos": [
            {
                "caption": "🍔 Burger bò phô mai siêu đẫm sốt",
                "image_url": "https://images.unsplash.com/photo-1568901346375-23c9450c58cd?w=800&auto=format&fit=crop&q=80",
                "amount": 145000,
                "category_id": CAT_FOOD,
                "dish_name": "Double Cheeseburger",
                "calories": 780,
                "location_name": "Marcel Burger, Q.1",
                "hours_ago": 48.0
            }
        ]
    },
    # 8. Phương Ly
    {
        "user_id": "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee",
        "username": "phuongly",
        "photos": [
            {
                "caption": "🥗 Salad ức gà ăn nhẹ giữ dáng",
                "image_url": "https://images.unsplash.com/photo-1540420773420-3366772f4999?w=800&auto=format&fit=crop&q=80",
                "amount": 85000,
                "category_id": CAT_FOOD,
                "dish_name": "Ceasar Salad Ức Gà",
                "calories": 310,
                "location_name": "SaladStop! Saigon Centre",
                "hours_ago": 60.0
            }
        ]
    },
    # 9. Quang Hải
    {
        "user_id": "66666666-6666-6666-6666-666666666666",
        "username": "quanghai",
        "photos": [
            {
                "caption": "☕ Cà phê trứng thơm bùi ngắm phố",
                "image_url": "https://images.unsplash.com/photo-1514432324607-a09d9b4aefdd?w=800&auto=format&fit=crop&q=80",
                "amount": 50000,
                "category_id": CAT_FOOD,
                "dish_name": "Cà phê trứng Giảng",
                "calories": 280,
                "location_name": "Cafe Giảng, Hà Nội",
                "hours_ago": 72.0
            }
        ]
    },
    # 10. Linh Nguyễn
    {
        "user_id": "11111111-1111-1111-1111-111111111111",
        "username": "linhnguyen",
        "photos": [
            {
                "caption": "☕ Cafe sáng cuối tuần chill thật sự",
                "image_url": "https://images.unsplash.com/photo-1554118811-1e0d58224f24?w=800&auto=format&fit=crop&q=80",
                "amount": 45000,
                "category_id": CAT_FOOD,
                "dish_name": "Bạc xỉu đá",
                "calories": 180,
                "location_name": "Là Việt Coffee, Tú Xương",
                "hours_ago": 1.0
            }
        ]
    },
    # 11. Huy Đặng
    {
        "user_id": "22222222-2222-2222-2222-222222222222",
        "username": "huydang",
        "photos": [
            {
                "caption": "🚗 Rửa xe chuẩn bị lượn phố",
                "image_url": "https://images.unsplash.com/photo-1568605117036-5fe5e7bab0b7?w=800&auto=format&fit=crop&q=80",
                "amount": 120000,
                "category_id": CAT_TRANSPORT,
                "dish_name": None,
                "calories": 0,
                "location_name": "Car Wash Center, Q.7",
                "hours_ago": 4.0
            }
        ]
    },
    # 12. Mai Trần
    {
        "user_id": "33333333-3333-3333-3333-333333333333",
        "username": "maitran",
        "photos": [
            {
                "caption": "🛍️ Săn sale đồ thu đông nè mọi người",
                "image_url": "https://images.unsplash.com/photo-1483985988355-763728e1935b?w=800&auto=format&fit=crop&q=80",
                "amount": 350000,
                "category_id": CAT_SHOPPING,
                "dish_name": None,
                "calories": 0,
                "location_name": "Takashimaya Saigon",
                "hours_ago": 8.0
            }
        ]
    },
    # 13. Hoàng Lê
    {
        "user_id": "44444444-4444-4444-4444-444444444444",
        "username": "hoangle",
        "photos": [
            {
                "caption": "🥩 Đi ăn mừng dự án thành công!",
                "image_url": "https://images.unsplash.com/photo-1504674900247-0877df9cc836?w=800&auto=format&fit=crop&q=80",
                "amount": 520000,
                "category_id": CAT_FOOD,
                "dish_name": "Bò Wagyu Nướng Đá",
                "calories": 750,
                "location_name": "El Gaucho Steakhouse, Q.1",
                "hours_ago": 16.0
            }
        ]
    }
]

def run_seed():
    conn = psycopg2.connect(DB_URL)
    conn.autocommit = False
    cur = conn.cursor()

    try:
        now = datetime.datetime.now()
        target_ids = [TARGET_DEV_ID, TARGET_MAIN_ID]

        # 1. Đảm bảo tất cả bạn bè mẫu đều kết bạn ACCEPTED với cả TARGET_DEV_ID và TARGET_MAIN_ID
        print("🤝 Đang chuẩn hóa quan hệ bạn bè ACCEPTED...")
        for friend in SAMPLE_FRIENDS_PHOTOS:
            friend_id = friend["user_id"]
            for target_id in target_ids:
                cur.execute("""
                    SELECT id FROM friendships 
                    WHERE (user_id = %s AND friend_id = %s) OR (user_id = %s AND friend_id = %s);
                """, (friend_id, target_id, target_id, friend_id))
                row = cur.fetchone()
                if row:
                    cur.execute("UPDATE friendships SET status = 'ACCEPTED' WHERE id = %s;", (row[0],))
                else:
                    cur.execute("""
                        INSERT INTO friendships (id, user_id, friend_id, status, created_at)
                        VALUES (%s, %s, %s, 'ACCEPTED', %s);
                    """, (str(uuid.uuid4()), friend_id, target_id, now - datetime.timedelta(days=14)))

        # 2. Xóa các photo mẫu cũ để nạp bộ mới sạch đẹp
        print("🧹 Đang dọn dẹp các photo mẫu cũ...")
        cur.execute("""
            DELETE FROM photo_recipients 
            WHERE photo_id IN (
                SELECT id FROM photos WHERE sender_id IN %s
            );
        """, (tuple(f["user_id"] for f in SAMPLE_FRIENDS_PHOTOS),))

        cur.execute("""
            DELETE FROM photos WHERE sender_id IN %s;
        """, (tuple(f["user_id"] for f in SAMPLE_FRIENDS_PHOTOS),))

        # 3. Tạo các photo mẫu hoàn chỉnh
        print("📸 Đang tạo photo khoảnh khắc cho từng bạn bè...")
        photo_count = 0
        for friend in SAMPLE_FRIENDS_PHOTOS:
            friend_id = friend["user_id"]
            username = friend["username"]

            for p in friend["photos"]:
                photo_id = str(uuid.uuid4())
                p_time = now - datetime.timedelta(hours=p["hours_ago"])

                cur.execute("""
                    INSERT INTO photos (
                        id, sender_id, image_url, thumbnail_url, caption, amount,
                        transaction_type, category_id, recipient_scope, recipient_count,
                        status, mime_type, file_size, width, height,
                        taken_at, created_at, location_name, dish_name, calories
                    )
                    VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s);
                """, (
                    photo_id, friend_id, p["image_url"], p["image_url"], p["caption"], p["amount"],
                    "EXPENSE", p["category_id"], "ALL_FRIENDS", len(target_ids),
                    "READY", "image/jpeg", 250000, 1080, 1080,
                    p_time, p_time, p["location_name"], p["dish_name"], p["calories"]
                ))

                # Thêm recipients
                for target_id in target_ids:
                    cur.execute("""
                        INSERT INTO photo_recipients (id, photo_id, recipient_id, created_at)
                        VALUES (%s, %s, %s, %s);
                    """, (str(uuid.uuid4()), photo_id, target_id, p_time))

                photo_count += 1
                print(f"   ✨ Đã tạo ảnh của @{username}: '{p['caption']}'")

        conn.commit()
        print(f"\n🎉 Thành công! Đã tạo {photo_count} ảnh khoảnh khắc cho bạn bè và chấp thuận kết bạn đầy đủ.")

    except Exception as e:
        conn.rollback()
        print(f"❌ Lỗi: {e}")
        raise
    finally:
        cur.close()
        conn.close()

if __name__ == "__main__":
    run_seed()
