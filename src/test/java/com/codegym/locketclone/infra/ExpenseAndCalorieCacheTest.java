package com.codegym.locketclone.infra;

import com.codegym.locketclone.LocketCloneApplication;
import com.codegym.locketclone.calorie.CalorieLog;
import com.codegym.locketclone.calorie.CalorieLogRepository;
import com.codegym.locketclone.calorie.CalorieService;
import com.codegym.locketclone.calorie.MealType;
import com.codegym.locketclone.calorie.dto.CalorieGoalRequestDTO;
import com.codegym.locketclone.calorie.dto.CalorieGoalResponseDTO;
import com.codegym.locketclone.calorie.dto.CalorieLogItemDTO;
import com.codegym.locketclone.calorie.dto.CreateCalorieLogRequestDTO;
import com.codegym.locketclone.calorie.dto.DailyCalorieSummaryDTO;
import com.codegym.locketclone.calorie.dto.MonthlyCalorieSummaryDTO;
import com.codegym.locketclone.common.config.CacheConfig;
import com.codegym.locketclone.expense.BudgetRepository;
import com.codegym.locketclone.expense.Category;
import com.codegym.locketclone.expense.CategoryRepository;
import com.codegym.locketclone.expense.ExpenseService;
import com.codegym.locketclone.expense.TransactionType;
import com.codegym.locketclone.expense.dto.BudgetResponse;
import com.codegym.locketclone.expense.dto.BudgetUpsertRequest;
import com.codegym.locketclone.expense.dto.CashflowSummaryResponse;
import com.codegym.locketclone.expense.dto.CategoryResponse;
import com.codegym.locketclone.expense.dto.CreateCategoryRequest;
import com.codegym.locketclone.expense.dto.ExpenseSummaryResponse;
import com.codegym.locketclone.expense.dto.YearlyCashflowSummaryResponse;
import com.codegym.locketclone.photo.Photo;
import com.codegym.locketclone.photo.PhotoRepository;
import com.codegym.locketclone.photo.PhotoService;
import com.codegym.locketclone.photo.PhotoStatus;
import com.codegym.locketclone.photo.RecipientScope;
import com.codegym.locketclone.user.User;
import com.codegym.locketclone.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = LocketCloneApplication.class)
class ExpenseAndCalorieCacheTest {

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private ExpenseService expenseService;

    @Autowired
    private CalorieService calorieService;

    @Autowired
    private PhotoService photoService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private BudgetRepository budgetRepository;

    @Autowired
    private PhotoRepository photoRepository;

    @Autowired
    private CalorieLogRepository calorieLogRepository;

    private final List<UUID> createdUserIds = new ArrayList<>();
    private final List<UUID> createdCategoryIds = new ArrayList<>();
    private final List<UUID> createdPhotoIds = new ArrayList<>();
    private final List<UUID> createdCalorieLogIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        Cache categoriesCache = cacheManager.getCache(CacheConfig.EXPENSE_CATEGORIES_CACHE);
        Cache budgetsCache = cacheManager.getCache(CacheConfig.EXPENSE_BUDGETS_CACHE);
        Cache summaryCache = cacheManager.getCache(CacheConfig.EXPENSE_SUMMARY_CACHE);
        Cache cashflowCache = cacheManager.getCache(CacheConfig.EXPENSE_CASHFLOW_CACHE);

        Cache calorieGoalCache = cacheManager.getCache(CacheConfig.CALORIE_GOAL_CACHE);
        Cache calorieDailyCache = cacheManager.getCache(CacheConfig.CALORIE_DAILY_CACHE);
        Cache calorieSummaryCache = cacheManager.getCache(CacheConfig.CALORIE_SUMMARY_CACHE);

        for (UUID logId : createdCalorieLogIds) {
            try { calorieLogRepository.deleteById(logId); } catch (Exception ignored) {}
        }
        createdCalorieLogIds.clear();

        for (UUID photoId : createdPhotoIds) {
            try { photoRepository.deleteById(photoId); } catch (Exception ignored) {}
        }
        createdPhotoIds.clear();

