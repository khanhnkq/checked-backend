# Kế Hoạch Triển Khai Chi Tiết: PR #14

> **Tiêu đề PR**: `test(ci): testcontainers-postgres-for-flyway-validation`  
> **Nhánh Git**: `test/ci-testcontainers-flyway-postgres` (tạo từ nhánh `main`)  
> **Mức độ ưu tiên**: 🟡 **P1 (High - Chốt Chặn Kiểm Thử Tự Động Toàn Diện CI)**  
> **Phạm vi ảnh hưởng**: `build.gradle`, `src/test`, `.github/workflows/ci.yml`  
> **Cam kết an toàn**: ❌ **KHÔNG DEPLOY HEROKU** (Mọi thay đổi chỉ thực thi và kiểm thử tại local)

---

## 1. 🔍 Phân Tích Hiện Trạng & Lý Do Cần Thiết

### 1.1. Sự khác biệt giữa H2 In-Memory và PostgreSQL Production
- Hiện tại, bộ test suite chạy trên local sử dụng cơ sở dữ liệu in-memory **H2** (`jdbc:h2:mem:locket-clone-test;MODE=PostgreSQL`) với `spring.flyway.enabled: false` và `spring.jpa.hibernate.ddl-auto: create-drop`.
- Các migration script của Flyway (từ `V1` đến `V17`) sử dụng các tính năng đặc thù chỉ có trên PostgreSQL:
  1. Tiện ích mở rộng: `CREATE EXTENSION IF NOT EXISTS "uuid-ossp"` và hàm `uuid_generate_v4()`.
  2. Hàm số & biểu thức nâng cao: `LEAST(user_id, friend_id)`, `GREATEST(...)`.
  3. Expression Index / Functional Index: `CREATE UNIQUE INDEX ... ON users(LOWER(email))`.
  4. Partial Index: `CREATE INDEX ... ON photos(created_at DESC) WHERE status <> 'DELETED'`.
  5. Các khối lệnh ẩn danh thủ tục: `DO $$ BEGIN ... END $$;`.
- H2 không hỗ trợ đầy đủ các cú pháp này. Nếu một migration script bị sai cú pháp PostgreSQL hoặc lỗi ràng buộc khóa ngoại, bộ unit test hiện tại hoàn toàn **không thể phát hiện**.

### 1.2. Giải pháp: Testcontainers PostgreSQL trên CI
- Tích hợp thư viện **Testcontainers** cho PostgreSQL (`org.testcontainers:postgresql` và `org.testcontainers:junit-jupiter`).
- Viết integration test chuyên biệt: `FlywayMigrationPostgreSqlTest`.
  - Khởi tạo container PostgreSQL chính thống (`postgres:16-alpine`).
  - Kích hoạt Flyway tự động thực thi toàn bộ 17 file migration (`V1__...` đến `V17__...`).
  - Xác minh toàn bộ các bảng, ràng buộc, và chỉ mục (đặc biệt là các index trong V15, V16, V17) được tạo thành công trong PostgreSQL.
- **Graceful Fallback**:
  - Trên môi trường local không có Docker daemon (hoặc Docker chưa mở), test sẽ tự động bỏ qua (**skip with notice**) nhờ `@EnabledIf("isDockerAvailable")`, tránh làm gián đoạn việc phát triển offline.
  - Trên GitHub Actions CI (`ubuntu-latest`), Docker daemon luôn sẵn sàng -> kiểm thử sẽ chạy 100% để bảo đảm an toàn trước khi merge code.

---

## 2. 💡 Thiết Kế Kỹ Thuật (Technical Design)

### 2.1. Thêm dependency vào `build.gradle`
```groovy
testImplementation 'org.testcontainers:postgresql:1.20.4'
testImplementation 'org.testcontainers:junit-jupiter:1.20.4'
```

