package com.codegym.locketclone.expense;

import com.codegym.locketclone.common.exception.AppException;
import com.codegym.locketclone.common.exception.ErrorCode;
import com.codegym.locketclone.expense.dto.*;
import com.codegym.locketclone.security.service.UserPrincipal;
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

@RestController
@RequestMapping("/api/v1/expense")
@RequiredArgsConstructor
public class ExpenseController {
    private final ExpenseService expenseService;

    @GetMapping("/categories")
    public ResponseEntity<List<CategoryResponse>> getCategories(@AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(expenseService.getCategories(currentUser.getId()));
    }

    @PostMapping("/categories")
    public ResponseEntity<CategoryResponse> createCategory(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody CreateCategoryRequest request
    ) {
        CategoryResponse response = expenseService.createCategory(currentUser.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/categories/{categoryId}")
    public ResponseEntity<CategoryResponse> updateCategory(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable UUID categoryId,
            @Valid @RequestBody UpdateCategoryRequest request
    ) {
        return ResponseEntity.ok(expenseService.updateCategory(currentUser.getId(), categoryId, request));
    }

    @GetMapping("/budgets/{monthKey}")
    public ResponseEntity<BudgetResponse> getBudget(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable String monthKey
    ) {
        return ResponseEntity.ok(expenseService.getBudget(currentUser.getId(), monthKey));
    }

    @PutMapping("/budgets/{monthKey}")
    public ResponseEntity<BudgetResponse> upsertBudget(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable String monthKey,
            @Valid @RequestBody BudgetUpsertRequest request
    ) {
        return ResponseEntity.ok(expenseService.upsertBudget(currentUser.getId(), monthKey, request));
    }

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

    @GetMapping("/summary")
    public ResponseEntity<ExpenseSummaryResponse> getExpenseSummary(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam String monthKey
    ) {
        return ResponseEntity.ok(expenseService.getExpenseSummary(currentUser.getId(), monthKey));
    }

    @GetMapping("/cashflow")
    public ResponseEntity<CashflowSummaryResponse> getCashflowSummary(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam String monthKey
    ) {
        return ResponseEntity.ok(expenseService.getCashflowSummary(currentUser.getId(), monthKey));
    }

    @GetMapping("/summary/yearly")
    public ResponseEntity<YearlyCashflowSummaryResponse> getYearlyCashflowSummary(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam Integer year
    ) {
        return ResponseEntity.ok(expenseService.getYearlyCashflowSummary(currentUser.getId(), year));
    }

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

    @GetMapping("/savings-goals/{monthKey}")
    public ResponseEntity<SavingsGoalResponse> getSavingsGoal(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable String monthKey
    ) {
        return ResponseEntity.ok(expenseService.getSavingsGoal(currentUser.getId(), monthKey));
    }

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