        for (UUID catId : createdCategoryIds) {
            try { categoryRepository.deleteById(catId); } catch (Exception ignored) {}
        }
        createdCategoryIds.clear();

        for (UUID userId : createdUserIds) {
            if (categoriesCache != null) categoriesCache.evictIfPresent("list:" + userId);
            if (budgetsCache != null) budgetsCache.evictIfPresent(userId + ":202610");
            if (summaryCache != null) summaryCache.evictIfPresent(userId + ":202610");
            if (cashflowCache != null) {
                cashflowCache.evictIfPresent("m:" + userId + ":202610");
                cashflowCache.evictIfPresent("y:" + userId + ":2026");
            }
            if (calorieGoalCache != null) calorieGoalCache.evictIfPresent(userId);
            if (calorieDailyCache != null) calorieDailyCache.evictIfPresent(userId + ":" + LocalDate.now());
            if (calorieSummaryCache != null) {
                calorieSummaryCache.evictIfPresent(userId + ":202610");
                calorieSummaryCache.evictIfPresent(userId + ":");
            }
            try { budgetRepository.findByUser_IdAndMonthKey(userId, "202610").ifPresent(budgetRepository::delete); } catch (Exception ignored) {}
            try { userRepository.deleteById(userId); } catch (Exception ignored) {}
        }
        createdUserIds.clear();
    }

    private User createTestUser() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = User.builder()
                .username("cache_exp_" + suffix)
                .email("cache_exp_" + suffix + "@example.com")
                .password("$2a$10$encodedpassword")
                .firstName("Cache")
                .lastName("User")
                .isVerified(true)
                .build();
        User saved = userRepository.save(user);
        createdUserIds.add(saved.getId());
        return saved;
    }

    @Test
    @DisplayName("PR4 - Partition 3: Cache Categories danh mục chi tiêu hoạt động chuẩn xác")
    void testExpenseCategoriesCache_HitAndEviction() {
        User user = createTestUser();
        Cache categoriesCache = cacheManager.getCache(CacheConfig.EXPENSE_CATEGORIES_CACHE);
        assertNotNull(categoriesCache);

        String cacheKey = "list:" + user.getId();
        categoriesCache.evict(cacheKey);

        // Lần 1: Gọi getCategories -> Chưa có cache, lưu vào cache
        List<CategoryResponse> categories1 = expenseService.getCategories(user.getId());
        assertNotNull(categories1);

        Cache.ValueWrapper wrapper = getCacheWithRetry(categoriesCache, cacheKey);
        assertNotNull(wrapper, "Categories phải được cache vào Redis");
        assertInstanceOf(List.class, wrapper.get());

        // Lần 2: Hit cache và deserialize an toàn
        List<CategoryResponse> categories2 = expenseService.getCategories(user.getId());
        assertEquals(categories1.size(), categories2.size());

        // Tạo mới category -> Cache phải bị evict
        CreateCategoryRequest createRequest = new CreateCategoryRequest("Ăn vặt test", "food", "#FF5722", TransactionType.EXPENSE);
        CategoryResponse createdCat = expenseService.createCategory(user.getId(), createRequest);
        createdCategoryIds.add(createdCat.id());

        assertCacheEvicted(categoriesCache, cacheKey, "Categories cache phải bị evict khi tạo category mới");
    }

    @Test
    @DisplayName("PR4 - Partition 3: Cache Budget, Expense Summary & Cashflow hoạt động chuẩn xác")
    void testExpenseBudgetAndSummaryCache_HitAndEviction() {
        User user = createTestUser();
        String monthKey = "202610";

        Cache budgetsCache = cacheManager.getCache(CacheConfig.EXPENSE_BUDGETS_CACHE);
        Cache summaryCache = cacheManager.getCache(CacheConfig.EXPENSE_SUMMARY_CACHE);
        Cache cashflowCache = cacheManager.getCache(CacheConfig.EXPENSE_CASHFLOW_CACHE);

        assertNotNull(budgetsCache);
        assertNotNull(summaryCache);
        assertNotNull(cashflowCache);

        String budgetKey = user.getId() + ":" + monthKey;
        String summaryKey = user.getId() + ":" + monthKey;
        String cashflowMonthKey = "m:" + user.getId() + ":" + monthKey;
        String cashflowYearKey = "y:" + user.getId() + ":2026";

        budgetsCache.evict(budgetKey);
        summaryCache.evict(summaryKey);
        cashflowCache.evict(cashflowMonthKey);
        cashflowCache.evict(cashflowYearKey);

        // 1. Get Budget
        BudgetResponse budget1 = expenseService.getBudget(user.getId(), monthKey);
        assertNotNull(budget1);
        Cache.ValueWrapper budgetWrapper = getCacheWithRetry(budgetsCache, budgetKey);
        assertNotNull(budgetWrapper, "Budget phải được lưu vào Redis");

        // 2. Get Expense Summary
        ExpenseSummaryResponse summary1 = expenseService.getExpenseSummary(user.getId(), monthKey);
        assertNotNull(summary1);
        Cache.ValueWrapper summaryWrapper = getCacheWithRetry(summaryCache, summaryKey);
        assertNotNull(summaryWrapper, "Expense Summary phải được lưu vào Redis");

        // 3. Get Cashflow Summary (Tháng & Năm)
        CashflowSummaryResponse monthlyCashflow1 = expenseService.getCashflowSummary(user.getId(), monthKey);
        assertNotNull(monthlyCashflow1);
        Cache.ValueWrapper monthlyCashflowWrapper = getCacheWithRetry(cashflowCache, cashflowMonthKey);
        assertNotNull(monthlyCashflowWrapper, "Monthly Cashflow phải được lưu vào Redis");

        YearlyCashflowSummaryResponse yearlyCashflow1 = expenseService.getYearlyCashflowSummary(user.getId(), 2026);
        assertNotNull(yearlyCashflow1);
        Cache.ValueWrapper yearlyCashflowWrapper = getCacheWithRetry(cashflowCache, cashflowYearKey);
        assertNotNull(yearlyCashflowWrapper, "Yearly Cashflow phải được lưu vào Redis");

        // Lần 2: Hit Cache & Deserialize an toàn (không bị lỗi unmodifiableList)
        ExpenseSummaryResponse summary2 = expenseService.getExpenseSummary(user.getId(), monthKey);
        assertEquals(summary1.monthKey(), summary2.monthKey());
        assertEquals(summary1.byCategory().size(), summary2.byCategory().size());

        CashflowSummaryResponse monthlyCashflow2 = expenseService.getCashflowSummary(user.getId(), monthKey);
        assertEquals(monthlyCashflow1.monthKey(), monthlyCashflow2.monthKey());
        assertEquals(monthlyCashflow1.expenseByCategory().size(), monthlyCashflow2.expenseByCategory().size());

        YearlyCashflowSummaryResponse yearlyCashflow2 = expenseService.getYearlyCashflowSummary(user.getId(), 2026);
        assertEquals(yearlyCashflow1.year(), yearlyCashflow2.year());
        assertEquals(yearlyCashflow1.months().size(), yearlyCashflow2.months().size());

        // 4. Upsert Budget -> Phải evict budget, summary, và monthly cashflow
        BudgetUpsertRequest upsertRequest = new BudgetUpsertRequest(new BigDecimal("5000000"), 80);
        expenseService.upsertBudget(user.getId(), monthKey, upsertRequest);

        assertCacheEvicted(budgetsCache, budgetKey, "Budget cache phải bị evict khi upsert");
        assertCacheEvicted(summaryCache, summaryKey, "Summary cache phải bị evict khi upsert budget");
        assertCacheEvicted(cashflowCache, cashflowMonthKey, "Monthly cashflow cache phải bị evict khi upsert budget");
    }

    @Test
    @DisplayName("PR4 - Partition 4: Cache Calorie Goal hoạt động chuẩn xác")
    void testCalorieGoalCache_HitAndEviction() {
        User user = createTestUser();
        Cache goalCache = cacheManager.getCache(CacheConfig.CALORIE_GOAL_CACHE);
        assertNotNull(goalCache);

        goalCache.evict(user.getId());

        // Lần 1: Gọi getGoal -> Lưu vào cache
        CalorieGoalResponseDTO goal1 = calorieService.getGoal(user.getId());
        assertNotNull(goal1);

        Cache.ValueWrapper wrapper = getCacheWithRetry(goalCache, user.getId());
        assertNotNull(wrapper, "Calorie Goal phải được lưu vào Redis");

        // Lần 2: Hit cache
        CalorieGoalResponseDTO goal2 = calorieService.getGoal(user.getId());
        assertEquals(goal1.getDailyTargetCalories(), goal2.getDailyTargetCalories());

        // Cập nhật Goal -> Evict cache
        CalorieGoalRequestDTO updateRequest = new CalorieGoalRequestDTO(2500);
        CalorieGoalResponseDTO updated = calorieService.updateGoal(user.getId(), updateRequest);
        assertEquals(2500, updated.getDailyTargetCalories());

        assertCacheEvicted(goalCache, user.getId(), "Calorie goal cache phải bị evict khi update");
    }

    @Test
    @DisplayName("PR4 - Partition 4: Cache Calorie Daily Summary & Monthly Summary hoạt động chuẩn xác")
    void testCalorieDailyAndMonthlySummaryCache_HitAndEviction() {
        User user = createTestUser();
        LocalDate today = LocalDate.now();
        String monthKey = today.format(DateTimeFormatter.ofPattern("yyyyMM"));

        Cache dailyCache = cacheManager.getCache(CacheConfig.CALORIE_DAILY_CACHE);
        Cache summaryCache = cacheManager.getCache(CacheConfig.CALORIE_SUMMARY_CACHE);

        assertNotNull(dailyCache);
        assertNotNull(summaryCache);

        String dailyKey = user.getId() + ":" + today;
        String monthlyKey = user.getId() + ":" + monthKey;

        dailyCache.evict(dailyKey);
        summaryCache.evict(monthlyKey);

        // 1. Get Daily Summary
        DailyCalorieSummaryDTO daily1 = calorieService.getDailySummary(user.getId(), today);
        assertNotNull(daily1);
        Cache.ValueWrapper dailyWrapper = getCacheWithRetry(dailyCache, dailyKey);
        assertNotNull(dailyWrapper, "Daily Calorie Summary phải được cache vào Redis");

        // 2. Get Monthly Summary
        MonthlyCalorieSummaryDTO monthly1 = calorieService.getMonthlySummary(user.getId(), monthKey);
        assertNotNull(monthly1);
        Cache.ValueWrapper monthlyWrapper = getCacheWithRetry(summaryCache, monthlyKey);
        assertNotNull(monthlyWrapper, "Monthly Calorie Summary phải được cache vào Redis");

        // Lần 2: Hit Cache & Deserialize an toàn (không bị lỗi unmodifiableList meals)
        DailyCalorieSummaryDTO daily2 = calorieService.getDailySummary(user.getId(), today);
        assertEquals(daily1.getConsumedCalories(), daily2.getConsumedCalories());
        assertEquals(daily1.getMeals().size(), daily2.getMeals().size());

        MonthlyCalorieSummaryDTO monthly2 = calorieService.getMonthlySummary(user.getId(), monthKey);
        assertEquals(monthly1.getDays().size(), monthly2.getDays().size());

        // 3. Tạo Manual Log -> Phải evict cache daily và monthly
        CreateCalorieLogRequestDTO logRequest = CreateCalorieLogRequestDTO.builder()
                .calories(450)
                .dishName("Phở bò tái")
                .mealType(MealType.BREAKFAST)
                .loggedAt(today.atTime(8, 0))
                .build();
        CalorieLogItemDTO createdLog = calorieService.createManualLog(user.getId(), logRequest);
        createdCalorieLogIds.add(createdLog.getId());

        assertCacheEvicted(dailyCache, dailyKey, "Daily Calorie cache phải bị evict khi tạo log mới");
        assertCacheEvicted(summaryCache, monthlyKey, "Monthly Calorie cache phải bị evict khi tạo log mới");

        // 4. Xóa log -> Phải evict cache tiếp tục
        calorieService.deleteLog(user.getId(), createdLog.getId());
        createdCalorieLogIds.remove(createdLog.getId());

        assertCacheEvicted(dailyCache, dailyKey, "Daily Calorie cache phải bị evict khi xóa log");
        assertCacheEvicted(summaryCache, monthlyKey, "Monthly Calorie cache phải bị evict khi xóa log");
    }

    @Test
    @DisplayName("PR4 - Cross-Service: PhotoService xóa giao dịch sẽ evict Expense & Calorie Cache")
    void testPhotoCrossServiceEviction() {
        User user = createTestUser();
        LocalDate today = LocalDate.now();
        String monthKey = today.format(DateTimeFormatter.ofPattern("yyyyMM"));

        Cache summaryCache = cacheManager.getCache(CacheConfig.EXPENSE_SUMMARY_CACHE);
        Cache cashflowCache = cacheManager.getCache(CacheConfig.EXPENSE_CASHFLOW_CACHE);
        Cache calorieDailyCache = cacheManager.getCache(CacheConfig.CALORIE_DAILY_CACHE);

        String summaryKey = user.getId() + ":" + monthKey;
        String cashflowMonthKey = "m:" + user.getId() + ":" + monthKey;
        String calorieDailyKey = user.getId() + ":" + today;

        // Tạo 1 photo có transaction amount và calories
        Photo photo = Photo.builder()
                .sender(user)
                .imageUrl("https://example.com/test-photo.jpg")
                .caption("Cơm trưa văn phòng")
                .amount(new BigDecimal("45000"))
                .transactionType(TransactionType.EXPENSE)
                .calories(650)
                .status(PhotoStatus.READY)
                .recipientScope(RecipientScope.ALL_FRIENDS)
                .recipientCount(1)
                .occurredAt(today.atTime(12, 0))
                .takenAt(today.atTime(12, 0))
                .build();
        Photo savedPhoto = photoRepository.save(photo);
        createdPhotoIds.add(savedPhoto.getId());

        // Cache trước
        expenseService.getExpenseSummary(user.getId(), monthKey);
        expenseService.getCashflowSummary(user.getId(), monthKey);
        calorieService.getDailySummary(user.getId(), today);

        assertNotNull(getCacheWithRetry(summaryCache, summaryKey));
        assertNotNull(getCacheWithRetry(cashflowCache, cashflowMonthKey));
        assertNotNull(getCacheWithRetry(calorieDailyCache, calorieDailyKey));

        // Gọi deleteTransaction từ PhotoService
        photoService.deleteTransaction(user.getId(), savedPhoto.getId());

        // Xác nhận cross-service eviction thành công cho cả Expense & Calorie
        assertCacheEvicted(summaryCache, summaryKey, "Expense summary phải bị xóa khi delete transaction");
        assertCacheEvicted(cashflowCache, cashflowMonthKey, "Cashflow summary phải bị xóa khi delete transaction");
        assertCacheEvicted(calorieDailyCache, calorieDailyKey, "Calorie daily cache phải bị xóa khi delete transaction");
    }

    private void assertCacheEvicted(Cache cache, Object key, String message) {
        Cache.ValueWrapper wrapper = cache.get(key);
        if (wrapper != null) {
            try {
                Thread.sleep(250);
            } catch (InterruptedException ignored) {}
            wrapper = cache.get(key);
        }
        assertNull(wrapper, message);
    }

    private Cache.ValueWrapper getCacheWithRetry(Cache cache, Object key) {
        Cache.ValueWrapper wrapper = cache.get(key);
        if (wrapper == null) {
            try {
                Thread.sleep(250);
            } catch (InterruptedException ignored) {
            }
            wrapper = cache.get(key);
        }
        return wrapper;
    }
}
