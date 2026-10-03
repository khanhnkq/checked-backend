# Báo Cáo Đánh Giá Dự Án & Kế Hoạch Chuyển Đổi Storage Sang Garage S3 (Docker)

> **Tài liệu**: Đánh giá toàn diện hiện trạng mã nguồn `checked-backend` (`locket-clone`) và Kế hoạch chi tiết thay thế Cloudinary bằng Garage S3 Object Storage chạy trên Docker.  
> **Phiên bản**: 1.0  
> **Trạng thái**: Đã phê duyệt kiến trúc - Sẵn sàng triển khai  

---

## MỤC LỤC
1. [Tổng Quan Dự Án](#1-tổng-quan-dự-án)
2. [Đánh Giá Chi Tiết Mã Nguồn (Codebase Audit)](#2-đánh-giá-chi-tiết-mã-nguồn-codebase-audit)
   - [2.1. Điểm Tốt & Best Practices Đã Thực Hiện](#21-điểm-tốt--best-practices-đã-thực-hiện)
   - [2.2. Sai Sót, Lỗ Hổng & Technical Debt Cần Khắc Phục](#22-sai-sót-lỗ-hổng--technical-debt-cần-khắc-phục)
3. [Tại Sao Chọn Garage S3 Thay Vì Cloudinary?](#3-tại-sao-chọn-garage-s3-thay-vì-cloudinary)
4. [Kiến Trúc Hệ Thống Lưu Trữ Mới](#4-kiến-trúc-hệ-thống-lưu-trữ-mới)
5. [Thiết Kế Chi Tiết Giải Pháp Garage Trên Docker](#5-thiết-kế-chi-tiết-giải-pháp-garage-trên-docker)
   - [5.1. Cấu hình Garage (`garage.toml`)](#51-cấu-hình-garage-garagetoml)
   - [5.2. Docker Compose & Auto-Init Service (`compose.yaml`)](#52-docker-compose--auto-init-service-composeyaml)
   - [5.3. Biến Môi Trường (`.env.example` & `application.yml`)](#53-biến-môi-trường-envexample--applicationyml)
6. [Thiết Kế Tầng Mã Nguồn Backend (Spring Boot 4 / Java 21)](#6-thiết-kế-tầng-mã-nguồn-backend-spring-boot-4--java-21)
   - [6.1. Cấu trúc Package Mới](#61-cấu-trúc-package-mới)
   - [6.2. Dependencies Cần Thêm / Thay Thế](#62-dependencies-cần-thêm--thay-thế)
   - [6.3. Thiết Kế Storage Abstraction (`StorageService`)](#63-thiết-kế-storage-abstraction-storageservice)
   - [6.4. Xử Lý Ảnh & Thumbnail (`Thumbnailator`)](#64-xử-lý-ảnh--thumbnail-thumbnailator)
   - [6.5. Cấu Hình AWS S3 SDK v2 Tương Thích Garage](#65-cấu-hình-aws-s3-sdk-v2-tương-thích-garage)
7. [Lộ Trình Triển Khai Từng Bước (Implementation Roadmap)](#7-lộ-trình-triển-khai-từng-bước-implementation-roadmap)
8. [Chiến Lược Dữ Liệu & Kế Hoạch Rollback](#8-chiến-lược-dữ-liệu--kế-hoạch-rollback)

---

## 1. TỔNG QUAN DỰ ÁN

- **Tên dự án**: `checked-backend` (hoặc `locket-clone`).
- **Nền tảng kỹ thuật**: Java 21, Spring Boot 4.0.3, Gradle, PostgreSQL 15, Flyway Migration, Spring Security + JWT, Cloudinary (hiện tại).
- **Mục tiêu nghiệp vụ**:
  - Ứng dụng kết hợp giữa mô hình chia sẻ khoảnh khắc ảnh (tương tự Locket Widget) và quản lý tài chính cá nhân (chi tiêu - `EXPENSE`, thu nhập - `INCOME`, dòng tiền - `CASHFLOW`).
  - Người dùng chụp ảnh, đính kèm số tiền, gắn danh mục (`Category`), thêm ghi chú, chọn đối tượng nhận ảnh (`ALL_FRIENDS` hoặc `SELECTED_FRIENDS`).
  - Cung cấp báo cáo tài chính theo tháng/năm, thiết lập hạn mức ngân sách (`Budget`) và mục tiêu tiết kiệm (`SavingsGoal`).
  - Kết nối bạn bè bảo mật thông qua liên kết mời (`FriendInviteLink`) có token, TTL và giới hạn lượt sử dụng.

---

## 2. ĐÁNH GIÁ CHI TIẾT MÃ NGUỒN (CODEBASE AUDIT)

### 2.1. Điểm Tốt & Best Practices Đã Thực Hiện

| Hạng mục | Điểm nổi bật | Đánh giá |
| :--- | :--- | :--- |
| **Cấu trúc thư mục** | Phân chia theo tính năng (`package-by-feature`): `auth`, `user`, `photo`, `expense`, `friendship`, `notification`, `security`. Dễ dàng bảo trì và mở rộng độc lập. | ⭐ Tốt |
| **Java 21 Modern Features** | Tận dụng triệt để Java `record` cho tất cả DTOs (bất biến, an toàn đa luồng), Pattern Matching, Switch expressions hiện đại. | ⭐ Tốt |
| **Flyway Schema Evolution** | Lịch sử migration mạch lạc từ `V1` đến `V14`. Đầy đủ các ràng buộc khóa ngoại (`FK`), khóa duy nhất (`UNIQUE`), điều kiện (`CHECK`) và chỉ mục (`INDEX`). | ⭐ Tốt |
| **Tối ưu hóa JPA / JPQL** | Trong `PhotoRepository`, các query lấy feed/detail sử dụng `JOIN FETCH` để triệt tiêu lỗi N+1 query với quan hệ `sender` và `category`. Dùng `Slice` thay vì `Page` cho feed ảnh cuộn vô tận để tránh query `COUNT(*)` tốn kém. | ⭐ Tốt |
| **Chống Race Condition** | Sử dụng `@Lock(LockModeType.PESSIMISTIC_WRITE)` trong `FriendInviteLinkRepository` khi accept link kết bạn, bảo đảm tính toàn vẹn của số lượt sử dụng (`used_count`). | ⭐ Tốt |
| **Bảo mật Spring Security** | Cấu hình Stateless hoàn toàn với JWT, mã hóa mật khẩu chuẩn BCrypt, phân tách rõ luồng Onboarding (buộc cập nhật Profile trước khi vào Home). | ⭐ Tốt |
| **Tài liệu & CI/CD** | Bộ tài liệu phong phú trong thư mục `docs/` (`API_CONTRACT.md`, `DEPLOYMENT_GUIDE.md`, `CI_CD.md`). Dockerfile chuẩn multi-stage với user non-root `spring:spring`. | ⭐ Tốt |

---

### 2.2. Sai Sót, Lỗ Hổng & Technical Debt Cần Khắc Phục

#### 🔴 Nghiêm trọng (High Priority / Bugs)
1. **Vòng lặp 24 câu query trong `getYearlyCashflowSummary`**:
   - Vị trí: `ExpenseServiceImpl.java` (dòng 348–369).
   - Vấn đề: Vòng lặp `for (int month = 1; month <= 12; month++)` thực hiện 2 câu query riêng rẽ cho từng tháng (tổng cộng **24 lượt gọi DB** chỉ cho 1 request đơn lẻ!).
   - Giải pháp: Chuyển sang 1 câu query SQL duy nhất gom nhóm `GROUP BY EXTRACT(MONTH FROM ...)` hoặc truy vấn khoảng thời gian 1 năm rồi tổng hợp trong bộ nhớ.
2. **Lỗ hổng Brute-force OTP (Chưa có Rate Limiting)**:
   - Vị trí: `AuthServiceImpl.java` & `AuthController.java` (`POST /api/v1/auth/verify`).
   - Vấn đề: Mã OTP 6 chữ số có hạn 5 phút nhưng **không giới hạn số lần nhập sai**. Kẻ tấn công có thể dùng bot chạy thử vét cạn 1 triệu mã số trong 5 phút.
   - Giải pháp: Giới hạn tối đa 5 lần thử sai cho mỗi OTP, sau đó vô hiệu hóa mã và yêu cầu gửi lại; thêm rate limiting theo IP/email.
3. **Flyway Migration bị tắt trong bộ kiểm thử (Testing Flaw)**:
   - Vị trí: `src/test/resources/application.yml` (`flyway.enabled: false`, `ddl-auto: create-drop`).
   - Vấn đề: Toàn bộ 14 file SQL migration chưa từng được kiểm chứng khi chạy `./gradlew test` hay trên GitHub Actions. Nếu script SQL nào có lỗi cú pháp PostgreSQL, test vẫn pass nhưng deploy thực tế sẽ bị sập.
   - Giải pháp: Kích hoạt Flyway trong test với Testcontainers PostgreSQL.

#### 🟡 Trung bình (Medium Priority / Architecture & Clean Code)
4. **Vi phạm nguyên tắc Separation of Concerns trong Controller**:
   - Vị trí: `UserController.java` (dòng 74).
   - Vấn đề: `UserController` tiêm trực tiếp `CloudinaryService` để xử lý upload avatar thay vì thông qua `UserService` hoặc tầng `StorageService`.
   - Giải pháp: Đưa toàn bộ nghiệp vụ lưu trữ vào Service layer.
5. **Rò rỉ tài nguyên lưu trữ (Orphaned Files)**:
   - Vấn đề: Khi người dùng đổi avatar mới hoặc xóa ảnh (`PhotoStatus.DELETED`), ảnh cũ trên storage không bao giờ bị xóa.
   - Giải pháp: Bổ sung phương thức `deleteFile(String key)` trong tầng lưu trữ và kích hoạt dọn dẹp khi cập nhật avatar/xóa ảnh.
6. **Phương thức rỗng không triển khai nghiệp vụ**:
   - Vị trí: `FriendshipServiceImpl.java` (dòng 21–28).
   - Vấn đề: `sendFriendRequest` và `acceptFriendRequest` bỏ trống hoàn toàn `{ }`.
   - Giải pháp: Xóa khỏi interface nếu đã chuyển hẳn sang luồng Link mời, hoặc implement hoàn thiện.
7. **CORS cấu hình cứng (Hardcoded)**:
   - Vị trí: `CorsConfig.java` (dòng 17).
   - Vấn đề: Fix cứng `http://localhost:5173` và `3000`. Khi deploy production sẽ chặn toàn bộ request từ domain thật hoặc mobile.
   - Giải pháp: Đọc cấu hình từ biến môi trường `CORS_ALLOWED_ORIGINS`.
8. **Độ dài cột `avatar_url` trong bảng `users` ngắn**:
   - Vị trí: `User.java` và migration SQL (`VARCHAR(255)`).
   - Vấn đề: Các URL S3 có query token hoặc CDN dài dễ vượt quá 255 ký tự dẫn đến lỗi `DataTruncationException`.
   - Giải pháp: Viết migration `V15` nâng lên `VARCHAR(500)`.

#### 🟢 Nhẹ (Low Priority / Tech Debt & Leftovers)
9. **Comment rác của AI sót lại**:
   - Vị trí: `Photo.java` (dòng 24): `// ...existing code...`.
10. **Dead code / DTOs và Config mồ côi**:
    - `VerifyFirebaseTokenRequest.java`, `FirebaseConfig.java`, `SetPasswordRequest.java`, `UserRequest.java`, `UpdateFcmTokenRequest.java`.
    - `DirectMessage.java` & `DirectMessageRepository.java` (module chat bị bỏ dở).
11. **Gửi email OTP đồng bộ (Synchronous SMTP)**:
    - `AuthServiceImpl.java` gọi SMTP trực tiếp trên luồng HTTP của Tomcat. Cần gắn `@Async` để tránh nghẽn thread.
12. **Chưa tận dụng MapStruct cho `PhotoMapper`**:
    - Dự án đã cài MapStruct cho `UserMapper` nhưng `PhotoMapper` lại viết tay boilerplate.

---

## 3. TẠI SAO CHỌN GARAGE S3 THAY VÌ CLOUDINARY?

| Tiêu chí | Cloudinary | MinIO | Garage S3 (Lựa chọn) |
| :--- | :--- | :--- | :--- |
| **Mô hình** | SaaS bên thứ 3 (Vendor Lock-in) | Self-hosted S3 | Self-hosted S3 (Distributed & Lightweight) |
| **Chi phí** | Miễn phí giới hạn (25 credit/tháng). Vượt mức rất đắt ($89–$99+/tháng). | Miễn phí (Chỉ tốn tài nguyên server) | Miễn phí (Chỉ tốn tài nguyên server) |
| **Mức tiêu hao RAM** | Không tốn RAM server | Nặng (200MB – 1GB+ RAM) | **Siêu nhẹ (~30MB – 50MB RAM)**, viết bằng Rust |
| **Bảo mật dữ liệu** | Ảnh hóa đơn, chi tiêu tài chính lưu ở máy chủ bên thứ 3 | Hoàn toàn làm chủ dữ liệu nội bộ | **Hoàn toàn làm chủ dữ liệu nội bộ** |
| **Khả năng mở rộng** | Phụ thuộc gói trả tiền | Cần cấu hình cụm phức tạp | **Thiết kế nguyên bản cho cụm phân tán (Distributed by design)** |
| **Giao thức chuẩn** | Proprietary REST API / SDK riêng | Chuẩn AWS S3 API | **Chuẩn AWS S3 API (Dùng chung AWS SDK v2)** |

---

## 4. KIẾN TRÚC HỆ THỐNG LƯU TRỮ MỚI

```
                  ┌───────────────────────────────┐
                  │   Flutter Mobile App / Web    │
                  └──────────────┬────────────────┘
                                 │
                 1. Upload ảnh   │   5. Truy cập ảnh trực tiếp
                 (MultipartFile) │      (HTTP GET Port 3902)
                                 ▼               ▲
                  ┌────────────────────────┐     │
                  │   Spring Boot Backend  │     │
                  │ (checked-backend:8080) │     │
                  └──────────────┬─────────┘     │
                                 │               │
                 2. Nén & tạo    │               │
                    Thumbnail    │               │
                 (Thumbnailator) │               │
                                 ▼               │
                 3. PutObject S3 │               │
                    (AWS SDK v2) │               │
                                 ▼               │
                  ┌──────────────────────────────┴┐
                  │       Garage S3 Storage       │
                  │    (Docker Container: 3900)   │
                  │   S3 Web Endpoint: Port 3902  │
                  └──────────────┬────────────────┘
                                 │
                         Ghi dữ liệu xuống
                                 │
                 ┌───────────────┴───────────────┐
                 │ Volumes: garage_data & _meta  │
                 └───────────────────────────────┘
```

### Cơ chế xử lý ảnh & Thumbnail tự động:
1. Khi client gửi ảnh lên backend:
   - Backend sử dụng thư viện **Thumbnailator** (`net.coobird:thumbnailator`) nén ảnh gốc về kích thước chuẩn (tối đa 1920px, chất lượng 85%).
   - Tự động sinh thêm 1 bản **Thumbnail** vuông (320x320 px, crop center).
2. Backend đẩy cả 2 file lên Garage S3 bucket:
   - Bản gốc: `photos/yyyy/MM/{uuid}.webp`
   - Bản thumb: `photos/yyyy/MM/{uuid}_thumb.webp`
3. Trả về `imageUrl` và `thumbnailUrl` tương thích 100% với contract hiện tại của Mobile app.

---

## 5. THIẾT KẾ CHI TIẾT GIẢI PHÁP GARAGE TRÊN DOCKER

### 5.1. Cấu hình Garage (`docker/garage/garage.toml`)
Tạo file cấu hình cho Garage node:

```toml
metadata_dir = "/var/lib/garage/meta"
data_dir = "/var/lib/garage/data"
db_engine = "sqlite"

replication_factor = 1

rpc_bind_addr = "0.0.0.0:3901"
rpc_public_addr = "garage:3901"
rpc_secret = "b4a8e23f98234857ef129845cd782345ba892345ef129845cd782345ba892345"

[s3_api]
s3_api_bind_addr = "0.0.0.0:3900"
root_domain = ".s3.garage"

[s3_web]
bind_addr = "0.0.0.0:3902"
root_domain = ".web.garage"

[admin]
api_bind_addr = "0.0.0.0:3903"
admin_token = "garage_admin_secret_token_change_in_production"
```

---

### 5.2. Docker Compose & Auto-Init Service (`compose.yaml`)
Cập nhật file `compose.yaml` để tự động hóa hoàn toàn việc dựng Garage, cấu hình layout, tạo access key và tạo bucket public:

```yaml
services:
  postgres:
    image: 'postgres:15-alpine'
    container_name: locket-clone-db
    environment:
      - POSTGRES_DB=${DB_NAME:-locket_clone}
      - POSTGRES_USER=${DB_USER:-postgres}
      - POSTGRES_PASSWORD=${DB_PASS:-postgres}
    ports:
      - '5432:5432'
    volumes:
      - pgdata:/var/lib/postgresql/data
    restart: unless-stopped

  garage:
    image: 'dxflrs/garage:v1.1.0'
    container_name: locket-clone-garage
    volumes:
      - ./docker/garage/garage.toml:/etc/garage.toml
      - garage_meta:/var/lib/garage/meta
      - garage_data:/var/lib/garage/data
    ports:
      - '3900:3900' # S3 API
      - '3902:3902' # S3 Web (Public Image CDN)
      - '3903:3903' # Admin API
    restart: unless-stopped

  # Container khởi tạo tự động (Layout, Access Key, Bucket) chỉ chạy 1 lần lúc start
  garage-init:
    image: 'dxflrs/garage:v1.1.0'
    container_name: locket-clone-garage-init
    depends_on:
      - garage
    entrypoint: >
      /bin/sh -c "
      echo 'Waiting for Garage daemon...';
      sleep 3;
      NODE_ID=$$(garage -c /etc/garage.toml status | grep -oE '[a-f0-9]{64}' | head -n 1);
      if [ -n \"$$NODE_ID\" ]; then
        echo 'Found Garage Node ID: ' $$NODE_ID;
        garage -c /etc/garage.toml layout assign -z dc1 -c 10G $$NODE_ID 2>/dev/null || true;
        garage -c /etc/garage.toml layout apply --version 1 2>/dev/null || true;
        garage -c /etc/garage.toml key create locket-app-key --key-id ${GARAGE_ACCESS_KEY_ID:-app_key_id} --secret-key ${GARAGE_SECRET_ACCESS_KEY:-app_secret_key_123} 2>/dev/null || true;
        garage -c /etc/garage.toml bucket create ${GARAGE_BUCKET:-locket-photos} 2>/dev/null || true;
        garage -c /etc/garage.toml bucket allow ${GARAGE_BUCKET:-locket-photos} --read --write --key locket-app-key 2>/dev/null || true;
        garage -c /etc/garage.toml bucket website --allow ${GARAGE_BUCKET:-locket-photos} 2>/dev/null || true;
        echo 'Garage cluster initialization completed!';
      fi;
      exit 0;
      "
    volumes:
      - ./docker/garage/garage.toml:/etc/garage.toml
    restart: "no"

volumes:
  pgdata:
  garage_meta:
  garage_data:
```

---

### 5.3. Biến Môi Trường (`.env.example` & `application.yml`)

#### Thêm vào `.env.example`:
```env
# ── Storage Configuration (garage / cloudinary) ───────────
STORAGE_TYPE=garage

# Cấu hình Garage S3
GARAGE_ENDPOINT=http://localhost:3900
GARAGE_PUBLIC_BASE_URL=http://localhost:3902/locket-photos
GARAGE_ACCESS_KEY_ID=app_key_id
GARAGE_SECRET_ACCESS_KEY=app_secret_key_123
GARAGE_BUCKET=locket-photos
GARAGE_REGION=garage
```

#### Thêm vào `application.yml`:
```yaml
storage:
  type: ${STORAGE_TYPE:garage}
  s3:
    endpoint: ${GARAGE_ENDPOINT:http://localhost:3900}
    public-base-url: ${GARAGE_PUBLIC_BASE_URL:http://localhost:3902/locket-photos}
    access-key-id: ${GARAGE_ACCESS_KEY_ID:app_key_id}
    secret-access-key: ${GARAGE_SECRET_ACCESS_KEY:app_secret_key_123}
    bucket-name: ${GARAGE_BUCKET:locket-photos}
    region: ${GARAGE_REGION:garage}
```

---

## 6. THIẾT KẾ TẦNG MÃ NGUỒN BACKEND (SPRING BOOT 4 / JAVA 21)

### 6.1. Cấu trúc Package Mới
Tạo package `storage/` tách biệt hoàn toàn logic lưu trữ:

```
com.codegym.locketclone.storage/
├── StorageService.java               # Interface trừu tượng chung
├── UploadedFile.java                 # Record thay thế UploadedImage
├── s3/
│   ├── S3ClientConfig.java           # Bean cấu hình AWS S3Client v2 cho Garage
│   ├── S3StorageProperties.java      # @ConfigurationProperties("storage.s3")
│   └── GarageS3StorageService.java   # Triển khai S3 cho Garage
├── image/
│   └── ImageProcessingService.java   # Xử lý nén & tạo thumbnail bằng Thumbnailator
└── legacy/
    └── CloudinaryStorageService.java # Giữ lại fallback nếu cần rollback
```

---

### 6.2. Dependencies Cần Thêm / Thay Thế trong `build.gradle`

Loại bỏ dependency Cloudinary và thêm AWS S3 SDK v2 cùng Thumbnailator:

```groovy
dependencies {
    // Thay thế com.cloudinary:cloudinary-http44:1.36.0 bằng:
    implementation platform('software.amazon.awssdk:bom:2.29.50')
    implementation 'software.amazon.awssdk:s3'
    implementation 'net.coobird:thumbnailator:0.4.20'

    // ... các dependencies khác giữ nguyên ...
}
```

---

### 6.3. Thiết Kế Storage Abstraction (`StorageService`)

```java
package com.codegym.locketclone.storage;

import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

public interface StorageService {
    UploadedFile uploadPhoto(MultipartFile file) throws IOException;
    UploadedFile uploadAvatar(MultipartFile file) throws IOException;
    void deleteFile(String key);
}
```

#### Record `UploadedFile`:
```java
package com.codegym.locketclone.storage;

public record UploadedFile(
        String secureUrl,
        String thumbnailUrl,
        String key,
        String mimeType,
        Long bytes,
        Integer width,
        Integer height
) {}
```

---

### 6.4. Xử Lý Ảnh & Thumbnail (`Thumbnailator`)

`ImageProcessingService` chịu trách nhiệm resize và nén ảnh:
- **Ảnh gốc**: Nén giữ tỷ lệ khung hình, chiều dài tối đa 1920px, chất lượng 85%.
- **Ảnh thumbnail**: Resize vuông 320x320 px, tối ưu cho widget Locket và avatar.

---

### 6.5. Cấu Hình AWS S3 SDK v2 Tương Thích Garage

```java
package com.codegym.locketclone.storage.s3;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

@Configuration
@ConditionalOnProperty(name = "storage.type", havingValue = "garage", matchIfMissing = true)
public class S3ClientConfig {

    @Bean
    public S3Client s3Client(S3StorageProperties props) {
        return S3Client.builder()
                .endpointOverride(URI.create(props.getEndpoint()))
                .region(Region.of(props.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(props.getAccessKeyId(), props.getSecretAccessKey())
                ))
                // Bắt buộc bật pathStyleAccessEnabled đối với Garage / MinIO
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }
}
```

---

## 7. LỘ TRÌNH TRIỂN KHAI TỪNG BƯỚC (IMPLEMENTATION ROADMAP)

### 🗓️ Giai Đoạn 1: Chuẩn Bị Hạ Tầng Garage Docker (Đã hoàn thành)
- [x] Tạo thư mục `docker/garage/` và file cấu hình `garage.toml`.
- [x] Cập nhật file `compose.yaml` bổ sung `garage` và `garage-init`.
- [x] Thêm các biến cấu hình S3 vào `.env.example` và `application.yml`.
- [x] Chạy `docker compose up -d --build garage garage-init` và xác nhận bucket `locket-photos` đã sẵn sàng trên cổng `3900` và `3902`.

### 🗓️ Giai Đoạn 2: Xây Dựng Tầng Storage Trong Backend (Đã hoàn thành)
- [x] Cập nhật `build.gradle` bổ sung `software.amazon.awssdk:s3` và `Thumbnailator`.
- [x] Tạo package `storage/` với `StorageService`, `UploadedFile`, `ImageProcessingService`.
- [x] Triển khai `GarageS3StorageService`:
  - Upload file gốc + file thumbnail.
  - Sinh đường dẫn URL public theo mẫu `http://<domain>:3902/locket-photos/<key>`.
  - Triển khai hàm `deleteFile(String key)` xóa cả file gốc và thumbnail trên bucket.
- [x] Giữ lại `CloudinaryStorageService` làm fallback khi `STORAGE_TYPE=cloudinary`.

### 🗓️ Giai Đoạn 3: Tích Hợp Vào Nghiệp Vụ & Sửa Các Lỗi Tồn Đọng (Đã hoàn thành)
- [x] Thay thế `CloudinaryService` trong `PhotoServiceImpl` bằng `StorageService`.
- [x] Chuyển logic upload avatar từ `UserController` vào `UserServiceImpl`.
- [x] Xóa bỏ comment `// ...existing code...` trong `Photo.java`.
- [x] Dọn dẹp các class mồ côi: `VerifyFirebaseTokenRequest`, `FirebaseConfig`, `SetPasswordRequest`, `UserRequest`, `UpdateFcmTokenRequest`.
- [x] Tối ưu hóa truy vấn vòng lặp 24 lần trong `getYearlyCashflowSummary`.
- [x] Thêm migration `V15__harden_schema_and_indexes.sql` mở rộng `avatar_url` thành `VARCHAR(500)`.

### 🗓️ Giai Đoạn 4: Kiểm Thử Toàn Diện (Đã hoàn thành)
- [x] Viết Unit Test cho `GarageS3StorageServiceTest` và `ImageProcessingServiceTest`.
- [x] Cập nhật `src/test/resources/application.yml` tương thích với cấu hình mới.
- [x] Chạy toàn bộ test suite: `./gradlew test` (79/79 test cases PASS 100%).
- [x] Đóng gói `./gradlew bootJar` thành công.
- [x] Viết script tiện ích `scripts/setup-garage.sh` kiểm tra tự động trạng thái bucket.

---

## 8. CHIẾN LƯỢC DỮ LIỆU & KẾ HOẠCH ROLLBACK

### Khả năng tương thích hai nguồn dữ liệu (Dual-Read Compatibility)
- Các ảnh cũ đã tải lên Cloudinary trước đây vẫn giữ nguyên URL dạng `https://res.cloudinary.com/...` trong cơ sở dữ liệu. Ứng dụng di động (Frontend) vẫn đọc và hiển thị bình thường.
- Các ảnh mới tải lên sẽ có URL dạng `http://<domain-garage>:3902/locket-photos/...`.
- **Không làm gián đoạn hệ thống hiện tại**: Không bắt buộc phải convert ảnh cũ ngay lập tức. Nếu muốn chuyển toàn bộ ảnh cũ từ Cloudinary về Garage, có thể chạy một background worker script riêng để tải ảnh về và re-upload lên Garage.

### Kế hoạch Rollback tức thì
- Hệ thống hỗ trợ biến môi trường `STORAGE_TYPE=garage` hoặc `STORAGE_TYPE=cloudinary`.
- Nếu hạ tầng Garage gặp sự cố ngoài dự kiến, chỉ cần cập nhật biến môi trường `STORAGE_TYPE=cloudinary` và khởi động lại backend, hệ thống sẽ tự động fallback về Cloudinary ngay lập tức mà không cần rollback code.
