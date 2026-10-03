package com.codegym.locketclone.expense;

import com.codegym.locketclone.expense.dto.CreateCategoryRequest;
import com.codegym.locketclone.expense.dto.SavingsGoalUpsertRequest;
import com.codegym.locketclone.expense.dto.UpdateCategoryRequest;
import com.codegym.locketclone.photo.Photo;
import com.codegym.locketclone.photo.PhotoRepository;
import com.codegym.locketclone.photo.PhotoStatus;
import com.codegym.locketclone.user.User;
import com.codegym.locketclone.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private BudgetRepository budgetRepository;
    @Mock
    private SavingsGoalRepository savingsGoalRepository;
    @Mock
    private PhotoRepository photoRepository;

    private ExpenseServiceImpl expenseService;

    @BeforeEach
    void setUp() {
        expenseService = new ExpenseServiceImpl(userRepository, categoryRepository, budgetRepository, savingsGoalRepository, photoRepository);
    }

    @Test
    void createCategory_defaultsToExpenseWhenTypeMissing() {
        UUID userId = UUID.randomUUID();
        User user = user(userId);
        Category saved = Category.builder()
                .id(UUID.randomUUID())
                .name("Food")
                .user(user)
                .isDefault(false)
                .isActive(true)
                .transactionType(TransactionType.EXPENSE)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(categoryRepository.existsByUser_IdAndNameIgnoreCase(userId, "Food")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenReturn(saved);

        var response = expenseService.createCategory(userId, new CreateCategoryRequest("Food", "restaurant", "#FF8A65", null));

        assertEquals(TransactionType.EXPENSE, response.transactionType());
        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(captor.capture());
        assertEquals(TransactionType.EXPENSE, captor.getValue().getTransactionType());
    }

    @Test
    void updateCategory_allowsChangingTransactionType() {
        UUID userId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        User user = user(userId);
        Category category = Category.builder()
                .id(categoryId)
                .name("Salary")
                .user(user)
                .isDefault(false)
                .isActive(true)
                .transactionType(TransactionType.EXPENSE)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = expenseService.updateCategory(userId, categoryId, new UpdateCategoryRequest(null, null, null, null, TransactionType.INCOME));

        assertEquals(TransactionType.INCOME, response.transactionType());
        verify(categoryRepository).save(any(Category.class));
    }

    @Test
    void getExpenseEntries_includesTransactionType() {
        UUID userId = UUID.randomUUID();
        User user = user(userId);
        Photo photo = Photo.builder()
                .id(UUID.randomUUID())
                .sender(user)
                .imageUrl("https://cdn.example.com/photo.jpg")
                .thumbnailUrl("https://cdn.example.com/photo_thumb.jpg")
                .amount(new BigDecimal("65000"))
                .note("Lunch")
                .transactionType(TransactionType.INCOME)
                .takenAt(LocalDateTime.of(2026, 4, 1, 12, 0))
                .createdAt(LocalDateTime.of(2026, 4, 1, 12, 1))
                .status(PhotoStatus.READY)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(photoRepository.findTransactionPhotosBySenderAndMonth(eq(userId), eq(PhotoStatus.DELETED), eq(TransactionType.INCOME), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(photo)));

        var result = expenseService.getExpenseEntries(userId, "202604", TransactionType.INCOME, Pageable.unpaged());

        assertEquals(1, result.getContent().size());
        assertEquals(TransactionType.INCOME, result.getContent().get(0).transactionType());
        assertNotNull(result.getContent().get(0).photoId());
    }

    @Test
    void getExpenseEntriesByPeriod_allType_usesRangeQuery() {
        UUID userId = UUID.randomUUID();
        User user = user(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(photoRepository.findTransactionPhotosBySenderAndRange(eq(userId), eq(PhotoStatus.DELETED), isNull(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        var result = expenseService.getExpenseEntriesByPeriod(
                userId,
                EntryPeriod.WEEK,
                LocalDate.of(2026, 4, 20),
                TransactionFilterType.ALL,
                Pageable.unpaged()
        );

        assertNotNull(result);
        verify(photoRepository).findTransactionPhotosBySenderAndRange(eq(userId), eq(PhotoStatus.DELETED), isNull(), any(), any(), any());
    }

    @Test
    void getTopCategories_limitsAndCalculatesPercentage() {
        UUID userId = UUID.randomUUID();
        User user = user(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(photoRepository.sumTransactionAmountBySenderInRange(eq(userId), eq(PhotoStatus.DELETED), eq(TransactionType.EXPENSE), any(), any()))
                .thenReturn(new BigDecimal("1000"));
        when(photoRepository.summarizeTransactionByCategoryInRange(eq(userId), eq(PhotoStatus.DELETED), eq(TransactionType.EXPENSE), any(), any()))
                .thenReturn(List.of(
                        new Object[]{UUID.randomUUID(), "Food", new BigDecimal("600")},
                        new Object[]{UUID.randomUUID(), "Transport", new BigDecimal("200")}
                ));

        var result = expenseService.getTopCategories(userId, "202604", null, TransactionFilterType.EXPENSE, 1);

        assertEquals(1, result.size());
        assertEquals(60, result.get(0).percentage());
    }

    @Test
    void upsertSavingsGoal_returnsProgressBasedOnMonthlyNet() {
        UUID userId = UUID.randomUUID();
        User user = user(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(savingsGoalRepository.findByUser_IdAndMonthKey(userId, "202604")).thenReturn(Optional.empty());
        when(savingsGoalRepository.save(any(SavingsGoal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        YearMonth ym = YearMonth.of(2026, 4);
        LocalDateTime from = ym.atDay(1).atStartOfDay();
        LocalDateTime to = ym.plusMonths(1).atDay(1).atStartOfDay();

        when(photoRepository.sumTransactionAmountBySenderAndMonth(userId, PhotoStatus.DELETED, TransactionType.INCOME, from, to))
                .thenReturn(new BigDecimal("2000"));
        when(photoRepository.sumTransactionAmountBySenderAndMonth(userId, PhotoStatus.DELETED, TransactionType.EXPENSE, from, to))
                .thenReturn(new BigDecimal("500"));

        var result = expenseService.upsertSavingsGoal(
                userId,
                "202604",
                new SavingsGoalUpsertRequest(new BigDecimal("1000"))
        );

        assertEquals(new BigDecimal("1000"), result.targetAmount());
        assertEquals(new BigDecimal("1500"), result.currentSaved());
        assertEquals(Integer.valueOf(100), result.progressPct());
        assertEquals(Boolean.TRUE, result.achieved());
    }

    @Test
    void getYearlyCashflowSummary_aggregatesMonthlyDataCorrectly() {
        UUID userId = UUID.randomUUID();
        User user = user(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        LocalDateTime fromDate = LocalDateTime.of(2026, 1, 1, 0, 0, 0);
        LocalDateTime toDate = LocalDateTime.of(2027, 1, 1, 0, 0, 0);

        when(photoRepository.summarizeMonthlyTransactionsForYear(userId, PhotoStatus.DELETED, fromDate, toDate))
                .thenReturn(List.of(
                        new Object[]{1, TransactionType.INCOME, new BigDecimal("5000000")},
                        new Object[]{1, TransactionType.EXPENSE, new BigDecimal("2000000")},
                        new Object[]{4, TransactionType.INCOME, new BigDecimal("6000000")},
                        new Object[]{4, TransactionType.EXPENSE, new BigDecimal("1500000")}
                ));

        var response = expenseService.getYearlyCashflowSummary(userId, 2026);

        assertEquals(2026, response.year());
        assertEquals(new BigDecimal("11000000"), response.totalIncome());
        assertEquals(new BigDecimal("3500000"), response.totalExpense());
        assertEquals(new BigDecimal("7500000"), response.netCashflow());
        assertEquals(12, response.months().size());

        // Month 1
        var month1 = response.months().get(0);
        assertEquals("202601", month1.monthKey());
        assertEquals(new BigDecimal("5000000"), month1.income());
        assertEquals(new BigDecimal("2000000"), month1.expense());
        assertEquals(new BigDecimal("3000000"), month1.net());

        // Month 2 (no data -> zeroes)
        var month2 = response.months().get(1);
        assertEquals("202602", month2.monthKey());
        assertEquals(BigDecimal.ZERO, month2.income());
        assertEquals(BigDecimal.ZERO, month2.expense());
        assertEquals(BigDecimal.ZERO, month2.net());
    }

    private User user(UUID id) {
        return User.builder()
                .id(id)
                .email("user@example.com")
                .username("user")
                .password("secret")
                .isVerified(true)
                .build();
    }
}