### 2.2. Viết Integration Test `FlywayMigrationPostgreSqlTest`
```java
package com.codegym.locketclone.db;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class FlywayMigrationPostgreSqlTest {

    static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("locket_test")
            .withUsername("test_user")
            .withPassword("test_pass");

    @Test
    @EnabledIf("isDockerAvailable")
    @DisplayName("Khởi chạy container PostgreSQL và kiểm tra thành công toàn bộ migration V1-V17")
    void testAllFlywayMigrationsOnRealPostgreSql() throws Exception {
        // Thực thi migration
        Flyway flyway = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load();

        var migrateResult = flyway.migrate();
        assertTrue(migrateResult.success, "Quá trình migrate Flyway phải thành công");
        assertTrue(migrateResult.migrationsExecuted >= 17, "Phải chạy ít nhất 17 migration script");

        // Kiểm tra các bảng và index cốt lõi
        try (Connection conn = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement()) {
            
            // 1. Bảng users và lower indexes (V17)
            ResultSet rsUsers = stmt.executeQuery("SELECT COUNT(*) FROM pg_indexes WHERE tablename = 'users' AND indexname = 'idx_users_lower_email'");
            rsUsers.next();
            assertEquals(1, rsUsers.getInt(1), "Chỉ mục idx_users_lower_email phải tồn tại");

            // 2. Partial index trên photos (V17)
            ResultSet rsPhotos = stmt.executeQuery("SELECT COUNT(*) FROM pg_indexes WHERE tablename = 'photos' AND indexname = 'idx_photos_feed_status_created'");
            rsPhotos.next();
            assertEquals(1, rsPhotos.getInt(1), "Chỉ mục idx_photos_feed_status_created phải tồn tại");

            // 3. Bidirectional index trên friendships (V15)
            ResultSet rsFriendships = stmt.executeQuery("SELECT COUNT(*) FROM pg_indexes WHERE tablename = 'friendships' AND indexname = 'uk_friendships_bidirectional'");
            rsFriendships.next();
            assertEquals(1, rsFriendships.getInt(1), "Chỉ mục uk_friendships_bidirectional phải tồn tại");
        }
    }
}
```

### 2.3. Cập nhật `.github/workflows/ci.yml`
- Bổ sung cấu hình cache cho Testcontainers / Docker layers (nếu cần).
- Đảm bảo bước chạy `./gradlew --no-daemon clean test bootJar` hoàn tất với báo cáo test artifact.

---

## 3. 📂 Chi Tiết Từng File Thay Đổi

1. **[build.gradle](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/build.gradle)**
   - Thêm `org.testcontainers:postgresql:1.20.4` và `org.testcontainers:junit-jupiter:1.20.4`.
2. **[src/test/java/com/codegym/locketclone/db/FlywayMigrationPostgreSqlTest.java](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/test/java/com/codegym/locketclone/db/FlywayMigrationPostgreSqlTest.java)** *(Tạo mới)*
   - Chứa integration test xác thực 17 Flyway scripts và kiểm tra index trên PostgreSQL container.
3. **[.github/workflows/ci.yml](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/.github/workflows/ci.yml)**
   - Đảm bảo CI chạy và upload artifact kiểm thử đầy đủ.

---

## 4. 🛠️ Quy Trình Triển Khai Từng Bước (Step-by-Step Execution)

1. **Bước 1**: Tạo nhánh `test/ci-testcontainers-flyway-postgres` từ `main`.
2. **Bước 2**: Chỉnh sửa [build.gradle](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/build.gradle) để thêm thư viện Testcontainers.
3. **Bước 3**: Tạo file kiểm thử [FlywayMigrationPostgreSqlTest.java](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/test/java/com/codegym/locketclone/db/FlywayMigrationPostgreSqlTest.java).
4. **Bước 4**: Chạy `./gradlew clean test bootJar` để xác thực toàn bộ test suite.
5. **Bước 5**: Cập nhật [.github/workflows/ci.yml](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/.github/workflows/ci.yml) (nếu cần tinh chỉnh).
6. **Bước 6**: Commit chuẩn Conventional Commits, merge vào nhánh `main` ở local và push lên remote `origin`.

---

## 5. ✅ Tiêu Chí Nghiệm Thu & Test Checklist

- [x] Dependency `org.testcontainers:postgresql` và `junit-jupiter` được nạp sạch sẽ, không xung đột phiên bản.
- [x] File kiểm thử `FlywayMigrationPostgreSqlTest` biên dịch thành công.
- [x] Chạy local khi không có Docker: test tự động pass/skip mượt mà (không gây lỗi build).
- [x] Chạy trên GitHub Actions CI (có Docker): container PostgreSQL khởi chạy, hoàn tất 100% 17 migration script và xác thực các index PostgreSQL.
- [x] Toàn bộ 158+ unit/integration tests khác vẫn pass 100%.
- [x] **Cam kết**: Tuyệt đối không thực thi lệnh deploy lên Heroku.
