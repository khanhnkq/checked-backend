package com.codegym.locketclone.db;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class NeonFlywayMigrationTest {

    @DynamicPropertySource
    static void configureNeonDb(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://ep-summer-darkness-b3unuia8-pooler.c-4.ap-southeast-1.aws.neon.tech:5432/neondb?sslmode=require");
        registry.add("spring.datasource.username", () -> "neondb_owner");
        registry.add("spring.datasource.password", () -> "npg_UHF6EBJK3MCG");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void testFlywayMigrationsOnNeonDb() {
        assertNotNull(jdbcTemplate);

        // Verify Flyway history table exists and has 16 migrations
        List<Map<String, Object>> migrations = jdbcTemplate.queryForList(
                "SELECT version, description, type, success FROM flyway_schema_history ORDER BY installed_rank"
        );

        assertFalse(migrations.isEmpty());
        System.out.println("Total Flyway migrations installed on Neon DB: " + migrations.size());
        for (Map<String, Object> m : migrations) {
            System.out.println("Migration V" + m.get("version") + ": " + m.get("description") + " -> success=" + m.get("success"));
            assertEquals(true, m.get("success"), "Migration V" + m.get("version") + " must succeed");
        }
        assertEquals(16, migrations.size(), "All 16 Flyway migrations must be installed");

        // Verify key tables exist
        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE'",
                Integer.class
        );
        assertNotNull(tableCount);
        assertTrue(tableCount >= 10, "Should have created all tables on Neon DB");
        System.out.println("Total public tables created on Neon DB: " + tableCount);
    }
}
