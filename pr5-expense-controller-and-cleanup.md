# Plan: PR #5 - WebMvc Testing for ExpenseController & Codebase Quality Cleanup

## Goal
Bổ sung bộ test WebMvc toàn diện cho `ExpenseController` (bao gồm toàn bộ 12 endpoints nghiệp vụ tài chính), chuẩn hóa xử lý ngoại lệ Spring MVC trong `GlobalExceptionHandler` để trả về đúng mã trạng thái HTTP chuẩn (404, 405, 400 thay vì 500), dọn dẹp các class dead code (`CloudinaryService`, `UploadedImage`), tối ưu dependencies trong `build.gradle`, và chuẩn hóa thương hiệu email OTP.

---

## Root Cause Analysis & Điểm Cần Cải Thiện

1. **Thiếu WebMvc Test cho `ExpenseController`**:
   - `ExpenseController` sở hữu tới 12 endpoints xử lý nghiệp vụ quản lý tài chính (Danh mục, Ngân sách, Bản ghi giao dịch, Dòng tiền tháng/năm, Top chi tiêu, Mục tiêu tiết kiệm).
   - Hiện tại controller này chưa có bất kỳ test MockMvc nào ở tầng web để kiểm thử request binding, path variables, query parameters, validation và HTTP status code.

2. **Lỗi Xử Lý Ngoại Lệ Mặc Định Biến 404/405 Thành 500**:
   - `GlobalExceptionHandler` hiện có `@ExceptionHandler(value = Exception.class)` bắt mọi `Exception` và trả về `500 INTERNAL_SERVER_ERROR`.
   - Khi client gọi sai URL (ném `NoResourceFoundException`), sai HTTP Method (ném `HttpRequestMethodNotSupportedException`), sai MediaType (ném `HttpMediaTypeNotSupportedException`) hoặc thiếu tham số bắt buộc (ném `MissingServletRequestParameterException`), handler chung này vô tình biến lỗi phía Client (4xx) thành lỗi hệ thống nội bộ (500), vi phạm nghiêm trọng chuẩn RESTful API.

3. **Tồn Tại Dead Code Cũ Sau Khi Chuyển Sang Garage S3**:
   - `CloudinaryService.java` và `UploadedImage.java` là code tàn dư cũ, không còn được bất kỳ service nào gọi tới (toàn bộ đã chuyển sang `GarageS3StorageService` và `UploadedFile`).
   - Cần xóa bỏ để giữ cấu trúc dự án tinh gọn, tránh gây nhầm lẫn cho bảo trì sau này.

4. **Trùng Lặp Dependency & Tên Thương Hiệu Sai Trong Template**:
   - `build.gradle` khai báo trùng 2 dòng `annotationProcessor 'org.projectlombok:lombok'`.
   - `SmtpEmailService` vẫn hardcode tiêu đề và nội dung email OTP là "SnapWidget" thay vì "Checked" / "Locket Clone".

---

## Kế Hoạch Triển Khai Chi Tiết (Step-by-Step Tasks)

