package com.codegym.locketclone.expense;

import com.codegym.locketclone.common.exception.AppException;
import com.codegym.locketclone.common.exception.ErrorCode;
import com.codegym.locketclone.expense.dto.*;
import com.codegym.locketclone.security.service.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Tag(name = "Expenses & Finance", description = "Quản lý tài chính cá nhân: Danh mục thu/chi, Ngân sách (Budget), Mục tiêu tiết kiệm (Savings Goal) và Báo cáo dòng tiền")
@RestController
@RequestMapping("/api/v1/expense")
@RequiredArgsConstructor
public class ExpenseController {
    private final ExpenseService expenseService;

    @Operation(summary = "Lấy danh sách danh mục chi tiêu/thu nhập", description = "Lấy danh sách các danh mục hệ thống và danh mục tùy chỉnh của người dùng hiện tại.")
    @GetMapping("/categories")
    public ResponseEntity<List<CategoryResponse>> getCategories(@AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(expenseService.getCategories(currentUser.getId()));
    }

    @Operation(summary = "Tạo danh mục mới", description = "Tạo danh mục chi tiêu hoặc thu nhập tùy chỉnh.")
    @PostMapping("/categories")
    public ResponseEntity<CategoryResponse> createCategory(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody CreateCategoryRequest request
    ) {
        CategoryResponse response = expenseService.createCategory(currentUser.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Cập nhật danh mục", description = "Chỉnh sửa tên hoặc icon của danh mục.")
    @PatchMapping("/categories/{categoryId}")
    public ResponseEntity<CategoryResponse> updateCategory(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable UUID categoryId,
            @Valid @RequestBody UpdateCategoryRequest request
    ) {
        return ResponseEntity.ok(expenseService.updateCategory(currentUser.getId(), categoryId, request));
    }

    @Operation(summary = "Xem ngân sách theo tháng", description = "Lấy ngân sách đã thiết lập cho tháng cụ thể (dạng YYYYMM, ví dụ 202610).")
    @GetMapping("/budgets/{monthKey}")
    public ResponseEntity<BudgetResponse> getBudget(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable String monthKey
    ) {
        return ResponseEntity.ok(expenseService.getBudget(currentUser.getId(), monthKey));
    }

    @Operation(summary = "Thiết lập ngân sách tháng", description = "Tạo mới hoặc cập nhật hạn mức ngân sách tháng.")
    @PutMapping("/budgets/{monthKey}")
    public ResponseEntity<BudgetResponse> upsertBudget(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable String monthKey,
            @Valid @RequestBody BudgetUpsertRequest request
    ) {
        return ResponseEntity.ok(expenseService.upsertBudget(currentUser.getId(), monthKey, request));
    }

    @Operation(summary = "Lấy danh sách bản ghi giao dịch theo tháng", description = "Lấy danh sách các khoản thu/chi trong tháng với phân trang.")
    @GetMapping("/entries")
    public ResponseEntity<Page<ExpenseItemResponse>> getExpenseEntries(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam String monthKey,
            @RequestParam(required = false) String type,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        if (type == null || type.isBlank()) {
            return ResponseEntity.ok(expenseService.getExpenseEntries(currentUser.getId(), monthKey, pageable));
        }

        TransactionFilterType filterType;
        try {
            filterType = TransactionFilterType.valueOf(type.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new AppException(ErrorCode.INVALID_TRANSACTION_TYPE);
        }
        if (filterType == TransactionFilterType.ALL) {
            YearMonthRange monthRange = YearMonthRange.fromMonthKey(monthKey);
            return ResponseEntity.ok(expenseService.getExpenseEntriesByPeriod(
                    currentUser.getId(),
                    EntryPeriod.MONTH,
                    monthRange.referenceDate(),
                    TransactionFilterType.ALL,
                    pageable
            ));
        }

        return ResponseEntity.ok(expenseService.getExpenseEntries(
                currentUser.getId(),
                monthKey,
                TransactionType.valueOf(filterType.name()),
                pageable
        ));
    }

    @Operation(summary = "Lấy giao dịch theo chu kỳ linh hoạt", description = "Lọc giao dịch theo DAY, WEEK, MONTH, YEAR hoặc CUSTOM.")
    @GetMapping("/entries/by-period")
    public ResponseEntity<Page<ExpenseItemResponse>> getExpenseEntriesByPeriod(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam EntryPeriod period,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate referenceDate,
            @RequestParam(defaultValue = "EXPENSE") TransactionFilterType type,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(expenseService.getExpenseEntriesByPeriod(currentUser.getId(), period, referenceDate, type, pageable));
    }

    @Operation(summary = "Báo cáo tổng kết chi tiêu tháng", description = "Tổng hợp tổng chi tiêu, ngân sách còn lại, tỷ lệ sử dụng.")
    @GetMapping("/summary")
    public ResponseEntity<ExpenseSummaryResponse> getExpenseSummary(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam String monthKey
    ) {
        return ResponseEntity.ok(expenseService.getExpenseSummary(currentUser.getId(), monthKey));
    }

    @Operation(summary = "Báo cáo dòng tiền tháng (Cashflow)", description = "Tổng thu, tổng chi và dòng tiền thuần theo tháng.")
    @GetMapping("/cashflow")
    public ResponseEntity<CashflowSummaryResponse> getCashflowSummary(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam String monthKey
    ) {
        return ResponseEntity.ok(expenseService.getCashflowSummary(currentUser.getId(), monthKey));
    }

    @Operation(summary = "Báo cáo dòng tiền cả năm", description = "Tổng hợp dòng tiền 12 tháng trong năm (đã tối ưu 1 query gom nhóm).")
    @GetMapping("/summary/yearly")
    public ResponseEntity<YearlyCashflowSummaryResponse> getYearlyCashflowSummary(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam Integer year
    ) {
        return ResponseEntity.ok(expenseService.getYearlyCashflowSummary(currentUser.getId(), year));
    }

    @Operation(summary = "Top danh mục chi tiêu nhiều nhất", description = "Lấy danh sách top N danh mục có tổng tiền lớn nhất.")
    @GetMapping("/categories/top")
    public ResponseEntity<List<TopCategoryResponse>> getTopCategories(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam(required = false) String monthKey,
            @RequestParam(required = false) Integer year,
            @RequestParam(defaultValue = "EXPENSE") TransactionFilterType type,
            @RequestParam(defaultValue = "5") Integer limit
    ) {
        return ResponseEntity.ok(expenseService.getTopCategories(currentUser.getId(), monthKey, year, type, limit));
    }

    @Operation(summary = "Xem mục tiêu tiết kiệm", description = "Lấy mục tiêu tiết kiệm của tháng.")
    @GetMapping("/savings-goals/{monthKey}")
    public ResponseEntity<SavingsGoalResponse> getSavingsGoal(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable String monthKey
    ) {
        return ResponseEntity.ok(expenseService.getSavingsGoal(currentUser.getId(), monthKey));
    }

    @Operation(summary = "Thiết lập mục tiêu tiết kiệm", description = "Tạo mới hoặc cập nhật mục tiêu tiết kiệm tháng.")
    @PutMapping("/savings-goals/{monthKey}")
    public ResponseEntity<SavingsGoalResponse> upsertSavingsGoal(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable String monthKey,
            @Valid @RequestBody SavingsGoalUpsertRequest request
    ) {
        return ResponseEntity.ok(expenseService.upsertSavingsGoal(currentUser.getId(), monthKey, request));
    }

    private record YearMonthRange(LocalDate referenceDate) {
        static YearMonthRange fromMonthKey(String monthKey) {
            if (monthKey == null || !monthKey.matches("\\d{6}")) {
                throw new AppException(ErrorCode.INVALID_MONTH_KEY);
            }
            try {
                int year = Integer.parseInt(monthKey.substring(0, 4));
                int month = Integer.parseInt(monthKey.substring(4));
                return new YearMonthRange(LocalDate.of(year, month, 1));
            } catch (RuntimeException ex) {
                throw new AppException(ErrorCode.INVALID_MONTH_KEY);
            }
        }
    }
}
