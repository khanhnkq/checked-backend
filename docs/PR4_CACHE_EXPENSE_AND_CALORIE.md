# Kế Hoạch Chi Tiết PR #4: Phân Vùng 3 & 4 - Cache Dashboard Chi Tiêu & Nhật Ký Calo

> **Tài liệu tham chiếu:**
> - [REDIS_VALKEY_AND_RABBITMQ_CLOUD_INTEGRATION_PLAN.md](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/docs/REDIS_VALKEY_AND_RABBITMQ_CLOUD_INTEGRATION_PLAN.md)
> - [REDIS_RABBITMQ_PR_CHECKLIST.md](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/docs/REDIS_RABBITMQ_PR_CHECKLIST.md)
> **Nhánh Git dự kiến:** `feat/cache-partition-expense-and-calorie`  
> **Mục tiêu chính:** Giảm thiểu tối đa 100% các câu truy vấn Aggregate SQL nặng (`SUM`, `GROUP BY`, `DATE_TRUNC`) trên Neon PostgreSQL cho màn hình Dashboard Chi tiêu và Nhật ký Calo, tăng tốc độ phản hồi API từ ~250ms xuống < 3ms.

---

## 1. Bối Cảnh & Vấn Đề Cần Giải Quyết

1. **Điểm nghẽn ở màn hình Chi tiêu (Expense Dashboard)**:
   - Các API `getExpenseSummary`, `getCashflowSummary`, `getYearlyCashflowSummary`, `getTopCategories` phải thực thi nhiều câu lệnh SQL tổng hợp (`photoRepository.sumTransactionAmountBySenderAndMonth`, `photoRepository.summarizeTransactionByCategory`, `photoRepository.summarizeMonthlyTransactionsForYear`).
   - Tần suất xem dashboard của người dùng rất cao mỗi khi mở app, nhưng dữ liệu chi tiêu trong tháng chỉ thay đổi khi người dùng thêm/sửa/xóa giao dịch.
   - **Giải pháp:** Cache kết quả aggregate trong **30 phút** (`EXPENSE_SUMMARY_CACHE`, `EXPENSE_CASHFLOW_CACHE`, `EXPENSE_BUDGETS_CACHE`, `EXPENSE_CATEGORIES_CACHE`).

2. **Điểm nghẽn ở màn hình Calo (Calorie Tracker)**:
   - Các API `getDailySummary` và `getMonthlySummary` phải join fetch `Photo` và tổng hợp calo theo ngày (`calorieLogRepository.aggregateDailyCaloriesOfMonth`).
   - `getGoal` bị gọi lặp lại trong mỗi lần tính summary.
   - **Giải pháp:** Cache mục tiêu calo trong **1 giờ** (`CALORIE_GOAL_CACHE`), cache tóm tắt ngày và tháng trong **30 phút** (`CALORIE_DAILY_CACHE`, `CALORIE_SUMMARY_CACHE`).

3. **Thách thức về tính nhất quán dữ liệu (Cache Invalidation)**:
   - Giao dịch chi tiêu được tạo chủ yếu từ luồng **Upload Photo** (`PhotoService.uploadPhoto`, `updatePhotoExpense`, `deleteTransaction`).
   - Calorie log cũng có thể được tự động tạo từ ảnh món ăn (`CalorieService.createLogFromPhoto`).
   - **Yêu cầu:** Phải thiết lập cơ chế **Cross-Service Eviction** để khi người dùng đăng ảnh chi tiêu hoặc ảnh món ăn, cache dashboard chi tiêu và calo tự động xóa sạch, đảm bảo tính nhất quán tuyệt đối.

---

## 2. Ma Trận Cache & Cấu Hình TTL

| Phân Vùng | Cache Name | TTL | Method Đích | Định Dạng Cache Key (SpEL) | Kiểu Dữ Liệu Lưu Trữ |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Phân vùng 3 (Expense)** | `expense_summary` | 30m | `getExpenseSummary(userId, monthKey)` | `#userId + ':' + (#monthKey != null ? #monthKey : '')` | `ExpenseSummaryResponse` |
| | `expense_cashflow` | 30m | `getCashflowSummary(userId, monthKey)` | `'m:' + #userId + ':' + (#monthKey != null ? #monthKey : '')` | `CashflowSummaryResponse` |
| | `expense_cashflow` | 30m | `getYearlyCashflowSummary(userId, year)` | `'y:' + #userId + ':' + #year` | `YearlyCashflowSummaryResponse` |
| | `expense_budgets` | 30m | `getBudget(userId, monthKey)` | `#userId + ':' + (#monthKey != null ? #monthKey : '')` | `BudgetResponse` |
| | `expense_categories` | 30m | `getCategories(userId)` | `'list:' + #userId` | `List<CategoryResponse>` |
| **Phân vùng 4 (Calorie)** | `calorie_goal` | 1h | `getGoal(userId)` | `#userId` | `CalorieGoalResponseDTO` |
| | `calorie_daily` | 30m | `getDailySummary(userId, date)` | `#userId + ':' + (#date != null ? #date.toString() : T(java.time.LocalDate).now().toString())` | `DailyCalorieSummaryDTO` |
| | `calorie_summary` | 30m | `getMonthlySummary(userId, monthKey)` | `#userId + ':' + (#monthKey != null ? #monthKey : '')` | `MonthlyCalorieSummaryDTO` |

---

## 3. Chiến Lược Eviction (Cache Invalidation)

### A. Trong Expense Service (`ExpenseServiceImpl.java`)
- **`createCategory(userId, ...)`**:
  - Evict: `expense_categories` với key `'list:' + userId`.
