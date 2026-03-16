package com.codegym.locketclone.expense;

import com.codegym.locketclone.common.exception.AppException;
import com.codegym.locketclone.common.exception.ErrorCode;
import com.codegym.locketclone.expense.dto.*;
import com.codegym.locketclone.photo.Photo;
import com.codegym.locketclone.photo.PhotoRepository;
import com.codegym.locketclone.photo.PhotoStatus;
import com.codegym.locketclone.user.User;
import com.codegym.locketclone.user.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExpenseServiceImpl implements ExpenseService {
    private static final DateTimeFormatter MONTH_KEY_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM", Locale.ROOT);

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final BudgetRepository budgetRepository;
    private final PhotoRepository photoRepository;

    @Override
    @Transactional
    public List<CategoryResponse> getCategories(UUID userId) {
        ensureUserExists(userId);
        return categoryRepository.findVisibleActiveCategories(userId)
                .stream()
                .map(this::toCategoryResponse)
                .toList();
    }

    @Override
    @Transactional
    public CategoryResponse createCategory(UUID userId, CreateCategoryRequest request) {
        User user = ensureUserExists(userId);
        String normalizedName = normalizeRequired(request.name(), 100);

        if (categoryRepository.existsByUser_IdAndNameIgnoreCase(userId, normalizedName)) {
            throw new AppException(ErrorCode.CATEGORY_NAME_ALREADY_EXISTS);
        }

        Category category = Category.builder()
                .name(normalizedName)
                .icon(normalizeOptional(request.icon(), 50))
                .color(normalizeOptional(request.color(), 20))
                .user(user)
                .isDefault(false)
                .isActive(true)
                .build();

        return toCategoryResponse(categoryRepository.save(category));
    }

    @Override
    @Transactional
    public CategoryResponse updateCategory(UUID userId, UUID categoryId, UpdateCategoryRequest request) {
        ensureUserExists(userId);
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));

        if (category.getUser() == null || !category.getUser().getId().equals(userId)) {
            throw new AppException(ErrorCode.CATEGORY_NOT_FOUND);
        }

        if (request.name() != null) {
            String normalizedName = normalizeRequired(request.name(), 100);
            boolean duplicated = categoryRepository.existsByUser_IdAndNameIgnoreCase(userId, normalizedName)
                    && !normalizedName.equalsIgnoreCase(category.getName());
            if (duplicated) {
                throw new AppException(ErrorCode.CATEGORY_NAME_ALREADY_EXISTS);
            }
            category.setName(normalizedName);
        }

        if (request.icon() != null) {
            category.setIcon(normalizeOptional(request.icon(), 50));
        }

        if (request.color() != null) {
            category.setColor(normalizeOptional(request.color(), 20));
        }

        if (request.isActive() != null) {
            category.setIsActive(request.isActive());
        }

        return toCategoryResponse(categoryRepository.save(category));
    }

    @Override
    @Transactional
    public BudgetResponse getBudget(UUID userId, String monthKey) {
        ensureUserExists(userId);
        YearMonth yearMonth = parseMonthKey(monthKey);
        MonthRange monthRange = monthRange(yearMonth);

        BigDecimal spent = safeAmount(photoRepository.sumExpenseAmountBySenderAndMonth(
                userId, PhotoStatus.DELETED, monthRange.fromDate(), monthRange.toDate()
        ));

        Budget budget = budgetRepository.findByUser_IdAndMonthKey(userId, yearMonth.format(MONTH_KEY_FORMATTER))
                .orElse(null);

        if (budget == null) {
            return new BudgetResponse(
                    yearMonth.format(MONTH_KEY_FORMATTER),
                    null,
                    null,
                    spent,
                    null,
                    false
            );
        }

        BigDecimal remaining = budget.getAmountLimit().subtract(spent);
        boolean exceeded = remaining.signum() < 0;

        return new BudgetResponse(
                budget.getMonthKey(),
                budget.getAmountLimit(),
                budget.getAlertThresholdPct(),
                spent,
                remaining,
                exceeded
        );
    }

    @Override
    @Transactional
    public BudgetResponse upsertBudget(UUID userId, String monthKey, BudgetUpsertRequest request) {
        User user = ensureUserExists(userId);
        YearMonth yearMonth = parseMonthKey(monthKey);

        BigDecimal amountLimit = request.amountLimit();
        if (amountLimit == null || amountLimit.signum() <= 0) {
            throw new AppException(ErrorCode.INVALID_BUDGET_LIMIT);
        }

        Integer threshold = request.alertThresholdPct() == null ? 80 : request.alertThresholdPct();
        if (threshold < 1 || threshold > 100) {
            throw new AppException(ErrorCode.INVALID_ALERT_THRESHOLD);
        }

        String normalizedMonthKey = yearMonth.format(MONTH_KEY_FORMATTER);
        Budget budget = budgetRepository.findByUser_IdAndMonthKey(userId, normalizedMonthKey)
                .orElseGet(() -> Budget.builder().user(user).monthKey(normalizedMonthKey).build());

        budget.setAmountLimit(amountLimit);
        budget.setAlertThresholdPct(threshold);
        budgetRepository.save(budget);

        return getBudget(userId, normalizedMonthKey);
    }

    @Override
    @Transactional
    public Page<ExpenseItemResponse> getExpenseEntries(UUID userId, String monthKey, Pageable pageable) {
        ensureUserExists(userId);
        YearMonth yearMonth = parseMonthKey(monthKey);
        MonthRange monthRange = monthRange(yearMonth);

        return photoRepository.findExpensePhotosBySenderAndMonth(
                        userId,
                        PhotoStatus.DELETED,
                        monthRange.fromDate(),
                        monthRange.toDate(),
                        pageable
                )
                .map(this::toExpenseItemResponse);
    }

    @Override
    @Transactional
    public ExpenseSummaryResponse getExpenseSummary(UUID userId, String monthKey) {
        ensureUserExists(userId);
        YearMonth yearMonth = parseMonthKey(monthKey);
        MonthRange monthRange = monthRange(yearMonth);
        String normalizedMonthKey = yearMonth.format(MONTH_KEY_FORMATTER);

        BigDecimal totalSpent = safeAmount(photoRepository.sumExpenseAmountBySenderAndMonth(
                userId, PhotoStatus.DELETED, monthRange.fromDate(), monthRange.toDate()
        ));

        Budget budget = budgetRepository.findByUser_IdAndMonthKey(userId, normalizedMonthKey).orElse(null);
        BigDecimal budgetLimit = budget != null ? budget.getAmountLimit() : null;
        BigDecimal remaining = budgetLimit != null ? budgetLimit.subtract(totalSpent) : null;
        boolean budgetExceeded = remaining != null && remaining.signum() < 0;

        Integer percentUsed = null;
        if (budgetLimit != null && budgetLimit.signum() > 0) {
            BigDecimal rawPercent = totalSpent
                    .multiply(BigDecimal.valueOf(100))
                    .divide(budgetLimit, 0, RoundingMode.HALF_UP);
            percentUsed = rawPercent.intValue();
        }

        List<CategorySpendResponse> byCategory = photoRepository
                .summarizeExpenseByCategory(userId, PhotoStatus.DELETED, monthRange.fromDate(), monthRange.toDate())
                .stream()
                .map(this::toCategorySpendResponse)
                .toList();

        return new ExpenseSummaryResponse(
                normalizedMonthKey,
                totalSpent,
                budgetLimit,
                remaining,
                budgetExceeded,
                percentUsed,
                byCategory
        );
    }

    private User ensureUserExists(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    private YearMonth parseMonthKey(String monthKey) {
        if (!StringUtils.hasText(monthKey)) {
            throw new AppException(ErrorCode.INVALID_MONTH_KEY);
        }
        String normalized = monthKey.trim();
        if (!normalized.matches("\\d{6}")) {
            throw new AppException(ErrorCode.INVALID_MONTH_KEY);
        }
        try {
            return YearMonth.parse(normalized, MONTH_KEY_FORMATTER);
        } catch (DateTimeParseException ex) {
            throw new AppException(ErrorCode.INVALID_MONTH_KEY);
        }
    }

    private MonthRange monthRange(YearMonth yearMonth) {
        LocalDateTime from = yearMonth.atDay(1).atStartOfDay();
        LocalDateTime to = yearMonth.plusMonths(1).atDay(1).atStartOfDay();
        return new MonthRange(from, to);
    }

    private CategoryResponse toCategoryResponse(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getIcon(),
                category.getColor(),
                category.getIsDefault(),
                category.getIsActive()
        );
    }

    private ExpenseItemResponse toExpenseItemResponse(Photo photo) {
        return new ExpenseItemResponse(
                photo.getId(),
                photo.getImageUrl(),
                photo.getThumbnailUrl(),
                safeAmount(photo.getAmount()),
                photo.getNote(),
                photo.getCategory() != null ? photo.getCategory().getId() : null,
                photo.getCategory() != null ? photo.getCategory().getName() : null,
                photo.getTakenAt(),
                photo.getCreatedAt()
        );
    }

    private CategorySpendResponse toCategorySpendResponse(Object[] row) {
        UUID categoryId = (UUID) row[0];
        String categoryName = (String) row[1];
        BigDecimal totalAmount = safeAmount((BigDecimal) row[2]);
        return new CategorySpendResponse(categoryId, categoryName, totalAmount);
    }

    private BigDecimal safeAmount(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private String normalizeRequired(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        return normalized;
    }

    private String normalizeOptional(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > maxLength) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        return normalized;
    }

    private record MonthRange(LocalDateTime fromDate, LocalDateTime toDate) {
    }
}

