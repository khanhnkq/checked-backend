import uuid
import datetime
import psycopg2

DB_URL = "postgresql://neondb_owner:npg_UHF6EBJK3MCG@ep-summer-darkness-b3unuia8-pooler.c-4.ap-southeast-1.aws.neon.tech:5432/neondb?sslmode=require"
TARGET_EMAIL = "khanhnkq.dev@gmail.com"

# Reusable BCrypt hash from target user
BCRYPT_PASSWORD_HASH = "$2a$10$rOH5cXTjNu7FOEwpuw00C.UKB3tMUyxNRI5tgfcTmcv1GIBsIITRG"

SAMPLE_USERS = [
    {
        "id": "11111111-1111-1111-1111-111111111111",
        "email": "linh.nguyen@checked.app",
        "username": "linhnguyen",
        "first_name": "Linh",
        "last_name": "Nguyễn",
        "avatar_url": "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=400&auto=format&fit=crop&q=80",
        "is_gold_member": False,
        "is_verified": True,
        "dob": datetime.date(2001, 3, 15),
        "photo": {
            "id": "11111111-aaaa-1111-aaaa-111111111111",
            "image_url": "https://images.unsplash.com/photo-1554118811-1e0d58224f24?w=800&auto=format&fit=crop&q=80",
            "caption": "☕ Cafe sáng cuối tuần chill thật sự",
            "amount": 45000,
            "category_id": "c089bcf8-29b0-4272-aa7b-4f06093c9892", # Food
            "hours_ago": 0.5,
        },
        "message": "Ê hôm nay rảnh đi cafe Highland không?",
    },
    {
        "id": "22222222-2222-2222-2222-222222222222",
        "email": "huy.dang@checked.app",
        "username": "huydang",
        "first_name": "Huy",
        "last_name": "Đặng",
        "avatar_url": "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=400&auto=format&fit=crop&q=80",
        "is_gold_member": True,
        "is_verified": True,
        "dob": datetime.date(1998, 7, 22),
        "photo": {
            "id": "22222222-aaaa-2222-aaaa-222222222222",
            "image_url": "https://images.unsplash.com/photo-1568605117036-5fe5e7bab0b7?w=800&auto=format&fit=crop&q=80",
            "caption": "🚗 Rửa xe chuẩn bị lượn phố",
            "amount": 120000,
            "category_id": "d4b4fe78-0c08-4cdb-98d1-c37b46d056c3", # Transport
            "hours_ago": 2.0,
        },
        "message": "App mới mượt đấy ông ơi 🔥",
    },
    {
        "id": "33333333-3333-3333-3333-333333333333",
        "email": "mai.tran@checked.app",
        "username": "maitran",
        "first_name": "Mai",
        "last_name": "Trần",
        "avatar_url": "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=400&auto=format&fit=crop&q=80",
        "is_gold_member": False,
        "is_verified": True,
        "dob": datetime.date(2002, 11, 5),
        "photo": {
            "id": "33333333-aaaa-3333-aaaa-333333333333",
            "image_url": "https://images.unsplash.com/photo-1483985988355-763728e1935b?w=800&auto=format&fit=crop&q=80",
            "caption": "🛍️ Săn sale đồ thu đông nè mọi người",
            "amount": 350000,
            "category_id": "c6db1d63-d0a7-479e-a723-a764b1c42bbf", # Shopping
            "hours_ago": 5.0,
        },
        "message": "Xem ảnh tui mới gửi chưa nè haha",
    },
    {
        "id": "44444444-4444-4444-4444-444444444444",
        "email": "hoang.le@checked.app",
        "username": "hoangle",
        "first_name": "Hoàng",
        "last_name": "Lê",
        "avatar_url": "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=400&auto=format&fit=crop&q=80",
        "is_gold_member": True,
        "is_verified": True,
        "dob": datetime.date(1997, 1, 10),
        "photo": {
            "id": "44444444-aaaa-4444-aaaa-444444444444",
            "image_url": "https://images.unsplash.com/photo-1504674900247-0877df9cc836?w=800&auto=format&fit=crop&q=80",
            "caption": "🥩 Đi ăn mừng dự án thành công!",
            "amount": 520000,
            "category_id": "c089bcf8-29b0-4272-aa7b-4f06093c9892", # Food
            "hours_ago": 24.0,
        },
        "message": "Hôm nào rảnh anh em mình làm kèo nhậu nhé",
    },
    {
        "id": "55555555-5555-5555-5555-555555555555",
        "email": "minh.anh@checked.app",
        "username": "minhanh",
        "first_name": "Minh",
        "last_name": "Anh",
        "avatar_url": "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=400&auto=format&fit=crop&q=80",
        "is_gold_member": False,
        "is_verified": True,
        "dob": datetime.date(2003, 9, 18),
        "photo": None,
        "message": "Hello bạn iu ✨",
    },
    {
        "id": "66666666-6666-6666-6666-666666666666",
        "email": "quang.hai@checked.app",
        "username": "quanghai",
        "first_name": "Quang",
        "last_name": "Hải",
        "avatar_url": "https://images.unsplash.com/photo-1519085360753-af0119f7cbe7?w=400&auto=format&fit=crop&q=80",
        "is_gold_member": False,
        "is_verified": True,
        "dob": datetime.date(2000, 4, 12),
        "photo": None,
        "message": "Chúc tuần mới vui vẻ nhé bro!",
    },
]

