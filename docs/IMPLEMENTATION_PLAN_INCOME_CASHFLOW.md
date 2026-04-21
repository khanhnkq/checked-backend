# Implementation Plan: Income/Cashflow Support

## 1. Overview

Mở rộng module Expense từ "chi tiêu (EXPENSE) only" sang hỗ trợ cả "tính tiền vào (INCOME)", cho phép xem cashflow (tiền vào - tiền ra) theo tháng.

**Nguyên tắc thiết kế:**
- Backward compatible: photo EXPENSE mặc định nếu không gửi `transactionType`.
- Budget giữ nguyên áp dụng cho EXPENSE (không thay đổi logic cũ).
- Entries + Summary hỗ trợ filter theo type.

---

## 2. Database Changes

### Migration: `V13__add_transaction_type_support.sql`

**Thêm vào photos table:**
- `transaction_type VARCHAR(20)` (DEFAULT `EXPENSE`, check `IN ('INCOME', 'EXPENSE')`)
- `occurred_at TIMESTAMP` (NOT NULL, backfill từ `taken_at` hoặc `created_at`)

**Thêm vào categories table:**
- `transaction_type VARCHAR(20)` (DEFAULT `EXPENSE`)

**Index mới:**
- `idx_photos_sender_type_occurred` trên `(sender_id, transaction_type, occurred_at DESC)`
- `idx_categories_owner_type_active` trên `(user_id, transaction_type, is_active)`

**Backfill dữ liệu cũ:**
- Tất cả photo cũ → `transaction_type = 'EXPENSE'`
- `occurred_at` = COALESCE(`taken_at`, `created_at`)

---

## 3. Java Entity Changes

### Photo.java
```java
@Enumerated(EnumType.STRING)
@Column(name = "transaction_type", nullable = false, length = 20)
@Builder.Default
private TransactionType transactionType = TransactionType.EXPENSE;

@Column(name = "occurred_at", nullable = false)
private LocalDateTime occurredAt;

@PrePersist
protected void ensureOccurredAt() {
    if (this.occurredAt == null) {
        this.occurredAt = this.takenAt != null ? this.takenAt : LocalDateTime.now();
    }
}
```

### Category.java
```java
@Enumerated(EnumType.STRING)
@Column(name = "transaction_type", nullable = false, length = 20)
@Builder.Default
private TransactionType transactionType = TransactionType.EXPENSE;
```

### TransactionType.java (enum mới)
```java
public enum TransactionType {
    INCOME,
    EXPENSE
}
```

---

## 4. Repository Changes

### PhotoRepository - Thêm queries mới

**findTransactionPhotosBySenderAndMonth**
- Filter: `transaction_type = :transactionType`
- Sử dụng `COALESCE(p.occurredAt, p.takenAt, p.createdAt)` để backward-compat
- Trả `Page<Photo>`

**sumTransactionAmountBySenderAndMonth**
- SUM amount theo `transaction_type`
- Trả `BigDecimal`

**summarizeTransactionByCategory**
- GROUP BY category, filter theo `transaction_type`
- Trả `List<Object[]>` (categoryId, categoryName, totalAmount)

---

## 5. Service Layer Changes

### ExpenseService.java

Thêm phương thức:
```java
Page<ExpenseItemResponse> getExpenseEntries(UUID userId, String monthKey, TransactionType type, Pageable pageable);
CashflowSummaryResponse getCashflowSummary(UUID userId, String monthKey);
```

### ExpenseServiceImpl.java

**getExpenseEntries(userId, monthKey, pageable)**
- Gọi `getExpenseEntries(userId, monthKey, EXPENSE, pageable)` để giữ backward-compat

**getExpenseEntries(userId, monthKey, type, pageable)**
- Dùng query mới `findTransactionPhotosBySenderAndMonth` với `type`

**getCashflowSummary(userId, monthKey)**
- `totalIncome` = SUM(transaction_type=INCOME)
- `totalExpense` = SUM(transaction_type=EXPENSE)
- `netCashflow` = totalIncome - totalExpense
- `budgetRemaining` = budgetLimit - totalExpense (chỉ tính expense)
- `incomeByCategory` + `expenseByCategory` (breakdown riêng)
- Trả `CashflowSummaryResponse`

---

## 6. API Contract Changes

### POST /api/v1/photos
**Thêm param:**
- `transactionType` (optional): `INCOME | EXPENSE`, default `EXPENSE`

### GET /api/v1/expense/entries
**Thêm query param:**
- `type` (optional): `INCOME | EXPENSE`, default `EXPENSE`
- FE gửi `type=INCOME` để lấy danh sách tính tăng

