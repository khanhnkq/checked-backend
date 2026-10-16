# Kế Hoạch Tích Hợp Appwrite Storage & Di Chuyển Từ Heroku (Cloudinary)

## 🎯 Mục Tiêu
Thay thế toàn bộ lưu trữ hình ảnh (Cloudinary/Garage S3) bằng **Appwrite Cloud Pro Storage Hub** (`checked-storage`) cho dịch vụ `checked-backend`, tận dụng CDN toàn cầu tự nén WebP, giải phóng bộ nhớ RAM (512MB limit) và giảm tải CPU cho Heroku Eco Dyno.

---

## 📊 Hiện Trạng & Lý Do Chuyển Đổi

| Tiêu chí | Hiện tại (Heroku: Cloudinary / Garage S3) | Sau khi chuyển sang Appwrite Cloud Pro |
| :--- | :--- | :--- |
| **Cơ chế lưu trữ** | Cloudinary (`STORAGE_TYPE=cloudinary`) | Appwrite Storage Bucket `checked-storage` |
| **Dung lượng & Băng thông** | Cloudinary 25 credits/tháng (dễ vượt hạn mức) | **150 GB Storage** • **2,000 GB Băng thông/tháng** (Gói Pro) |
| **Tải trên Dyno Heroku** | Tốn CPU/RAM dyno để xử lý & upload đa file song song | Tối ưu upload, CDN tự động nén WebP lossless giảm 70% size |
| **Giao thức kết nối** | SDK Cloudinary thư viện ngoài | Spring Boot 4 `RestClient` chuẩn Java 21 (0 dependency thừa) |
| **Tính tương thích URL** | DB lưu URL tuyệt đối | Không ảnh hưởng ảnh cũ đã lưu trên DB |

---

## 🗂️ Thông Tin Cấu Hình Appwrite Hub (`/Users/nguyenkimquockhanh/Desktop/APPWRITE`)

* **Endpoint:** `https://fra.cloud.appwrite.io/v1`
* **Project ID:** `backend-storage-hub`
* **Bucket ID:** `checked-storage`
* **API Key:** `Checked Heroku Storage Key` (Scoped: `files.read`, `files.write`)
* **Trạng thái MCP:** Đã bật thành công qua `mcp start appwrite` trên cổng `8087` (`http://localhost:8087/mcp`).

---

## 🛠️ Các Bước Thực Hiện (Action Plan)

### Phase 1: Tạo Configuration & Properties Trong Spring Boot ✅ (HOÀN THÀNH)
1. ✅ Tạo class `AppwriteStorageProperties.java` tại `com.codegym.locketclone.storage.appwrite`:
   - Endpoint: `https://fra.cloud.appwrite.io/v1`
   - Project ID: `backend-storage-hub`
   - Bucket ID: `checked-storage`
   - API Key binding từ biến môi trường.
   - Helper methods: `buildViewUrl`, `buildPreviewUrl`, `buildPhotoUrl` (1080x1080), `buildThumbnailUrl` (320x320), `buildAvatarUrl` (500x500).
2. ✅ Cập nhật `application.yml`, `.env.example`, và `.env` local.
3. ✅ Viết bộ unit test `AppwriteStoragePropertiesTest.java` (Passed 100%).

### Phase 2: Cài Đặt `AppwriteStorageService` (Spring Boot RestClient) ✅ (HOÀN THÀNH)
1. ✅ Tạo `AppwriteFileResponse.java` mapping JSON response từ Appwrite API.
2. ✅ Tạo `AppwriteStorageConfig.java`:
   - Khởi tạo bean `RestClient` tối ưu với Header `X-Appwrite-Project` và `X-Appwrite-Key`.
   - Kết nối có cấu hình timeout (Connect 10s, Read 30s).
   - Điều kiện kích hoạt: `@ConditionalOnProperty(name = "storage.type", havingValue = "appwrite")`.
