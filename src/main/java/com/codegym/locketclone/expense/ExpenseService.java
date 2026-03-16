package com.codegym.locketclone.expense;

import com.codegym.locketclone.expense.dto.*;
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

    ExpenseSummaryResponse getExpenseSummary(UUID userId, String monthKey);
}

