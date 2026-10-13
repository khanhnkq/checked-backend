# Kế Hoạch Triển Khai PR #3: Phân Vùng 2 - Cache Reactions Summary, Feed & Kho Ảnh MyPhotos

> **Dự án:** Checked Backend (`locket-clone-api`)  
> **Nhánh Git dự kiến:** `feat/cache-partition-photo-and-reactions`  
> **Mức độ ưu tiên:** 🔴 **P0 (Critical - Điểm nóng hiệu năng lớn nhất hệ thống)**  
> **Tài liệu tham chiếu:** [REDIS_RABBITMQ_PR_CHECKLIST.md](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/docs/REDIS_RABBITMQ_PR_CHECKLIST.md)

---

## 1. Mục Tiêu & Thách Thức Kỹ Thuật

PR #3 giải quyết điểm nóng truy vấn lớn nhất của ứng dụng chia sẻ ảnh phong cách Locket:
1. **Reactions Summary (`reactions_summary`)**: Giảm độ trễ từ ~1.600ms (lệnh SQL group by, count, join người thả tim) xuống **< 2ms** qua Redis.
2. **Chi Tiết Ảnh (`photo_detail`)**: Cache chi tiết metadata bức ảnh (caption, location, calo, chi tiêu) với khóa bảo mật theo quyền người xem.
3. **Trang Đầu Feed & MyPhotos (`feed` & `myphotos`)**: Cache trang đầu (`pageNumber == 0`) của dòng thời gian bạn bè và kho ảnh cá nhân (chiếm 95% lưu lượng đọc ảnh).

### ⚠️ Phát Hiện & Thách Thức Jackson Serialization (Đã Kiểm Chứng Thực Nghiệm)
- **Vấn đề**: `PageImpl` và `SliceImpl` mặc định của Spring Data **không có default constructor** hoặc `@JsonCreator`, đồng thời phương thức `Unpaged.getOffset()` ném `UnsupportedOperationException`. Do đó, nếu serialize trực tiếp vào Redis, Jackson sẽ ném ngoại lệ `InvalidDefinitionException: Cannot construct instance of SliceImpl` khi đọc ra.
- **Giải pháp**: Xây dựng 2 lớp wrapper `RestSlice<T> implements Slice<T>` và `RestPage<T> implements Page<T>` có gắn `@JsonCreator` và `@JsonIgnoreProperties(ignoreUnknown = true)`. Lớp wrapper này vừa tương thích 100% với Spring MVC (iOS client không đổi bất kỳ định dạng JSON nào), vừa serialize/deserialize hai chiều mượt mà trong Redis.

---

## 2. Chi Tiết Các Hạng Mục Triển Khai

### 2.1. Lớp Wrapper Cho Slice & Page (`RestSlice.java` & `RestPage.java`)
- Thư mục: `src/main/java/com/codegym/locketclone/common/dto/`
- Triển khai đầy đủ các phương thức của `Slice<T>` và `Page<T>`:
  - `RestSlice`: chứa `content`, `number`, `size`, `hasNext`.
  - `RestPage`: chứa `content`, `number`, `size`, `totalElements`.
  - Gắn `@JsonCreator` và `@JsonProperty` cho constructor để Jackson deserialize an toàn từ Redis.

### 2.2. Tái Cấu Trúc Cache Reactions Summary
- **DTO Mới**: `PhotoReactionStats.java` (chứa `photoId`, `totalCount`, `countsByType`, `reactors`).
- **Method Cache Mới**:
  ```java
  @Cacheable(value = CacheConfig.REACTIONS_SUMMARY_CACHE, key = "#photoId")
  PhotoReactionStats getReactionStats(UUID photoId);
  ```
- **Method Public**: Trong `getReactionSummary(UUID userId, UUID photoId)`:
  - Gọi `getReactionStats(photoId)` qua proxy Spring (`@Lazy private PhotoService self;`).
  - Truy vấn riêng `findByPhotoIdAndUserId(photoId, userId)` để lấy `myReaction` của người gọi.
  - Ghép thành `PhotoReactionSummaryResponse`.