def seed_data():
    conn = psycopg2.connect(DB_URL)
    conn.autocommit = False
    cur = conn.cursor()

    try:
        # 1. Lấy thông tin user đích khanhnkq.dev@gmail.com
        cur.execute("SELECT id, email, username FROM users WHERE email = %s;", (TARGET_EMAIL,))
        target_user = cur.fetchone()
        if not target_user:
            raise RuntimeError(f"User {TARGET_EMAIL} không tồn tại trong database!")
        target_id = target_user[0]
        print(f"🎯 Target user: {target_user[1]} (id: {target_id}, username: {target_user[2]})")

        # 2. Cập nhật user namkimnam2@gmail.com (nếu có) và kết bạn
        cur.execute("SELECT id FROM users WHERE email = 'namkimnam2@gmail.com';")
        existing_other = cur.fetchone()
        if existing_other:
            other_id = existing_other[0]
            cur.execute("""
                UPDATE users 
                SET avatar_url = 'https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=400&auto=format&fit=crop&q=80',
                    is_gold_member = true
                WHERE id = %s;
            """, (other_id,))
            
            # Kết bạn với target_user
            cur.execute("""
                INSERT INTO friendships (id, user_id, friend_id, status, created_at)
                VALUES (%s, %s, %s, 'ACCEPTED', NOW())
                ON CONFLICT ON CONSTRAINT unique_friendship DO UPDATE SET status = 'ACCEPTED';
            """, (str(uuid.uuid4()), str(other_id), str(target_id)))
            print(f"✅ Đã kết bạn với user namkimnam2@gmail.com ({other_id})")

        # 3. Tạo các user mẫu
        now = datetime.datetime.now()
        for u in SAMPLE_USERS:
            user_id = u["id"]
            # Upsert user
            cur.execute("""
                INSERT INTO users (
                    id, email, username, password, first_name, last_name, 
                    avatar_url, dob, is_verified, is_gold_member, otp_failed_attempts, created_at
                )
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
                ON CONFLICT (email) DO UPDATE SET
                    username = EXCLUDED.username,
                    first_name = EXCLUDED.first_name,
                    last_name = EXCLUDED.last_name,
                    avatar_url = EXCLUDED.avatar_url,
                    is_gold_member = EXCLUDED.is_gold_member,
                    is_verified = EXCLUDED.is_verified
                RETURNING id;
            """, (
                user_id, u["email"], u["username"], BCRYPT_PASSWORD_HASH,
                u["first_name"], u["last_name"], u["avatar_url"],
                u["dob"], u["is_verified"], u["is_gold_member"], 0,
                now - datetime.timedelta(days=10)
            ))
            actual_user_id = cur.fetchone()[0]
            print(f"👤 User mẫu: {u['first_name']} {u['last_name']} (@{u['username']}) - id: {actual_user_id}")

            # 4. Tạo quan hệ kết bạn với target_user
            # Kiểm tra xem đã có quan hệ song phương chưa
            cur.execute("""
                SELECT id FROM friendships 
                WHERE (user_id = %s AND friend_id = %s) OR (user_id = %s AND friend_id = %s);
            """, (actual_user_id, target_id, target_id, actual_user_id))
            f_row = cur.fetchone()
            if f_row:
                cur.execute("UPDATE friendships SET status = 'ACCEPTED' WHERE id = %s;", (f_row[0],))
                print(f"   🤝 Cập nhật bạn bè: {u['username']} <-> target_user (id: {f_row[0]})")
            else:
                friendship_id = str(uuid.uuid4())
                cur.execute("""
                    INSERT INTO friendships (id, user_id, friend_id, status, created_at)
                    VALUES (%s, %s, %s, 'ACCEPTED', %s);
                """, (friendship_id, actual_user_id, target_id, now - datetime.timedelta(days=7)))
                print(f"   🤝 Tạo mới bạn bè: {u['username']} <-> target_user (id: {friendship_id})")

            # 5. Tạo ảnh khoảnh khắc (nếu có cấu hình)
            if u["photo"]:
                p = u["photo"]
                photo_time = now - datetime.timedelta(hours=p["hours_ago"])
                # Xóa photo cũ cùng ID nếu có để tránh conflict
                cur.execute("DELETE FROM photo_recipients WHERE photo_id = %s;", (p["id"],))
                cur.execute("DELETE FROM photos WHERE id = %s;", (p["id"],))
                
                cur.execute("""
                    INSERT INTO photos (
                        id, sender_id, image_url, caption, amount, is_private,
                        category_id, recipient_scope, status, recipient_count,
                        transaction_type, occurred_at, created_at
                    )
                    VALUES (%s, %s, %s, %s, %s, false, %s, 'ALL_FRIENDS', 'READY', 1, 'EXPENSE', %s, %s);
                """, (
                    p["id"], actual_user_id, p["image_url"], p["caption"],
                    p["amount"], p["category_id"], photo_time, photo_time
                ))

                # Thêm recipient là target_user
                cur.execute("""
                    INSERT INTO photo_recipients (id, photo_id, recipient_id, created_at)
                    VALUES (%s, %s, %s, %s);
                """, (str(uuid.uuid4()), p["id"], target_id, photo_time))
                print(f"   📸 Tạo photo khoảnh khắc: {p['caption']} (bởi @{u['username']})")

            # 6. Tạo tin nhắn mẫu (direct message)
            if u.get("message"):
                msg_time = now - datetime.timedelta(hours=u["photo"]["hours_ago"] if u["photo"] else 3)
                cur.execute("""
                    INSERT INTO direct_messages (id, sender_id, receiver_id, content, is_read, created_at)
                    VALUES (%s, %s, %s, %s, false, %s);
                """, (str(uuid.uuid4()), actual_user_id, target_id, u["message"], msg_time))
                print(f"   💬 Gửi tin nhắn mẫu: '{u['message']}'")

        conn.commit()
        print("\n🎉 Seed data thành công 100%!")

    except Exception as e:
        conn.rollback()
        print(f"❌ Lỗi khi seed data: {e}")
        raise
    finally:
        cur.close()
        conn.close()

if __name__ == "__main__":
    seed_data()
