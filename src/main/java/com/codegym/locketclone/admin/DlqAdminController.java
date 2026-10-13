package com.codegym.locketclone.admin;

import com.codegym.locketclone.admin.dto.DlqReplayRequest;
import com.codegym.locketclone.admin.dto.DlqReplayResponse;
import com.codegym.locketclone.admin.dto.DlqStatsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/dlq")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin DLQ", description = "Quản trị Dead Letter Queue và Replay thông điệp lỗi")
public class DlqAdminController {

    private final DlqAdminService dlqAdminService;

    @Operation(summary = "Lấy thống kê hàng đợi Dead Letter Queue",
            description = "Trả về số lượng tin nhắn đọng, số consumer kết nối và trạng thái DLQ.")
    @GetMapping("/stats")
    public ResponseEntity<DlqStatsResponse> getDlqStats() {
        DlqStatsResponse stats = dlqAdminService.getDlqStats();
        return ResponseEntity.ok(stats);
    }

    @Operation(summary = "Replay tin nhắn lỗi từ Dead Letter Queue",
            description = "Lấy tin nhắn từ DLQ, giải mã header x-death hoặc TypeId và tái gửi về Exchange xử lý.")
    @PostMapping("/replay")
    public ResponseEntity<DlqReplayResponse> replayMessages(
            @RequestBody(required = false) @Valid DlqReplayRequest request,
            @RequestParam(value = "limit", required = false) Integer queryLimit,
            @RequestParam(value = "targetExchange", required = false) String queryExchange
    ) {
        Integer limit = (request != null && request.limit() != null) ? request.limit() : queryLimit;
        String exchange = (request != null && request.targetExchange() != null) ? request.targetExchange() : queryExchange;

        log.info("[DLQ-ADMIN] Nhận yêu cầu Replay DLQ với limit={}, exchange={}", limit, exchange);
        DlqReplayResponse response = dlqAdminService.replayMessages(limit, exchange);
        return ResponseEntity.ok(response);
    }
}
