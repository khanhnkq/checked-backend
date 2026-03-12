# checked-backend

Spring Boot backend cho `locket-clone`, dùng Gradle, Java 21 và có sẵn `Dockerfile` để build container image.

## 🚀 Chạy nhanh trên GitHub Codespaces

1. Nhấn nút **Code → Codespaces → Create codespace on main** trên GitHub.
2. Chờ Codespace khởi động và cài đặt các extensions (lần đầu ~3–5 phút).
3. Sao chép file môi trường và điền giá trị của bạn:
   ```bash
   cp .env.example .env
   # Mở .env và điền JWT_SECRET, thông tin SMTP, Cloudinary, v.v.
   ```
4. Khởi động PostgreSQL và chạy ứng dụng:
   ```bash
   # Tải biến môi trường từ .env (bash/zsh)
   set -o allexport && source .env && set +o allexport
   # Hoặc trên fish shell: export (cat .env | psub)

   # Khởi động PostgreSQL bằng Docker Compose
   docker compose up -d

   # Chạy ứng dụng Spring Boot
   ./gradlew bootRun
   ```
   > **Lưu ý:** Lệnh `source .env` hoạt động tốt trên **bash** và **zsh** (mặc định trong Codespace).
   > Nếu dùng shell khác, hãy export từng biến hoặc dùng plugin tương ứng.
5. Codespace sẽ tự động forward cổng **8080**. Nhấn vào thông báo "Open in Browser" hoặc vào tab **PORTS** để mở API.

### 🔄 Để Codespace cập nhật nhanh sau khi push lên `main`

Nếu bạn muốn mỗi lần push lên `main` thì **Codespace tạo mới** hoặc **Codespace được rebuild** dùng ngay môi trường mới nhất, hãy bật:

**GitHub → Repository Settings → Codespaces → Prebuild configurations**

- tạo prebuild cho branch `main`
- giữ trigger mặc định để GitHub tự update prebuild sau mỗi lần push

Repo này đã được cấu hình `onCreateCommand` và `updateContentCommand` trong [`.devcontainer/devcontainer.json`](.devcontainer/devcontainer.json) để prebuild cũng tải sẵn Gradle dependencies khi code thay đổi.

> **Lưu ý quan trọng:** GitHub **không tự động pull commit mới vào một Codespace đang mở sẵn**. Sau khi push lên `main`, Codespace cũ vẫn cần một trong các cách sau để lấy code mới:
> - chạy `git pull origin main`
> - hoặc dùng lệnh **Codespaces: Rebuild Container**
> - hoặc tạo Codespace mới từ branch `main` để dùng prebuild mới nhất

### Biến môi trường bắt buộc

| Biến | Mô tả |
|------|-------|
| `DB_URL` | JDBC URL của PostgreSQL (ví dụ: `jdbc:postgresql://localhost:5432/locket_clone`) |
| `DB_USER` | Tên đăng nhập PostgreSQL |
| `DB_PASS` | Mật khẩu PostgreSQL |
| `JWT_SECRET` | Chuỗi bí mật Base64 dùng để ký JWT |
| `JWT_EXPIRATION_MS` | Thời hạn token tính bằng millisecond (ví dụ: `86400000` = 24h) |
| `MAIL_HOST` | SMTP host (ví dụ: `smtp.gmail.com`) |
| `MAIL_PORT` | SMTP port (mặc định: `587`) |
| `MAIL_USERNAME` | Địa chỉ email gửi OTP |
| `MAIL_PASSWORD` | App password của email |
| `CLOUDINARY_CLOUD_NAME` | Tên cloud Cloudinary |
| `CLOUDINARY_API_KEY` | API key Cloudinary |
| `CLOUDINARY_API_SECRET` | API secret Cloudinary |

Xem thêm tại [`.env.example`](.env.example).

## 🏃 Chạy cục bộ (Local)

Yêu cầu: **Java 21**, **Docker** (để chạy PostgreSQL).

```bash
cp .env.example .env
# Điền các giá trị trong .env

# bash/zsh
set -o allexport && source .env && set +o allexport

docker compose up -d
./gradlew bootRun
```

## CI/CD

Repo hiện có GitHub Actions cho:
- build + test + validate Docker build trên `pull_request` và `push` vào `main`
- release file JAR + publish Docker image lên GHCR khi push tag version dạng `v*.*.*`

Xem chi tiết tại `docs/CI_CD.md`.