### GET /api/v1/expense/cashflow (endpoint mới)
**Response:**
```json
{
  "monthKey": "202604",
  "totalIncome": 10000000,
  "totalExpense": 3000000,
  "netCashflow": 7000000,
  "budgetLimit": 5000000,
  "budgetRemaining": 2000000,
  "budgetUsedPct": 60,
  "budgetExceeded": false,
  "incomeByCategory": [
    {"categoryId": "uuid", "categoryName": "Salary", "totalAmount": 10000000}
  ],
  "expenseByCategory": [
    {"categoryId": "uuid", "categoryName": "Food", "totalAmount": 1500000}
  ]
}
```

---

## 7. DTO Changes

### CashflowSummaryResponse.java (tạo mới)
```java
public record CashflowSummaryResponse(
    String monthKey,
    BigDecimal totalIncome,
    BigDecimal totalExpense,
    BigDecimal netCashflow,
    BigDecimal budgetLimit,
    BigDecimal budgetRemaining,
    Integer budgetUsedPct,
    Boolean budgetExceeded,
    List<CategorySpendResponse> incomeByCategory,
    List<CategorySpendResponse> expenseByCategory
) {}
```

---

## 8. Controller Changes

### ExpenseController.java

```java
@GetMapping("/entries")
public ResponseEntity<Page<ExpenseItemResponse>> getExpenseEntries(
    @AuthenticationPrincipal UserPrincipal currentUser,
    @RequestParam String monthKey,
    @RequestParam(required = false) TransactionType type,
    @PageableDefault(size = 20) Pageable pageable
) {
    if (type == null) {
        return ResponseEntity.ok(expenseService.getExpenseEntries(currentUser.getId(), monthKey, pageable));
    }
    return ResponseEntity.ok(expenseService.getExpenseEntries(currentUser.getId(), monthKey, type, pageable));
}

@GetMapping("/cashflow")
public ResponseEntity<CashflowSummaryResponse> getCashflowSummary(
    @AuthenticationPrincipal UserPrincipal currentUser,
    @RequestParam String monthKey
) {
    return ResponseEntity.ok(expenseService.getCashflowSummary(currentUser.getId(), monthKey));
}
```

---

## 9. Rollout Plan

### Phase 1: DB Migration + Entity (ngày 1)
- Chạy migration V13__add_transaction_type_support.sql
- Cập nhật Photo, Category, TransactionType entity

### Phase 2: Repository + Service (ngày 2)
- Cập nhật PhotoRepository queries
- Cập nhật ExpenseService + ExpenseServiceImpl

### Phase 3: Controller + Contract (ngày 3)
- Cập nhật ExpenseController
- Cập nhật API_CONTRACT.md
- Cập nhật FEATURES_COMPLETED_LOG.md

### Phase 4: Test + Validation (ngày 4)
- Test migration backfill dữ liệu cũ
- Test expense/income queries
- Test cashflow calculation logic
- Postman test e2e flow

---

## 10. Test Cases (Must-have)

### Migration Test
- [ ] Backfill: tất cả photo cũ có `transaction_type='EXPENSE'`
- [ ] Backfill: `occurred_at` = COALESCE(`taken_at`, `created_at`)

### Service Test
- [ ] GET entries với `type=EXPENSE` → tỉnh ra chỉ EXPENSE
- [ ] GET entries với `type=INCOME` → chỉ INCOME
- [ ] GET entries mặc định (không param type) → EXPENSE
- [ ] GET cashflow tháng rỗng → 0 income/expense, netCashflow=0
- [ ] GET cashflow tháng có data → công thức net đúng
- [ ] Budget chỉ tính EXPENSE (không affected bởi INCOME)

### API Test (Postman)
- [ ] POST /photos với `transactionType=INCOME` → lưu đúng
- [ ] GET /expense/entries?monthKey=202604&type=INCOME → lấy đúng
- [ ] GET /expense/cashflow?monthKey=202604 → trả response đầy đủ

---

## 11. Backward Compatibility Notes

- **Photo cũ:** Tự động nhận `transactionType=EXPENSE` từ migration, không cần sửa code FE.
- **API cũ:** `GET /expense/entries` không param `type` vẫn trả EXPENSE (default).
- **Budget:** Logic cũ không thay đổi, chỉ dùng EXPENSE để tính.
- **Queries:** Dùng COALESCE fallback để handle NULL `occurred_at` từ old records.

---

## 12. Future Enhancements (Phase 2+)

- Category type enforcement: enforce INCOME category chỉ dùng cho INCOME photo, EXPENSE category chỉ cho EXPENSE photo.
- Recurring income/expense templates.
- Transfer between "accounts" (nếu có feature ví).
- Forecast/projection dựa trên trend.
- Export CSV/PDF report.
- Webhook/notification khi budget threshold bị vượt.

