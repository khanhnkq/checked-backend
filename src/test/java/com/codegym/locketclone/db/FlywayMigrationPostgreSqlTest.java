package com.codegym.locketclone.db;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
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

@Testcontainers(disabledWithoutDocker = true)
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
        assertTrue(postgres.isRunning(), "PostgreSQL container phải đang hoạt động");

        // 1. Thực thi Flyway migration từ V1 đến V17
        Flyway flyway = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load();

        MigrateResult migrateResult = flyway.migrate();
        assertTrue(migrateResult.success, "Quá trình migrate Flyway phải thành công");
        assertTrue(migrateResult.migrationsExecuted >= 17, "Phải chạy tối thiểu 17 migration scripts");

        // 2. Kết nối JDBC trực tiếp để xác minh các ràng buộc và chỉ mục cốt lõi
        try (Connection conn = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement()) {

            // Kiểm tra bảng users và functional lower indexes (V17)
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM pg_indexes WHERE tablename = 'users' AND indexname = 'idx_users_lower_email'")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "Chỉ mục idx_users_lower_email phải tồn tại trên PostgreSQL");
            }

            try (ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM pg_indexes WHERE tablename = 'users' AND indexname = 'idx_users_lower_username'")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "Chỉ mục idx_users_lower_username phải tồn tại trên PostgreSQL");
            }

            // Kiểm tra partial index trên photos (V17)
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM pg_indexes WHERE tablename = 'photos' AND indexname = 'idx_photos_feed_status_created'")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "Chỉ mục idx_photos_feed_status_created phải tồn tại trên PostgreSQL");
            }

            // Kiểm tra composite B-Tree index cho giao dịch (V13)
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM pg_indexes WHERE tablename = 'photos' AND indexname = 'idx_photos_sender_type_occurred'")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "Chỉ mục idx_photos_sender_type_occurred phải tồn tại trên PostgreSQL");
            }

            // Kiểm tra bidirectional friendship unique index (V15)
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM pg_indexes WHERE tablename = 'friendships' AND indexname = 'uk_friendships_bidirectional'")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "Chỉ mục uk_friendships_bidirectional phải tồn tại trên PostgreSQL");
            }

            // Kiểm tra seed categories mặc định cho INCOME (V16)
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM categories WHERE is_default = TRUE AND transaction_type = 'INCOME'")) {
                assertTrue(rs.next());
                assertTrue(rs.getInt(1) >= 3, "Phải có ít nhất 3 danh mục INCOME mặc định đã được seed");
            }
        }
    }
}
