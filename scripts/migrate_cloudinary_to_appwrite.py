#!/usr/bin/env python3
"""
Migration Script: Cloudinary -> Appwrite Cloud Pro Storage
Database: Supabase PostgreSQL (checked-backend)
Target Storage: Appwrite Bucket 'checked-storage' (Frankfurt - fra)
"""

import json
import os
import sys
import time
from datetime import datetime
import psycopg2
import requests

# Cấu hình Database & Appwrite từ Environment hoặc Default
DB_URL = os.getenv(
    "DB_URL",
    "postgresql://postgres.qxcbcjcrhslsarpsbtod:0703586224Kh%40nh@aws-0-ap-southeast-1.pooler.supabase.com:5432/postgres?sslmode=require"
)
APPWRITE_ENDPOINT = os.getenv("APPWRITE_ENDPOINT", "https://fra.cloud.appwrite.io/v1")
APPWRITE_PROJECT_ID = os.getenv("APPWRITE_PROJECT_ID", "backend-storage-hub")
APPWRITE_BUCKET_ID = os.getenv("APPWRITE_BUCKET_ID", "checked-storage")
APPWRITE_API_KEY = os.getenv(
    "APPWRITE_API_KEY",
    "standard_3b92caaf70ba722c9c757d0b62498ae543e92f668606563bc85af093b8a36df284e6fb703eee35a721a41551aa544be5c6cadbd3a241e4a748549e6ce2b628f66d0c7ac34f3ecd503ec8ea8bdd5031b20cf68bb2d4685ab845571be53dc12dc1be30b2b8fa75c16f7c7e1273e44403b7b00096276265a3671237f268b94a1e19"
)

HEADERS = {
    "X-Appwrite-Project": APPWRITE_PROJECT_ID,
    "X-Appwrite-Key": APPWRITE_API_KEY
}


def download_bytes(url: str, retries=3) -> bytes:
    for attempt in range(retries):
        try:
            resp = requests.get(url, timeout=20)
            resp.raise_for_status()
            return resp.content
        except Exception as e:
            if attempt == retries - 1:
                raise
            time.sleep(1 + attempt)


def upload_to_appwrite(file_bytes: bytes, filename: str, content_type: str = "image/jpeg") -> str:
    url = f"{APPWRITE_ENDPOINT}/storage/buckets/{APPWRITE_BUCKET_ID}/files"
    files = {
        "fileId": (None, "unique()"),
        "file": (filename, file_bytes, content_type),
        "permissions[]": (None, 'read("any")')
    }
    resp = requests.post(url, headers=HEADERS, files=files, timeout=30)
    if not resp.ok:
        raise RuntimeError(f"Appwrite upload failed [{resp.status_code}]: {resp.text}")
    data = resp.json()
    return data["$id"]


def build_appwrite_photo_urls(file_id: str):
    base = f"{APPWRITE_ENDPOINT}/storage/buckets/{APPWRITE_BUCKET_ID}/files/{file_id}/preview"
    photo_url = f"{base}?project={APPWRITE_PROJECT_ID}&width=1080&height=1080&output=webp"
    thumb_url = f"{base}?project={APPWRITE_PROJECT_ID}&width=320&height=320&output=webp"
    return photo_url, thumb_url


def build_appwrite_avatar_url(file_id: str):
    base = f"{APPWRITE_ENDPOINT}/storage/buckets/{APPWRITE_BUCKET_ID}/files/{file_id}/preview"
    return f"{base}?project={APPWRITE_PROJECT_ID}&width=500&height=500&output=webp"