- **`updateCategory(userId, categoryId, ...)`**:
  - Evict: `expense_categories` với key `'list:' + userId`.
  - Evict: `expense_summary` & `expense_cashflow` của user (vì tên/icon danh mục thay đổi).
- **`upsertBudget(userId, monthKey, ...)`**:
  - Evict: `expense_budgets` với key `userId + ':' + monthKey`.
  - Evict: `expense_summary` với key `userId + ':' + monthKey`.
  - Evict: `expense_cashflow` với key `'m:' + userId + ':' + monthKey`.

### B. Trong Calorie Service (`CalorieServiceImpl.java`)
- **`updateGoal(userId, ...)`**:
  - Evict: `calorie_goal` với key `userId`.
  - Evict: `calorie_daily` ngày hiện tại.
  - Evict: `calorie_summary` tháng hiện tại.
- **`createManualLog(userId, ...)`**:
  - Evict: `calorie_daily` với key `userId + ':' + loggedAt.toLocalDate()`.
  - Evict: `calorie_summary` với key `userId + ':' + YYYYMM`.
- **`deleteLog(userId, logId)`**:
  - Evict: `calorie_daily` ngày của log bị xóa.
  - Evict: `calorie_summary` tháng của log bị xóa.
- **`createLogFromPhoto(photo, ...)`**:
  - Evict: `calorie_daily` ngày của photo.
  - Evict: `calorie_summary` tháng của photo.

### C. Cross-Service Eviction từ Photo Service (`PhotoServiceImpl.java`)
- Khi người dùng đăng ảnh, sửa giao dịch hoặc xóa ảnh trong `PhotoService`:
  - Nếu photo có **khoản chi tiêu** (`amount != null`):
    - Evict `expense_summary`: `userId + ':' + YYYYMM`.
    - Evict `expense_cashflow`: `'m:' + userId + ':' + YYYYMM` và `'y:' + userId + ':' + YYYY`.
    - Evict `expense_budgets`: `userId + ':' + YYYYMM`.
  - Nếu photo có **calo** (`calories != null`):
    - Evict `calorie_daily`: `userId + ':' + photoDate`.
    - Evict `calorie_summary`: `userId + ':' + YYYYMM`.

---

## 4. Kiểm Thao Tác Jackson Deserialization (Anti-Bug Guideline)

- **Bài học từ PR #2 & PR #3**: Jackson `GenericJackson2JsonRedisSerializer` khi dùng kèm `WRAPPER_ARRAY` yêu cầu danh sách lồng trong DTO phải là `java.util.ArrayList`, tránh `Collections$UnmodifiableRandomAccessList` tạo ra từ `Stream.toList()`.
- **Kiểm soát DTO**:
  - Trong `ExpenseServiceImpl`: bọc `new ArrayList<>(...)` cho các nested list trong `ExpenseSummaryResponse`, `CashflowSummaryResponse`, `YearlyCashflowSummaryResponse`, và `getCategories`.
  - Trong `CalorieServiceImpl`: các DTO đã dùng Lombok `@Data`, `@NoArgsConstructor`, `@AllArgsConstructor` chuẩn xác.

---

## 5. Kế Hoạch Triển Khai Từng Bước

| Bước | Hành Động | File Thay Đổi | Tiêu Chuẩn Nghiệm Thu |
| :---: | :--- | :--- | :--- |
| **1** | Bổ sung `@Cacheable` & `@CacheEvict` trong `ExpenseService` & `ExpenseServiceImpl` | `ExpenseService.java`, `ExpenseServiceImpl.java` | Biên dịch thành công, các API summary/budget/category lưu cache Redis |
| **2** | Bổ sung `@Cacheable` & `@CacheEvict` trong `CalorieService` & `CalorieServiceImpl` | `CalorieService.java`, `CalorieServiceImpl.java` | Biên dịch thành công, goal/daily/monthly lưu cache Redis |
| **3** | Tích hợp Cross-Service Eviction trong `PhotoServiceImpl` | `PhotoServiceImpl.java` | Khi upload ảnh có chi tiêu/calo -> cache dashboard tự động evict |
| **4** | Viết Integration Test toàn diện | `ExpenseAndCalorieCacheTest.java` | 100% tests PASS kiểm thử hit cache, deserialization và evict đúng kỳ vọng |
| **5** | Chạy toàn bộ test suite | Toàn bộ dự án | `./gradlew test` (211+ tests PASS 100%) |
| **6** | Merge `main`, Push GitHub & Deploy Heroku | Heroku Dyno `heroku-7usd` | Dyno UP (Release v34), không lỗi R14/R15, phản hồi API < 50ms |
| **7** | Cập nhật checklist dự án | `docs/REDIS_RABBITMQ_PR_CHECKLIST.md` | Đánh dấu hoàn thành PR #4 |

---

## 6. Tiêu Chuẩn Nghiệm Thu (Acceptance Criteria)

1. **Chức năng**:
   - `getExpenseSummary`, `getCashflowSummary`, `getBudget`, `getCategories` hit cache Redis từ lần gọi thứ 2.
   - `getGoal`, `getDailySummary`, `getMonthlySummary` hit cache Redis từ lần gọi thứ 2.
   - Sửa ngân sách (`upsertBudget`) -> evict tức thì `expense_budgets`, `expense_summary`, `expense_cashflow`.
   - Sửa mục tiêu calo (`updateGoal`) -> evict tức thì `calorie_goal`, `calorie_daily`, `calorie_summary`.
   - Đăng ảnh kèm chi tiêu/calo -> evict tức thì các dashboard tương ứng.
2. **Hiệu năng & Ổn định**:
   - Serialization & Deserialization không ném bất kỳ ngoại lệ Jackson nào.
   - Fallback graceful degradation nếu Redis bị timeout/gián đoạn.
   - Toàn bộ suite test pass 100%.
