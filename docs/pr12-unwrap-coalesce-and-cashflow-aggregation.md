# Kế Hoạch Triển Khai Chi Tiết: PR #12

> **Tiêu đề PR**: `perf(query): unwrap-coalesce-indexes-and-cashflow-aggregation`  
> **Nhánh Git**: `perf/sql-unwrap-coalesce-indexes` (tạo từ nhánh `main`)  
> **Mức độ ưu tiên**: 🔴 **P0 (Critical - Khôi phục B-Tree Index & Giảm 50% Dashboard Query)**  
> **Phạm vi ảnh hưởng**: `photo`, `expense`, `test`  
> **Cam kết an toàn**: ❌ **KHÔNG DEPLOY HEROKU** (Mọi thay đổi chỉ thực thi và kiểm thử tại local)

---

## 1. 🔍 Phân Tích Hiện Trạng & Nguyên Nhân Gốc (Root Cause)

### 1.1. Hàm `COALESCE` vô hiệu hóa chỉ mục B-Tree Index trên PostgreSQL
- **Hiện trạng Database**:
  - Tại migration `V13__add_transaction_type_support.sql`:
    ```sql
    UPDATE photos SET occurred_at = COALESCE(taken_at, created_at) WHERE occurred_at IS NULL;
    ALTER TABLE photos ALTER COLUMN occurred_at SET NOT NULL;
    CREATE INDEX IF NOT EXISTS idx_photos_sender_type_occurred ON photos(sender_id, transaction_type, occurred_at DESC);
    ```
  - Cột `occurred_at` đã được đảm bảo ràng buộc **`NOT NULL`** ở tầng database và trong entity `Photo.java` (`@Column(name = "occurred_at", nullable = false)`).
  - Tầng nghiệp vụ [PhotoServiceImpl.java#createPhoto](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/java/com/codegym/locketclone/photo/PhotoServiceImpl.java) luôn khởi tạo giá trị cho `occurredAt`.
- **Vấn đề trong JPQL**:
  - Tại [PhotoRepository.java](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/java/com/codegym/locketclone/photo/PhotoRepository.java), tất cả các câu truy vấn thời gian giao dịch đều viết:
    ```sql
    WHERE ...
      AND COALESCE(p.occurredAt, p.takenAt, p.createdAt) >= :fromDate
      AND COALESCE(p.occurredAt, p.takenAt, p.createdAt) < :toDate
    ORDER BY COALESCE(p.occurredAt, p.takenAt, p.createdAt) DESC
    ```
  - **Cơ chế PostgreSQL**: Trong PostgreSQL, chỉ mục B-Tree `idx_photos_sender_type_occurred` được đánh trên giá trị nguyên bản của cột `occurred_at`. Khi biểu thức được bọc trong hàm `COALESCE(...)`, PostgreSQL **không thể sử dụng B-Tree Index Range Scan**. Thay vào đó, query planner buộc phải thực hiện:
    1. Quét tuần tự toàn bảng (**Sequential Scan**) hoặc
    2. Quét index `sender_id` rồi tính toán hàm `COALESCE` trên từng dòng trong RAM, sau đó thực hiện Sort trong bộ nhớ (**In-Memory Quicksort**).
  - Khi số lượng ảnh tăng lên hàng chục nghìn bản ghi, CPU database sẽ bị quá tải ở các thao tác Sort và Function Evaluation này.

### 1.2. Phát sinh 2 truy vấn tổng hợp riêng lẻ cho INCOME và EXPENSE
- Tại [ExpenseServiceImpl.java#L287-L295](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/java/com/codegym/locketclone/expense/ExpenseServiceImpl.java#L287-L295) (`getCashflowSummary`) và [ExpenseServiceImpl.java#L582-L587](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/java/com/codegym/locketclone/expense/ExpenseServiceImpl.java#L582-L587) (`currentSavedForMonth`):
  ```java
  // Truy vấn 1: Lấy tổng Thu nhập
  BigDecimal totalIncome = safeAmount(photoRepository.sumTransactionAmountBySenderAndMonth(
          userId, PhotoStatus.DELETED, TransactionType.INCOME, monthRange.fromDate(), monthRange.toDate()
  ));

  // Truy vấn 2: Lấy tổng Chi tiêu
  BigDecimal totalExpense = safeAmount(photoRepository.sumTransactionAmountBySenderAndMonth(
          userId, PhotoStatus.DELETED, TransactionType.EXPENSE, monthRange.fromDate(), monthRange.toDate()
  ));
  ```
  - Cùng một khoảng thời gian trong tháng và cùng một `sender_id`, hệ thống gửi **2 câu lệnh SQL SELECT SUM tuần tự**.
  - Đây là lãng phí tài nguyên mạng, hoàn toàn có thể gom thành 1 câu `GROUP BY p.transactionType`.

---

## 2. 💡 Thiết Kế Kỹ Thuật (Technical Design)

### 2.1. Loại bỏ `COALESCE` khỏi các câu query của `PhotoRepository`
Do `occurred_at` là trường bắt buộc và có giá trị cho 100% bản ghi ảnh giao dịch, ta tối ưu trực tiếp:
- **Từ**:
  ```sql
  AND COALESCE(p.occurredAt, p.takenAt, p.createdAt) >= :fromDate
  AND COALESCE(p.occurredAt, p.takenAt, p.createdAt) < :toDate
  ORDER BY COALESCE(p.occurredAt, p.takenAt, p.createdAt) DESC
  ```
- **Sang**:
  ```sql
  AND p.occurredAt >= :fromDate
  AND p.occurredAt < :toDate
  ORDER BY p.occurredAt DESC
  ```
- **Phạm vi áp dụng**:
  1. `findTransactionPhotosBySenderAndMonth`
  2. `sumTransactionAmountBySenderAndMonth`
  3. `summarizeTransactionByCategory`
  4. `findTransactionPhotosBySenderAndRange`
  5. `sumTransactionAmountBySenderInRange`
  6. `summarizeTransactionByCategoryInRange`
  7. `summarizeMonthlyTransactionsForYear`

### 2.2. Bổ sung query gộp `summarizeTransactionTotalsByTypeAndMonth`
Thêm câu query tổng hợp 1 lần trong `PhotoRepository`:
```java
@Query("""
        SELECT p.transactionType, COALESCE(SUM(p.amount), 0)
        FROM Photo p
        WHERE p.sender.id = :senderId
          AND p.status <> :deletedStatus
          AND p.amount IS NOT NULL
          AND p.amount > 0
          AND p.occurredAt >= :fromDate
          AND p.occurredAt < :toDate
        GROUP BY p.transactionType
        """)
List<Object[]> summarizeTransactionTotalsByTypeAndMonth(@Param("senderId") UUID senderId,
                                                        @Param("deletedStatus") PhotoStatus deletedStatus,
                                                        @Param("fromDate") LocalDateTime fromDate,
                                                        @Param("toDate") LocalDateTime toDate);
```

### 2.3. Tối ưu Service Layer (`ExpenseServiceImpl`)
Tạo hàm trợ năng chuyển đổi kết quả thành `Map<TransactionType, BigDecimal>`:
```java
private Map<TransactionType, BigDecimal> getMonthlyTotalsByType(UUID userId, LocalDateTime fromDate, LocalDateTime toDate) {
    List<Object[]> rows = photoRepository.summarizeTransactionTotalsByTypeAndMonth(
            userId, PhotoStatus.DELETED, fromDate, toDate
    );
    Map<TransactionType, BigDecimal> totals = new EnumMap<>(TransactionType.class);
    for (Object[] row : rows) {
        TransactionType type = (TransactionType) row[0];
        BigDecimal sum = (BigDecimal) row[1];
        totals.put(type, safeAmount(sum));
    }
    return totals;
}
```
Sử dụng trong cả `getCashflowSummary` và `currentSavedForMonth`.
- **Kết quả**: Mỗi lần tải Dashboard hoặc tính toán mục tiêu tiết kiệm, số lượng query giảm từ $2 \rightarrow 1$ (**tiết kiệm 50% số lần kết nối database**).

---

## 3. 📂 Chi Tiết Từng File Thay Đổi

1. **[src/main/java/com/codegym/locketclone/photo/PhotoRepository.java](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/java/com/codegym/locketclone/photo/PhotoRepository.java)**
   - Loại bỏ `COALESCE` trên 7 câu query tài chính.
   - Bổ sung phương thức `summarizeTransactionTotalsByTypeAndMonth`.
2. **[src/main/java/com/codegym/locketclone/expense/ExpenseServiceImpl.java](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/java/com/codegym/locketclone/expense/ExpenseServiceImpl.java)**
   - Tích hợp `summarizeTransactionTotalsByTypeAndMonth` vào `getCashflowSummary` và `currentSavedForMonth`.
3. **[src/test/java/com/codegym/locketclone/expense/ExpenseServiceImplTest.java](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/test/java/com/codegym/locketclone/expense/ExpenseServiceImplTest.java)**
   - Cập nhật mock setup cho phương thức `summarizeTransactionTotalsByTypeAndMonth`.
   - Bổ sung test case kiểm thử `getCashflowSummary` với luồng query gộp.

---

## 4. 🛠️ Quy Trình Triển Khai Từng Bước (Step-by-Step Execution)

1. **Bước 1**: Tạo nhánh tính năng từ `main`:
   ```bash
   git checkout main && git pull origin main
   git checkout -b perf/sql-unwrap-coalesce-indexes
   ```
2. **Bước 2**: Chỉnh sửa [PhotoRepository.java](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/java/com/codegym/locketclone/photo/PhotoRepository.java): bỏ `COALESCE` và thêm `summarizeTransactionTotalsByTypeAndMonth`.
3. **Bước 3**: Chỉnh sửa [ExpenseServiceImpl.java](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/main/java/com/codegym/locketclone/expense/ExpenseServiceImpl.java): thay thế 2 lời gọi query riêng rẽ bằng phương thức gộp.
4. **Bước 4**: Cập nhật bài kiểm thử trong [ExpenseServiceImplTest.java](file:///Users/nguyenkimquockhanh/Desktop/checked-backend/src/test/java/com/codegym/locketclone/expense/ExpenseServiceImplTest.java).
5. **Bước 5**: Chạy toàn bộ test suites và build ứng dụng:
   ```bash
   ./gradlew clean test bootJar
   ```
6. **Bước 6**: Commit chuẩn Conventional Commits, merge vào `main` tại local và push lên `origin/main`.

---

## 5. ✅ Tiêu Chí Nghiệm Thu & Test Checklist

- [x] Không còn biểu thức `COALESCE(p.occurredAt, p.takenAt, p.createdAt)` trong các câu query lọc theo thời gian của `PhotoRepository`.
- [x] Lệnh SQL phát sinh từ `PhotoRepository` cho phép PostgreSQL áp dụng B-Tree Index `idx_photos_sender_type_occurred`.
- [x] Hàm `getCashflowSummary` chỉ gọi database đúng 1 lần duy nhất cho việc tổng hợp Thu nhập & Chi tiêu.
- [x] Hàm `currentSavedForMonth` (dùng trong Savings Goal) tính toán số tiền tiết kiệm lũy kế chính xác.
- [x] Toàn bộ test suites pass 100%, bản build `bootJar` thành công.
- [x] **Cam kết**: Tuyệt đối không thực thi deploy lên Heroku.