### Task 1: Tạo Nhánh Mới `test/expense-controller-and-cleanup`
- Tạo nhánh từ `main` mới nhất (đã chứa đầy đủ PR #1, #2, #3, #4).
- Verify: `git branch --show-current` trả về `test/expense-controller-and-cleanup`.

### Task 2: Chuẩn Hóa Xử Lý Ngoại Lệ Spring MVC Trong `GlobalExceptionHandler.java`
- Bổ sung các `@ExceptionHandler` cụ thể cho các lỗi Spring Web thường gặp:
  - `NoResourceFoundException`: Trả về `404 NOT FOUND` với message rõ ràng ("Tài nguyên không tồn tại").
  - `HttpRequestMethodNotSupportedException`: Trả về `405 METHOD NOT ALLOWED` ("Phương thức HTTP không được hỗ trợ").
  - `HttpMediaTypeNotSupportedException`: Trả về `415 UNSUPPORTED MEDIA TYPE`.
  - `MissingServletRequestParameterException`: Trả về `400 BAD REQUEST` ("Thiếu tham số yêu cầu").
  - `MethodArgumentTypeMismatchException`: Trả về `400 BAD REQUEST` ("Tham số không đúng định dạng").
- Verify: Endpoint không tồn tại trả về 404; gọi sai method trả về 405.

### Task 3: Dọn Dẹp Dead Code, `build.gradle` & Email Service
- Xóa bỏ 2 file thừa:
  - `src/main/java/com/codegym/locketclone/photo/CloudinaryService.java`
  - `src/main/java/com/codegym/locketclone/photo/UploadedImage.java`
- Chỉnh sửa `build.gradle`: Loại bỏ khai báo trùng lặp `annotationProcessor 'org.projectlombok:lombok'`.
- Chỉnh sửa `SmtpEmailService.java`: Sửa chuỗi tiêu đề và nội dung "SnapWidget" thành "Checked".
- Verify: Dự án biên dịch sạch sẽ (`./gradlew compileJava compileTestJava`).

### Task 4: Viết Bộ Test WebMvc Toàn Diện `ExpenseControllerTest.java`
- Tạo file `src/test/java/com/codegym/locketclone/expense/ExpenseControllerTest.java`.
- Cấu hình MockMvc với Mockito cho `ExpenseService`, tích hợp `AuthenticationPrincipalArgumentResolver` và `GlobalExceptionHandler`.
- Viết test cases cho tất cả 12 endpoints:
  1. `GET /api/v1/expense/categories` -> 200 OK & trả về danh sách categories.
  2. `POST /api/v1/expense/categories` -> 201 Created & validate request body.
  3. `PATCH /api/v1/expense/categories/{categoryId}` -> 200 OK.
  4. `GET /api/v1/expense/budgets/{monthKey}` -> 200 OK & nhận đúng monthKey.
  5. `PUT /api/v1/expense/budgets/{monthKey}` -> 200 OK & validate budget limit.
  6. `GET /api/v1/expense/entries` -> 200 OK, phân trang Pageable, lọc theo type (EXPENSE/INCOME/ALL).
  7. `GET /api/v1/expense/entries/by-period` -> 200 OK, kiểm tra params period và referenceDate.
  8. `GET /api/v1/expense/summary` -> 200 OK.
  9. `GET /api/v1/expense/cashflow` -> 200 OK.
  10. `GET /api/v1/expense/summary/yearly` -> 200 OK với param `year`.
  11. `GET /api/v1/expense/categories/top` -> 200 OK với limit & type.
  12. `GET /api/v1/expense/savings-goals/{monthKey}` -> 200 OK.
  13. `PUT /api/v1/expense/savings-goals/{monthKey}` -> 200 OK & validate target amount.
  14. Test các tình huống lỗi chuẩn hóa: URL 404, Method 405, thiếu tham số bắt buộc 400.
- Verify: Chạy test `./gradlew test --tests *ExpenseControllerTest*` đạt 100% PASS.

### Task 5: Chạy Toàn Bộ Test Suite, Cập Nhật Roadmap, Commit & Merge Vào `main`
- Chạy `./gradlew clean test` để kiểm thử toàn diện toàn bộ test suite.
- Cập nhật checklist trong `docs/PULL_REQUESTS_ROADMAP.md` và `pr5-expense-controller-and-cleanup.md`.
- Commit git: `test(quality): add expense controller tests, standardize error handling and clean dead code`.
- Merge Fast-Forward vào nhánh `main` local theo quy ước.

---

## Tiêu Chí Nghiệm Thu (Acceptance Criteria / Done When)
- [x] `ExpenseControllerTest.java` bao phủ đầy đủ 12 endpoints với MockMvc và PASS 100%.
- [x] `GlobalExceptionHandler` trả về HTTP 404 cho URL không tồn tại, HTTP 405 cho sai HTTP method, HTTP 400 cho thiếu/sai kiểu tham số.
- [x] `CloudinaryService.java` và `UploadedImage.java` đã được xóa sạch.
- [x] `build.gradle` không còn khai báo trùng `lombok`.
- [x] `SmtpEmailService` dùng đúng tên thương hiệu "Checked".
- [x] `./gradlew clean test` đạt BUILD SUCCESSFUL 100%.
- [x] Toàn bộ thay đổi của PR #5 được merge trực tiếp vào nhánh `main` local.
