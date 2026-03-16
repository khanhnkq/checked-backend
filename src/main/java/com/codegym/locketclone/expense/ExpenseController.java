package com.codegym.locketclone.expense;

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
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(expenseService.getExpenseEntries(currentUser.getId(), monthKey, pageable));
    }

    @GetMapping("/summary")
    public ResponseEntity<ExpenseSummaryResponse> getExpenseSummary(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam String monthKey
    ) {
        return ResponseEntity.ok(expenseService.getExpenseSummary(currentUser.getId(), monthKey));
    }
}

