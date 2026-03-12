# CI/CD cho `locket-clone`

Repo này hiện đã có 2 workflow GitHub Actions:

- `CI` — chạy khi có `pull_request` vào `main` hoặc `push` vào `main`
- `Release` — chạy khi push tag theo format `v*.*.*`

---

## 1. CI workflow

File: `.github/workflows/ci.yml`

### Mục tiêu
- checkout source
- setup Java 21
- cache Gradle dependencies
- chạy `clean test bootJar`
- build thử Docker image để chắc `Dockerfile` luôn hợp lệ
- upload test report và file JAR làm artifact

### Trigger
- pull request vào `main`
- push vào `main`

### Kết quả
- nếu test fail: có artifact chứa test reports để debug
- nếu build pass: có artifact chứa file `.jar`
- nếu Docker build fail: PR sẽ fail ngay, tránh merge Dockerfile hỏng

---

## 2. Release workflow

File: `.github/workflows/release.yml`

### Mục tiêu
- build lại project từ source sạch
- chạy test trước khi release
- tạo checksum `SHA256SUMS.txt`
- tạo GitHub Release
- attach file JAR + checksum vào release
- build và push Docker image lên GHCR

### Trigger
- push tag theo format semantic version, ví dụ:

```bash
git tag v1.0.0
git push origin v1.0.0
```

### Kết quả
Sau khi workflow chạy xong, trên GitHub sẽ có:
- một Release mới
- file JAR của backend
- file `SHA256SUMS.txt`
- Docker image trên GHCR

Image dự kiến có dạng:

```text
ghcr.io/<github-owner>/locket-clone-backend:<tag>
```

---

## 3. Docker build strategy hiện tại

Repo hiện đã có:
- `Dockerfile` multi-stage
- `.dockerignore`

### Dockerfile làm gì
- stage 1: dùng JDK 21 để chạy `bootJar -x test`
- stage 2: dùng JRE 21 để chạy app nhẹ hơn
- chạy bằng user không phải root

### Local build

```bash
docker build -t locket-clone:local .
```

### Local run

```bash
docker run --rm -p 8080:8080 \
  -e DB_URL=jdbc:postgresql://host.docker.internal:5432/locket_clone \
  -e DB_USER=postgres \
  -e DB_PASS=postgres \
  -e JWT_SECRET=your-base64-secret \
  -e JWT_EXPIRATION_MS=86400000 \
  -e MAIL_HOST=smtp.gmail.com \
  -e MAIL_PORT=587 \
  -e MAIL_USERNAME=your-mail@gmail.com \
  -e MAIL_PASSWORD=your-app-password \
  -e MAIL_FROM=your-mail@gmail.com \
  -e CLOUDINARY_CLOUD_NAME=demo \
  -e CLOUDINARY_API_KEY=demo \
  -e CLOUDINARY_API_SECRET=demo \
  locket-clone:local
```

> `host.docker.internal` phù hợp khi backend container chạy trên Docker Desktop và DB chạy ở máy host.

---

## 4. Secrets hiện cần / không cần

### CI
CI **không cần** thêm secret custom để chạy:
- test dùng config trong `src/test/resources/application.yml`
- Docker validation chỉ build image, không push

### Release
Release hiện dùng:
- `GITHUB_TOKEN` mặc định để tạo GitHub Release
- `GITHUB_TOKEN` mặc định để push image lên GHCR

Không cần thêm Docker registry secret riêng nếu dùng GHCR trong cùng repo/account.

---

## 5. Vì sao hướng Docker này hợp lý cho repo hiện tại

Repo đã có `compose.yaml` và backend Spring Boot, nên Docker image là bước tự nhiên để:
- dễ deploy lên VPS/cloud sau này
- chuẩn hóa runtime environment
- đỡ lệ thuộc máy local khi release

Hiện tại workflow mới đang ở mức:
- **CI**: verify code + verify Docker build
- **CD**: publish JAR + publish Docker image

Đây là mức rất thực tế để sau này bạn nối tiếp sang VPS/Kubernetes/Render/Railway.

---

## 6. Nâng cấp tiếp theo nếu muốn deploy thật

Khi bạn sẵn sàng deploy lên server, có thể mở rộng thêm workflow thứ 3, ví dụ:
- SSH vào VPS để pull image mới và restart container
- deploy lên Render/Railway/Fly.io/AWS
- dùng `docker compose pull && docker compose up -d`

Lúc đó nên thêm các secrets kiểu:
- `DB_URL`
- `DB_USER`
- `DB_PASS`
- `JWT_SECRET`
- `MAIL_USERNAME`
- `MAIL_PASSWORD`
- `CLOUDINARY_*`
- `SSH_PRIVATE_KEY`
- `SSH_HOST`
- `SSH_USER`

---

## 7. Quy trình release đề xuất

```bash
git checkout main
git pull origin main
git tag v1.0.0
git push origin main
git push origin v1.0.0
```

Sau đó vào tab **Actions**, **Packages**, hoặc **Releases** trên GitHub để kiểm tra artifact và Docker image đã được publish.
