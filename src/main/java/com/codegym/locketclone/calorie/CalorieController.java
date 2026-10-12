package com.codegym.locketclone.calorie;

import com.codegym.locketclone.calorie.dto.*;
import com.codegym.locketclone.security.service.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@Tag(name = "Calorie Tracking", description = "Quản lý mục tiêu Calo hàng ngày, nhật ký bữa ăn và báo cáo xu hướng năng lượng")
@RestController
@RequestMapping("/api/v1/calories")
@RequiredArgsConstructor
public class CalorieController {

    private final CalorieService calorieService;

    @Operation(summary = "Lấy mục tiêu Calo hàng ngày", description = "Lấy mức Calo mục tiêu của người dùng hiện tại (mặc định 2000 kcal).")
    @GetMapping("/goal")
    public ResponseEntity<CalorieGoalResponseDTO> getGoal(@AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(calorieService.getGoal(currentUser.getId()));
    }

    @Operation(summary = "Cập nhật mục tiêu Calo hàng ngày", description = "Thiết lập hoặc cập nhật mức Calo mục tiêu mỗi ngày.")
    @PutMapping("/goal")
    public ResponseEntity<CalorieGoalResponseDTO> updateGoal(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody CalorieGoalRequestDTO request
    ) {
        return ResponseEntity.ok(calorieService.updateGoal(currentUser.getId(), request));
    }

    @Operation(summary = "Xem tổng quan Calo theo ngày", description = "Lấy tổng Calo đã nạp, mức còn lại và danh sách món ăn trong ngày (mặc định ngày hôm nay).")
    @GetMapping("/daily")
    public ResponseEntity<DailyCalorieSummaryDTO> getDailySummary(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ResponseEntity.ok(calorieService.getDailySummary(currentUser.getId(), date));
    }

    @Operation(summary = "Xem báo cáo xu hướng Calo theo tháng", description = "Lấy biểu đồ Calo từng ngày trong tháng (monthKey định dạng yyyyMM, ví dụ 202610).")
    @GetMapping("/summary")
    public ResponseEntity<MonthlyCalorieSummaryDTO> getMonthlySummary(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam(required = false) String month
    ) {
        return ResponseEntity.ok(calorieService.getMonthlySummary(currentUser.getId(), month));
    }

    @Operation(summary = "Ghi nhận Calo nhanh thủ công", description = "Thêm nhanh một bản ghi món ăn/calo khi không chụp ảnh qua camera.")
    @PostMapping("/logs")
    public ResponseEntity<CalorieLogItemDTO> createManualLog(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody CreateCalorieLogRequestDTO request
    ) {
        CalorieLogItemDTO response = calorieService.createManualLog(currentUser.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Xóa bản ghi Calo", description = "Xóa một bản ghi bữa ăn đã log.")
    @DeleteMapping("/logs/{id}")
    public ResponseEntity<Void> deleteLog(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable UUID id
    ) {
        calorieService.deleteLog(currentUser.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