- **Eviction**:
  - `upsertReaction(...)`: `@CacheEvict(value = CacheConfig.REACTIONS_SUMMARY_CACHE, key = "#photoId")`
  - `removeReaction(...)`: `@CacheEvict(value = CacheConfig.REACTIONS_SUMMARY_CACHE, key = "#photoId")`

### 2.3. Cache Chi Tiết Ảnh (`photo_detail`)
- Gắn `@Cacheable(value = CacheConfig.PHOTO_DETAIL_CACHE, key = "#userId + ':' + #photoId")` trên `getPhotoDetail(UUID userId, UUID photoId)`.
- TTL: **30 phút**.
- **Eviction**:
  - `updatePhotoExpense`: `@CacheEvict(value = CacheConfig.PHOTO_DETAIL_CACHE, key = "#userId + ':' + #photoId")`
  - `updatePhotoTransaction`: `@CacheEvict(value = CacheConfig.PHOTO_DETAIL_CACHE, key = "#userId + ':' + #photoId")`
  - `deleteTransaction`: `@CacheEvict(value = CacheConfig.PHOTO_DETAIL_CACHE, key = "#userId + ':' + #photoId")`

### 2.4. Cache Trang Đầu Feed & Kho Ảnh MyPhotos
- **Feed**:
  ```java
  @Cacheable(
      value = CacheConfig.FEED_CACHE,
      key = "#userId",
      condition = "#pageable.pageNumber == 0 && #friendId == null"
  )
  Slice<PhotoResponse> getFeedPhotos(UUID userId, UUID friendId, Pageable pageable);
  ```
  - Chuyển đổi kết quả sang `RestSlice<PhotoResponse>` trước khi trả về.
  - TTL: **10 phút**.
- **MyPhotos**:
  ```java
  @Cacheable(
      value = CacheConfig.MY_PHOTOS_CACHE,
      key = "#userId",
      condition = "#pageable.pageNumber == 0"
  )
  Page<PhotoResponse> getMyPhotos(UUID userId, Pageable pageable);
  ```
  - Chuyển đổi kết quả sang `RestPage<PhotoResponse>` trước khi trả về.
  - TTL: **15 phút**.
- **Eviction**:
  - Khi đăng ảnh mới `uploadPhoto(...)`: Xóa cache `myphotos::{senderId}`.
  - Khi xóa ảnh `deleteTransaction(...)`: Xóa cache `myphotos::{userId}` và `feed::{userId}`.

---

## 3. Kế Hoạch Kiểm Thử (Integration Tests)

Tạo file kiểm thử tích hợp: `src/test/java/com/codegym/locketclone/infra/PhotoAndReactionCacheTest.java`:
1. `testReactionStatsCaching`: Gọi `getReactionSummary` lần 1 nạp cache Redis, lần 2 hit cache.
2. `testReactionEvictionOnUpsertAndRemove`: Thả reaction và xóa reaction kiểm tra xóa sạch cache `reactions_summary`.
3. `testPhotoDetailCachingAndEviction`: Xem chi tiết ảnh hit cache, chỉnh sửa/xóa ảnh tự động xóa cache `photo_detail`.
4. `testFeedAndMyPhotosPage0Caching`: Kiểm tra feed trang 0 và my-photos trang 0 serialize/deserialize thành công qua Redis không lỗi, trang >= 1 không bị nạp vào cache.
5. `testUploadPhotoEvictsMyPhotos`: Đăng ảnh mới kiểm tra cache `myphotos` bị evict lập tức.

Chạy toàn bộ test suite dự án `./gradlew test` (mục tiêu 100% PASS).

---

## 4. Kế Hoạch Triển Khai & Kiểm Thử Live Heroku

1. Tạo nhánh git `feat/cache-partition-photo-and-reactions`.
2. Commit: `feat(cache): cache photo reactions summary, feed, and myphotos with smart eviction`.
3. Push nhánh lên GitHub.
4. Merge vào `main`, push lên Heroku `heroku-7usd`.
5. Kiểm thử live trên Heroku:
   - `GET /v3/api-docs` (200 OK)
   - `GET /api/v1/photos/feed` (401 Auth check)
   - `GET /api/v1/photos/my-photos` (401 Auth check)
6. Cập nhật bảng checklist tiến độ trong `docs/REDIS_RABBITMQ_PR_CHECKLIST.md`.
