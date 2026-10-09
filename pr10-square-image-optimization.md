# PR #10: Tối Ưu Hóa Ảnh Vuông 1:1 Khớp Frontend & Tăng Tốc Pipeline Lưu Trữ

## 🎯 Mục Tiêu
Chuẩn hóa ảnh chính và thumbnail về tỷ lệ 1:1 vuông (Center Crop $1080\times 1080$ và $320\times 320$) để khớp hoàn hảo với Widget/Card UI của Frontend Locket, khắc phục góc xoay EXIF, loại bỏ giải mã trùng lặp trong RAM, thêm S3 Cache-Control vĩnh viễn và upload song song.

---

## 📋 Danh Sách Nhiệm Vụ (Tasks)

- [x] **Task 1: Tạo nhánh Git cho PR #10**
  - Hành động: `git checkout -b perf/square-image-and-upload-opt` từ nhánh `main`.
  - Kiểm tra: `git branch --show-current` trả về `perf/square-image-and-upload-opt`.

- [x] **Task 2: Cấu hình `storageTaskExecutor` trong `AsyncConfig.java`**
  - Hành động: Khai báo bean `storageTaskExecutor` (`corePoolSize=4`, `maxPoolSize=16`, prefix `storage-exec-`) chuyên dụng cho các tác vụ I/O upload ảnh song song.
  - Kiểm tra: Spring context biên dịch và nạp bean thành công.

- [x] **Task 3: Tối ưu `ImageProcessingService.java` (Ảnh vuông 1:1, EXIF & Zero-Copy RAM)**
  - Hành động:
    1. Đổi `PHOTO_SIZE = 1080`.
    2. Trong `processPhoto`: Áp dụng `.useExifOrientation(true)`, `.crop(Positions.CENTER)`, `.size(1080, 1080)`.
    3. Đọc ra `BufferedImage squareImage = ...asBufferedImage()`.
    4. Sinh `mainBytes` ($1080\times 1080$, quality `0.85f`) và `thumbBytes` ($320\times 320$, quality `0.80f`) trực tiếp từ `squareImage` trong RAM mà không gọi `ImageIO.read(mainBytes)`.
    5. Áp dụng tương tự cho `processAvatar` (500x500 & 150x150).
  - Kiểm tra: Kích thước trả về luôn đảm bảo $width = height = 1080$ (cho photo) hoặc $500$ (cho avatar).

- [x] **Task 4: Thêm S3 Cache-Control & Upload song song trong `GarageS3StorageService.java`**
  - Hành động:
    1. Bổ sung `.cacheControl("public, max-age=31536000, immutable")` trong `putObject`.
    2. Chuyển đổi lệnh upload tuần tự sang `CompletableFuture.runAsync(..., storageTaskExecutor)` cho ảnh chính và thumbnail, đồng bộ bằng `CompletableFuture.allOf(...).join()`.
  - Kiểm tra: Metadata của S3 object chứa header `Cache-Control`, thời gian upload giảm ~40-50%.

- [x] **Task 5: Upload song song ảnh và thumbnail trong `CloudinaryStorageService.java`**
  - Hành động:
    1. Tạo UUID trước cho cả ảnh chính và thumbnail (`publicId` và `publicId + "_thumb"`).
    2. Tải lên đồng thời bằng `CompletableFuture.supplyAsync(..., storageTaskExecutor)`.
  - Kiểm tra: Cả 2 ảnh được đẩy lên Cloudinary song song, trả về đúng `secureUrl` và `thumbnailUrl`.

- [x] **Task 6: Cập nhật & Bổ sung Unit Tests**
  - Hành động:
    1. Cập nhật `ImageProcessingServiceTest.java`: Kiểm tra ảnh đầu ra đúng tỷ lệ 1:1 ($1080\times 1080$ và $500\times 500$), kiểm tra thumbnail, kiểm tra ảnh không bị méo.
    2. Cập nhật `GarageS3StorageServiceTest.java`: Verify `Cache-Control` header trong `PutObjectRequest` và kiểm tra cả 2 tác vụ putObject được gọi.
    3. Cập nhật `CloudinaryStorageServiceTest.java`: Verify cả 2 tác vụ upload được gọi.
  - Kiểm tra: `./gradlew clean test` chạy toàn bộ test suite pass 100%.

- [x] **Task 7: Kiểm tra toàn diện & Build Production Artifact**
  - Hành động: Chạy `./gradlew test bootJar`.
  - Kiểm tra: `BUILD SUCCESSFUL`, artifact jar tạo thành công trong `build/libs/`.

---

## ✅ Tiêu Chí Nghiệm Thu (Done When)
- [x] Ảnh tải lên (bất kể tỷ lệ gốc 4:3, 16:9, v.v.) đều được Center-crop thành hình vuông 1:1 ($1080\times 1080$ cho photo feed, $320\times 320$ cho thumbnail).
- [x] Ảnh chụp dọc từ smartphone giữ nguyên chiều thẳng đứng, không bị xoay ngang (EXIF handled).
- [x] Không còn lệnh `ImageIO.read(new ByteArrayInputStream(mainBytes))` trong toàn bộ codebase.
- [x] File trên Garage S3 có header `Cache-Control: public, max-age=31536000, immutable`.
- [x] 100% tests (158+ tests) chạy `BUILD SUCCESSFUL`.
