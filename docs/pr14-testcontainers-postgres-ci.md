# PR #14: Tích Hợp Testcontainers PostgreSQL & Kiểm Thử Flyway Migration Trên CI

## 🎯 Mục Tiêu
Tích hợp Testcontainers PostgreSQL vào test suite và GitHub Actions CI pipeline để tự động khởi chạy database PostgreSQL thật, kiểm thử toàn bộ 17 script Flyway migration (`V1` – `V17`), đảm bảo không có lỗi cú pháp SQL, dialect hay schema mismatch trước khi code được đẩy lên Production.

---

## 📋 Danh Sách Nhiệm Vụ (Tasks)

- [ ] **Task 1: Tạo nhánh Git cho PR #14**
  - Hành động: `git checkout -b test/ci-testcontainers-flyway-postgres` từ nhánh `main`.
  - Kiểm tra: `git branch --show-current` trả về `test/ci-testcontainers-flyway-postgres`.

- [ ] **Task 2: Bổ sung dependencies Testcontainers vào `build.gradle`**
  - Hành động: Thêm `spring-boot-testcontainers`, `org.testcontainers:postgresql:1.20.4`, `org.testcontainers:junit-jupiter:1.20.4`.
  - Kiểm tra: `./gradlew dependencies` tải thư viện thành công không có xung đột phiên bản.

- [ ] **Task 3: Cấu hình hồ sơ kiểm thử PostgreSQL Flyway**
  - Hành động: Tạo profile/cấu hình test chuyên dụng kích hoạt Flyway migration thật (`spring.flyway.enabled: true`, `spring.jpa.hibernate.ddl-auto: validate`).
  - Kiểm tra: Spring Context nhận diện và kết nối động với PostgreSQL container thông qua `@ServiceConnection`.

- [ ] **Task 4: Xây dựng Integration Test `FlywayPostgreSqlIntegrationTest`**
  - Hành động:
    1. Sử dụng `@Testcontainers(disabledWithoutDocker = true)` và `@Container static PostgreSQLContainer<?> postgres`.
    2. Viết các test case kiểm tra:
       - Flyway áp dụng thành công toàn bộ 16 script migration (`V1` -> `V16`).
       - Hibernate validate khớp 100% giữa Entity JPA và Table schema PostgreSQL.
       - Dữ liệu seed mặc định trong `categories` (cả `EXPENSE` và `INCOME`) được nạp chính xác.
  - Kiểm tra: Chạy test độc lập vượt qua với kết quả xanh `PASSED`.

- [ ] **Task 5: Đồng bộ & Tối ưu GitHub Actions Workflow `.github/workflows/ci.yml`**
  - Hành động: Đảm bảo bước `./gradlew --no-daemon clean test bootJar` trên `ubuntu-latest` tự động kết nối Docker daemon để chạy container kiểm thử.
  - Kiểm tra: Workflow file hợp lệ cú pháp GitHub Actions.

- [ ] **Task 6: Kiểm thử toàn diện & Đóng gói Jar**
  - Hành động: Chạy `./gradlew clean test bootJar` cục bộ với Docker OrbStack.
  - Kiểm tra: `BUILD SUCCESSFUL`, toàn bộ test suites (H2 unit tests + PostgreSQL integration test) pass 100%.

- [ ] **Task 7: Cập nhật Roadmap & Merge vào `main`**
  - Hành động: Cập nhật `docs/PULL_REQUESTS_ROADMAP.md` ghi nhận PR #11 hoàn thành, merge và push lên GitHub.

---

## ✅ Tiêu Chí Nghiệm Thu (Done When)
- [ ] Container PostgreSQL 15 alpine khởi chạy động trong integration test.
- [ ] Tất cả 16 file Flyway migration (`V1` – `V16`) chạy không gặp bất kỳ lỗi cú pháp PostgreSQL nào.
- [ ] Các unit tests H2 hiện tại vẫn chạy độc lập và giữ tốc độ nhanh.
- [ ] `./gradlew clean test bootJar` chạy thành công 100% cả trên local và CI.
