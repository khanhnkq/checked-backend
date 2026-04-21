package com.codegym.locketclone.expense;

import com.codegym.locketclone.expense.dto.BudgetResponse;
import com.codegym.locketclone.expense.dto.BudgetUpsertRequest;
import com.codegym.locketclone.expense.dto.CashflowSummaryResponse;
import com.codegym.locketclone.expense.dto.CategoryResponse;
import com.codegym.locketclone.expense.dto.CreateCategoryRequest;
import com.codegym.locketclone.expense.dto.ExpenseItemResponse;
import com.codegym.locketclone.expense.dto.ExpenseSummaryResponse;
import com.codegym.locketclone.expense.dto.UpdateCategoryRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface ExpenseService {
    List<CategoryResponse> getCategories(UUID userId);

    CategoryResponse createCategory(UUID userId, CreateCategoryRequest request);

    CategoryResponse updateCategory(UUID userId, UUID categoryId, UpdateCategoryRequest request);

    BudgetResponse getBudget(UUID userId, String monthKey);

    BudgetResponse upsertBudget(UUID userId, String monthKey, BudgetUpsertRequest request);

    Page<ExpenseItemResponse> getExpenseEntries(UUID userId, String monthKey, Pageable pageable);

    Page<ExpenseItemResponse> getExpenseEntries(UUID userId, String monthKey, TransactionType type, Pageable pageable);

    ExpenseSummaryResponse getExpenseSummary(UUID userId, String monthKey);

    CashflowSummaryResponse getCashflowSummary(UUID userId, String monthKey);
}