def main():
    print("=" * 60)
    print("🚀 BẮT ĐẦU DI CHUYỂN DỮ LIỆU TỪ CLOUDINARY SANG APPWRITE STORAGE")
    print("=" * 60)
    print(f"• Appwrite Project: {APPWRITE_PROJECT_ID}")
    print(f"• Appwrite Bucket:  {APPWRITE_BUCKET_ID}")
    print(f"• Database URL:     {DB_URL.split('@')[-1]}")
    print("-" * 60)

    conn = psycopg2.connect(DB_URL)
    conn.autocommit = False
    cursor = conn.cursor()

    try:
        # 1. Lấy danh sách Photos cần migrate
        cursor.execute("""
            SELECT id, image_url, thumbnail_url, public_id 
            FROM photos 
            WHERE image_url LIKE '%res.cloudinary.com%';
        """)
        photos = cursor.fetchall()
        print(f"📸 Tìm thấy {len(photos)} ảnh photos đang lưu trên Cloudinary.")

        # 2. Lấy danh sách Users cần migrate avatar
        cursor.execute("""
            SELECT id, username, avatar_url 
            FROM users 
            WHERE avatar_url LIKE '%res.cloudinary.com%';
        """)
        avatars = cursor.fetchall()
        print(f"👤 Tìm thấy {len(avatars)} avatar đang lưu trên Cloudinary.")

        # 3. Tạo file backup metadata trước khi thực hiện
        backup_data = {
            "timestamp": datetime.now().isoformat(),
            "photos": [
                {"id": str(p[0]), "image_url": p[1], "thumbnail_url": p[2], "public_id": p[3]}
                for p in photos
            ],
            "avatars": [
                {"id": str(a[0]), "username": a[1], "avatar_url": a[2]}
                for a in avatars
            ]
        }
        backup_file = os.path.join(os.path.dirname(__file__), "cloudinary_backup.json")
        with open(backup_file, "w", encoding="utf-8") as f:
            json.dump(backup_data, f, indent=2)
        print(f"💾 Đã lưu file backup metadata tại: {backup_file}")
        print("-" * 60)

        # 4. Migrate Photos
        photo_success = 0
        photo_fail = 0
        for idx, (p_id, img_url, thumb_url, pub_id) in enumerate(photos, 1):
            print(f"[{idx}/{len(photos)}] Đang di chuyển Photo ID: {p_id}...")
            try:
                # Tải file từ Cloudinary
                img_bytes = download_bytes(img_url)
                filename = f"migrated_photo_{p_id}.jpg"
                
                # Upload lên Appwrite
                file_id = upload_to_appwrite(img_bytes, filename)
                new_img_url, new_thumb_url = build_appwrite_photo_urls(file_id)

                # Cập nhật DB
                cursor.execute("""
                    UPDATE photos 
                    SET image_url = %s,
                        thumbnail_url = %s,
                        public_id = %s
                    WHERE id = %s;
                """, (new_img_url, new_thumb_url, file_id, p_id))
                conn.commit()
                photo_success += 1
                print(f"   ✅ Thành công -> Appwrite File ID: {file_id}")
            except Exception as ex:
                conn.rollback()
                photo_fail += 1
                print(f"   ❌ Thất bại: {ex}")

        print("-" * 60)

        # 5. Migrate Avatars
        avatar_success = 0
        avatar_fail = 0
        for idx, (u_id, username, avt_url) in enumerate(avatars, 1):
            print(f"[{idx}/{len(avatars)}] Đang di chuyển Avatar của User: {username} (ID: {u_id})...")
            try:
                avt_bytes = download_bytes(avt_url)
                filename = f"migrated_avatar_{username}_{u_id}.jpg"

                file_id = upload_to_appwrite(avt_bytes, filename)
                new_avt_url = build_appwrite_avatar_url(file_id)

                cursor.execute("""
                    UPDATE users 
                    SET avatar_url = %s
                    WHERE id = %s;
                """, (new_avt_url, u_id))
                conn.commit()
                avatar_success += 1
                print(f"   ✅ Thành công -> Appwrite File ID: {file_id}")
            except Exception as ex:
                conn.rollback()
                avatar_fail += 1
                print(f"   ❌ Thất bại: {ex}")

        print("=" * 60)
        print("🎉 KẾT QUẢ DI CHUYỂN DỮ LIỆU:")
        print(f"• Photos:  {photo_success} thành công, {photo_fail} thất bại / Tổng {len(photos)}")
        print(f"• Avatars: {avatar_success} thành công, {avatar_fail} thất bại / Tổng {len(avatars)}")
        print("=" * 60)

    finally:
        cursor.close()
        conn.close()


if __name__ == "__main__":
    main()
