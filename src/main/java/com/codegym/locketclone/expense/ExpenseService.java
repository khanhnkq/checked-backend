package com.codegym.locketclone.expense;

import com.codegym.locketclone.expense.dto.BudgetResponse;
import com.codegym.locketclone.expense.dto.BudgetUpsertRequest;
import com.codegym.locketclone.expense.dto.CashflowSummaryResponse;
import com.codegym.locketclone.expense.dto.CategoryResponse;
import com.codegym.locketclone.expense.dto.CreateCategoryRequest;
import com.codegym.locketclone.expense.dto.ExpenseItemResponse;
import com.codegym.locketclone.expense.dto.ExpenseSummaryResponse;
import com.codegym.locketclone.expense.dto.UpdateCategoryRequest;
import com.codegym.locketclone.expense.dto.SavingsGoalResponse;
import com.codegym.locketclone.expense.dto.SavingsGoalUpsertRequest;
import com.codegym.locketclone.expense.dto.TopCategoryResponse;
import com.codegym.locketclone.expense.dto.YearlyCashflowSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
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

    Page<ExpenseItemResponse> getExpenseEntriesByPeriod(
            UUID userId,
            EntryPeriod period,
            LocalDate referenceDate,
            TransactionFilterType type,
            Pageable pageable
    );

    ExpenseSummaryResponse getExpenseSummary(UUID userId, String monthKey);

    CashflowSummaryResponse getCashflowSummary(UUID userId, String monthKey);

    YearlyCashflowSummaryResponse getYearlyCashflowSummary(UUID userId, Integer year);

    java.util.List<TopCategoryResponse> getTopCategories(
            UUID userId,
            String monthKey,
            Integer year,
            TransactionFilterType type,
            Integer limit
    );

    SavingsGoalResponse getSavingsGoal(UUID userId, String monthKey);

    SavingsGoalResponse upsertSavingsGoal(UUID userId, String monthKey, SavingsGoalUpsertRequest request);
}
