package com.codegym.locketclone.common.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "Health", description = "Kiểm tra tình trạng hoạt động của hệ thống và chống cold start cho Heroku Dyno")
@RestController
@RequestMapping("/api/v1/health")
public class HealthCheckController {

    @Operation(summary = "Kiểm tra tình trạng service", description = "Endpoint siêu nhẹ (không truy vấn cơ sở dữ liệu, phản hồi < 2ms) phục vụ Uptime monitoring và giữ ấm Dyno.")
    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "checked-backend",
                "timestamp", System.currentTimeMillis()
        ));
    }

    @Operation(summary = "Ping service bằng HEAD method", description = "Phục vụ cron ping tối ưu băng thông mạng.")
    @RequestMapping(method = RequestMethod.HEAD)
    public ResponseEntity<Void> ping() {
        return ResponseEntity.ok().build();
    }
}