3. ✅ Tạo `AppwriteStorageService.java` implement `StorageService`:
   - `uploadPhoto(MultipartFile)`: Nén ảnh với `ImageProcessingService` và upload 1 file duy nhất lên Appwrite bucket với quyền `read("any")`.
   - `uploadAvatar(MultipartFile)`: Nén avatar và upload lên Appwrite bucket.
   - `uploadTempPhoto(MultipartFile)`: Upload nhanh raw file phục vụ hàng đợi RabbitMQ worker.
   - `processAndFinalizePhoto(String tempKey)`: Download raw file, nén chuẩn 1080x1080, upload ảnh chuẩn và tự dọn dẹp tempKey.
   - `deleteFile(String key)`: Xóa file khỏi Appwrite bucket an toàn (idempotent, bắt 404 không văng lỗi).
   - Trả về URL CDN WebP tự nén (`buildPhotoUrl`, `buildThumbnailUrl`, `buildAvatarUrl`).
4. ✅ Biên dịch mã nguồn (`./gradlew compileJava`) thành công 100%.

### Phase 3: Viết Unit Test & Kiểm Thử Hồi Quy ✅ (HOÀN THÀNH)
1. ✅ Tạo unit test `AppwriteStorageServiceTest.java` sử dụng `MockRestServiceServer`:
   - `uploadPhoto_success`: kiểm tra nén ảnh, multipart headers, CDN WebP 1080x1080 và 320x320.
   - `uploadAvatar_success`: kiểm tra upload avatar CDN WebP 500x500 và 150x150.
   - `uploadTempPhoto_success`: kiểm tra upload raw stream cho hàng đợi RabbitMQ.
   - `processAndFinalizePhoto_success`: kiểm tra quy trình download raw stream, nén 1080x1080, upload ảnh chuẩn và tự dọn dẹp tempKey.
   - `deleteFile_success`: kiểm tra gửi lệnh HTTP DELETE.
   - `deleteFile_notFound_shouldBeIdempotent`: kiểm tra nuốt lỗi 404 không làm crash server.
   - `deleteFile_nullOrBlank_shouldIgnore`: kiểm tra bỏ qua khi null/blank.
   - `uploadPhoto_whenAppwriteFails_shouldThrowIOException`: kiểm tra bắt lỗi 500 ném `IOException`.
2. ✅ Chạy kiểm thử tự động toàn bộ tầng storage (`com.codegym.locketclone.storage.*`) và domain regression (`PhotoServiceImplTest`, `UserServiceImplTest`) đạt 100% PASS (0 lỗi hồi quy).

### Phase 4: Thiết Lập Biến Môi Trường Trên Heroku & Chuyển Đổi ✅ (HOÀN THÀNH)
1. ✅ Tạo git commit sạch sẽ (`4339457`: `feat(storage): integrate appwrite cloud pro storage hub`).
2. ✅ Thiết lập đồng thời các biến môi trường trên Heroku qua Heroku CLI (`STORAGE_TYPE=appwrite`, `APPWRITE_ENDPOINT`, `APPWRITE_PROJECT_ID`, `APPWRITE_BUCKET_ID`, `APPWRITE_API_KEY`) trên app `heroku-7usd` (Release v40).
3. ✅ Đẩy code lên Heroku (`git push heroku main` -> Release v41).
4. ✅ Dyno khởi động thành công (`web.1: up`, Tomcat lắng nghe port, Swagger UI HTTP 200 OK).

### Phase 5: Xác Thực & Đánh Giá Tải (Verification)
1. Upload thử nghiệm ảnh từ app mobile/API và kiểm tra file xuất hiện trong bucket `checked-storage`.
2. Truy cập trực tiếp URL preview WebP trên trình duyệt kiểm tra tốc độ CDN.
3. Giám sát log Heroku (`heroku logs --tail -a heroku-7usd`) và kiểm tra mức sử dụng RAM/CPU của Dyno.
