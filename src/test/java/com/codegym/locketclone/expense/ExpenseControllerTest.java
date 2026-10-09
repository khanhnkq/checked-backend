package com.codegym.locketclone.expense;

import com.codegym.locketclone.common.exception.GlobalExceptionHandler;
import com.codegym.locketclone.expense.dto.*;
import com.codegym.locketclone.security.service.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ExpenseControllerTest {

    @Mock
    private ExpenseService expenseService;

    private MockMvc mockMvc;
    private final UUID testUserId = UUID.randomUUID();
    private UserPrincipal testPrincipal;

    @BeforeEach
    void setUp() {
        testPrincipal = new UserPrincipal(
                testUserId,
                "khanh_dev",
                "khanh@example.com",
                "secret",
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                true
        );

        ExpenseController expenseController = new ExpenseController(expenseService);

        HandlerMethodArgumentResolver principalResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.getParameterType().equals(UserPrincipal.class)
                        || parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
            }

            @Override
            public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                return testPrincipal;
            }
        };

        mockMvc = MockMvcBuilders.standaloneSetup(expenseController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(principalResolver, new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    void getCategories_returnsCategoryList() throws Exception {
        UUID catId = UUID.randomUUID();
        List<CategoryResponse> categories = List.of(
                new CategoryResponse(catId, "Ăn uống", "restaurant", "#FF5722", true, true, TransactionType.EXPENSE)
        );
        when(expenseService.getCategories(testUserId)).thenReturn(categories);

        mockMvc.perform(get("/api/v1/expense/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(catId.toString()))
                .andExpect(jsonPath("$[0].name").value("Ăn uống"))
                .andExpect(jsonPath("$[0].transactionType").value("EXPENSE"));

        verify(expenseService).getCategories(testUserId);
    }

    @Test
    void createCategory_withValidRequest_returnsCreated() throws Exception {
        UUID catId = UUID.randomUUID();
        CategoryResponse response = new CategoryResponse(catId, "Lương", "payments", "#4CAF50", false, true, TransactionType.INCOME);
        when(expenseService.createCategory(eq(testUserId), any(CreateCategoryRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/expense/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Lương",
                                  "icon": "payments",
                                  "color": "#4CAF50",
                                  "transactionType": "INCOME"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(catId.toString()))
                .andExpect(jsonPath("$.name").value("Lương"))
                .andExpect(jsonPath("$.transactionType").value("INCOME"));
    }

    @Test
    void createCategory_withBlankName_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/expense/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "   ",
                                  "transactionType": "EXPENSE"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Dữ liệu yêu cầu không hợp lệ"))
                .andExpect(jsonPath("$.errors.name").value("Tên danh mục không được để trống"));
    }

    @Test
    void updateCategory_withValidRequest_returnsOk() throws Exception {
        UUID catId = UUID.randomUUID();
        CategoryResponse response = new CategoryResponse(catId, "Du lịch", "flight", "#00BCD4", false, true, TransactionType.EXPENSE);
        when(expenseService.updateCategory(eq(testUserId), eq(catId), any(UpdateCategoryRequest.class))).thenReturn(response);

        mockMvc.perform(patch("/api/v1/expense/categories/{categoryId}", catId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Du lịch",
                                  "icon": "flight"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Du lịch"));

        verify(expenseService).updateCategory(eq(testUserId), eq(catId), any(UpdateCategoryRequest.class));
    }

    @Test
    void getBudget_returnsBudgetResponse() throws Exception {
        BudgetResponse budget = new BudgetResponse(
                "202610",
                new BigDecimal("5000000"),
                80,
                new BigDecimal("1500000"),
                new BigDecimal("3500000"),
                false
        );
        when(expenseService.getBudget(testUserId, "202610")).thenReturn(budget);

        mockMvc.perform(get("/api/v1/expense/budgets/{monthKey}", "202610"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthKey").value("202610"))
                .andExpect(jsonPath("$.amountLimit").value(5000000))
                .andExpect(jsonPath("$.spent").value(1500000))
                .andExpect(jsonPath("$.remaining").value(3500000))
                .andExpect(jsonPath("$.exceeded").value(false));

        verify(expenseService).getBudget(testUserId, "202610");
    }

    @Test
    void upsertBudget_withValidRequest_returnsOk() throws Exception {
        BudgetResponse budget = new BudgetResponse(
                "202610",
                new BigDecimal("6000000"),
                85,
                new BigDecimal("1000000"),
                new BigDecimal("5000000"),
                false
        );
        when(expenseService.upsertBudget(eq(testUserId), eq("202610"), any(BudgetUpsertRequest.class))).thenReturn(budget);

        mockMvc.perform(put("/api/v1/expense/budgets/{monthKey}", "202610")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amountLimit": 6000000,
                                  "alertThresholdPct": 85
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amountLimit").value(6000000))
                .andExpect(jsonPath("$.alertThresholdPct").value(85));
    }

    @Test
    void upsertBudget_withNullAmountLimit_returnsBadRequest() throws Exception {
        mockMvc.perform(put("/api/v1/expense/budgets/{monthKey}", "202610")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amountLimit": null,
                                  "alertThresholdPct": 80
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amountLimit").value("amountLimit là bắt buộc"));
    }

    @Test
    void getExpenseEntries_withoutType_returnsPage() throws Exception {
        ExpenseItemResponse item = new ExpenseItemResponse(
                UUID.randomUUID(),
                "https://cdn.example.com/p1.jpg",
                "https://cdn.example.com/t1.jpg",
                new BigDecimal("45000"),
                "Cà phê",
                UUID.randomUUID(),
                "Ăn uống",
                TransactionType.EXPENSE,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        org.springframework.data.domain.PageRequest pageRequest = org.springframework.data.domain.PageRequest.of(0, 20);
        when(expenseService.getExpenseEntries(eq(testUserId), eq("202610"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(item), pageRequest, 1));

        mockMvc.perform(get("/api/v1/expense/entries")
                        .param("monthKey", "202610"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].note").value("Cà phê"))
                .andExpect(jsonPath("$.content[0].amount").value(45000));

        verify(expenseService).getExpenseEntries(eq(testUserId), eq("202610"), any(Pageable.class));
    }

    @Test
    void getExpenseEntries_withExpenseType_returnsPage() throws Exception {
        org.springframework.data.domain.PageRequest pageRequest = org.springframework.data.domain.PageRequest.of(0, 20);
        when(expenseService.getExpenseEntries(eq(testUserId), eq("202610"), eq(TransactionType.EXPENSE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), pageRequest, 0));

        mockMvc.perform(get("/api/v1/expense/entries")
                        .param("monthKey", "202610")
                        .param("type", "EXPENSE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        verify(expenseService).getExpenseEntries(eq(testUserId), eq("202610"), eq(TransactionType.EXPENSE), any(Pageable.class));
    }

    @Test
    void getExpenseEntries_withAllType_returnsPage() throws Exception {
        org.springframework.data.domain.PageRequest pageRequest = org.springframework.data.domain.PageRequest.of(0, 20);
        when(expenseService.getExpenseEntriesByPeriod(
                eq(testUserId),
                eq(EntryPeriod.MONTH),
                eq(LocalDate.of(2026, 10, 1)),
                eq(TransactionFilterType.ALL),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(), pageRequest, 0));

        mockMvc.perform(get("/api/v1/expense/entries")
                        .param("monthKey", "202610")
                        .param("type", "ALL"))
                .andExpect(status().isOk());

        verify(expenseService).getExpenseEntriesByPeriod(
                eq(testUserId),
                eq(EntryPeriod.MONTH),
                eq(LocalDate.of(2026, 10, 1)),
                eq(TransactionFilterType.ALL),
                any(Pageable.class)
        );
    }

    @Test
    void getExpenseEntries_withInvalidType_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/expense/entries")
                        .param("monthKey", "202610")
                        .param("type", "INVALID_FILTER"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Loại giao dịch không hợp lệ"));
    }

    @Test
    void getExpenseEntriesByPeriod_returnsPage() throws Exception {
        org.springframework.data.domain.PageRequest pageRequest = org.springframework.data.domain.PageRequest.of(0, 20);
        when(expenseService.getExpenseEntriesByPeriod(
                eq(testUserId),
                eq(EntryPeriod.MONTH),
                eq(LocalDate.of(2026, 10, 1)),
                eq(TransactionFilterType.EXPENSE),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(), pageRequest, 0));

        mockMvc.perform(get("/api/v1/expense/entries/by-period")
                        .param("period", "MONTH")
                        .param("referenceDate", "2026-10-01")
                        .param("type", "EXPENSE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        verify(expenseService).getExpenseEntriesByPeriod(
                eq(testUserId),
                eq(EntryPeriod.MONTH),
                eq(LocalDate.of(2026, 10, 1)),
                eq(TransactionFilterType.EXPENSE),
                any(Pageable.class)
        );
    }

    @Test
    void getExpenseSummary_returnsSummary() throws Exception {
        ExpenseSummaryResponse summary = new ExpenseSummaryResponse(
                "202610",
                new BigDecimal("2000000"),
                new BigDecimal("5000000"),
                new BigDecimal("3000000"),
                false,
                40,
                List.of()
        );
        when(expenseService.getExpenseSummary(testUserId, "202610")).thenReturn(summary);

        mockMvc.perform(get("/api/v1/expense/summary")
                        .param("monthKey", "202610"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthKey").value("202610"))
                .andExpect(jsonPath("$.percentUsed").value(40));

        verify(expenseService).getExpenseSummary(testUserId, "202610");
    }

    @Test
    void getExpenseSummary_missingMonthKey_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/expense/summary"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Thiếu tham số yêu cầu: monthKey"));
    }

    @Test
    void getCashflowSummary_returnsCashflow() throws Exception {
        CashflowSummaryResponse response = new CashflowSummaryResponse(
                "202610",
                new BigDecimal("15000000"),
                new BigDecimal("5000000"),
                new BigDecimal("10000000"),
                new BigDecimal("7000000"),
                new BigDecimal("2000000"),
                71,
                false,
                List.of(),
                List.of()
        );
        when(expenseService.getCashflowSummary(testUserId, "202610")).thenReturn(response);

        mockMvc.perform(get("/api/v1/expense/cashflow")
                        .param("monthKey", "202610"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalIncome").value(15000000))
                .andExpect(jsonPath("$.netCashflow").value(10000000));

        verify(expenseService).getCashflowSummary(testUserId, "202610");
    }

    @Test
    void getYearlyCashflowSummary_returnsYearlySummary() throws Exception {
        YearlyCashflowSummaryResponse response = new YearlyCashflowSummaryResponse(
                2026,
                new BigDecimal("120000000"),
                new BigDecimal("60000000"),
                new BigDecimal("60000000"),
                List.of()
        );
        when(expenseService.getYearlyCashflowSummary(testUserId, 2026)).thenReturn(response);

        mockMvc.perform(get("/api/v1/expense/summary/yearly")
                        .param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(2026))
                .andExpect(jsonPath("$.netCashflow").value(60000000));

        verify(expenseService).getYearlyCashflowSummary(testUserId, 2026);
    }

    @Test
    void getTopCategories_returnsTopCategories() throws Exception {
        TopCategoryResponse top = new TopCategoryResponse(
                UUID.randomUUID(),
                "Ăn uống",
                new BigDecimal("1200000"),
                60
        );
        when(expenseService.getTopCategories(testUserId, "202610", null, TransactionFilterType.EXPENSE, 5))
                .thenReturn(List.of(top));

        mockMvc.perform(get("/api/v1/expense/categories/top")
                        .param("monthKey", "202610")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].categoryName").value("Ăn uống"))
                .andExpect(jsonPath("$[0].percentage").value(60));

        verify(expenseService).getTopCategories(testUserId, "202610", null, TransactionFilterType.EXPENSE, 5);
    }

    @Test
    void getSavingsGoal_returnsSavingsGoal() throws Exception {
        SavingsGoalResponse goal = new SavingsGoalResponse(
                "202610",
                new BigDecimal("5000000"),
                new BigDecimal("2500000"),
                new BigDecimal("2500000"),
                50,
                false
        );
        when(expenseService.getSavingsGoal(testUserId, "202610")).thenReturn(goal);

        mockMvc.perform(get("/api/v1/expense/savings-goals/{monthKey}", "202610"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthKey").value("202610"))
                .andExpect(jsonPath("$.progressPct").value(50));

        verify(expenseService).getSavingsGoal(testUserId, "202610");
    }

    @Test
    void upsertSavingsGoal_withValidRequest_returnsOk() throws Exception {
        SavingsGoalResponse goal = new SavingsGoalResponse(
                "202610",
                new BigDecimal("8000000"),
                new BigDecimal("1000000"),
                new BigDecimal("7000000"),
                12,
                false
        );
        when(expenseService.upsertSavingsGoal(eq(testUserId), eq("202610"), any(SavingsGoalUpsertRequest.class)))
                .thenReturn(goal);

        mockMvc.perform(put("/api/v1/expense/savings-goals/{monthKey}", "202610")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetAmount": 8000000
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetAmount").value(8000000));
    }

    @Test
    void upsertSavingsGoal_withNegativeAmount_returnsBadRequest() throws Exception {
        mockMvc.perform(put("/api/v1/expense/savings-goals/{monthKey}", "202610")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetAmount": -5000
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.targetAmount").value("targetAmount phai > 0"));
    }

    @Test
    void unsupportedMethod_returnsMethodNotAllowed() throws Exception {
        mockMvc.perform(delete("/api/v1/expense/categories"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.message").value("Phương thức HTTP không được hỗ trợ: DELETE"));
    }
}
