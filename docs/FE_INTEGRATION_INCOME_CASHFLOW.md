# FE Integration Guide: Income/Cashflow Feature

## 1. Overview

Backend đã hỗ trợ:
- Upload photo với loại giao dịch: `INCOME` (tính tiền vào) hoặc `EXPENSE` (tính tiền ra).
- Query entries theo loại giao dịch.
- Cashflow summary: tổng tiền vào, tiền ra, và net (tiền vào - tiền ra) theo tháng.
- Budget vẫn chỉ áp dụng cho EXPENSE như cũ.

---

## 2. API Endpoints Mới/Thay Đổi

### POST /api/v1/photos
**Form field mới (optional):**
- `transactionType`: `INCOME` | `EXPENSE`
  - Default: `EXPENSE` nếu không gửi
  - Use case: khi user upload ảnh thu nhập (lương, thưởng, etc.)

**Example:**
```bash
curl -X POST http://localhost:8080/api/v1/photos \
  -H "Authorization: Bearer <token>" \
  -F "file=@receipt.jpg" \
  -F "amount=5000000" \
  -F "categoryId=<salary-category-id>" \
  -F "transactionType=INCOME"
```

---

### GET /api/v1/expense/entries?monthKey=202604&type=EXPENSE
**Query params:**
- `monthKey` (required): `yyyyMM` format
- `type` (optional): `INCOME` | `EXPENSE`
  - Default: `EXPENSE` nếu không gửi (backward-compat)

**Use cases:**
- `?monthKey=202604&type=EXPENSE` → lấy danh sách chi tiêu tháng 4/2026
- `?monthKey=202604&type=INCOME` → lấy danh sách tính tăng tháng 4/2026
- `?monthKey=202604` → lấy chi tiêu (default)

---

### GET /api/v1/expense/cashflow?monthKey=202604
**Query params:**
- `monthKey` (required): `yyyyMM` format

**Response:**
```json
{
  "monthKey": "202604",
  "totalIncome": 15000000,
  "totalExpense": 3000000,
  "netCashflow": 12000000,
  "budgetLimit": 5000000,
  "budgetRemaining": 2000000,
  "budgetUsedPct": 60,
  "budgetExceeded": false,
  "incomeByCategory": [
    {
      "categoryId": "uuid-salary",
      "categoryName": "Salary",
      "totalAmount": 15000000
    }
  ],
  "expenseByCategory": [
    {
      "categoryId": "uuid-food",
      "categoryName": "Food",
      "totalAmount": 1500000
    },
    {
      "categoryId": "uuid-transport",
      "categoryName": "Transport",
      "totalAmount": 1500000
    }
  ]
}
```

---

## 3. FE Implementation Checklist

### 3.1 Upload Photo (Change)
- [ ] Thêm field `transactionType` dropdown (INCOME / EXPENSE) khi user tạo photo.
- [ ] Default select `EXPENSE` (vì hầu hết dùng case tracking chi tiêu).
- [ ] Khi POST /photos, gửi thêm `transactionType` trong form data.
- [ ] Xử lý response như cũ (không thay đổi).

### 3.2 Entries List (Change)
- [ ] Thêm tab/filter: "Chi tiêu" / "Tính tăng" (hoặc "Expense" / "Income").
- [ ] Khi switch tab, gửi `?type=EXPENSE` hoặc `?type=INCOME` tới API.
- [ ] Xử lý pagination như cũ (vẫn là Page<>).

### 3.3 Summary View (Mới - Expense Tab)
**Nếu có màn "Expense Summary":**
- [ ] GET /expense/summary → hiển thị thông tin chi tiêu (tương tự cũ).
- [ ] Nếu có budget alert, dùng `budgetExceeded` để hiển thị cảnh báo.

### 3.4 Cashflow Dashboard (Mới)
**Nếu muốn có overview tương tự Locket:**
- [ ] Thêm màn hình mới: "Cashflow" hoặc "Overview" (tuỳ design).
- [ ] GET /expense/cashflow?monthKey=yyyyMM
- [ ] Hiển thị:
  - "Total Income": `totalIncome`
  - "Total Expense": `totalExpense`
  - "Net": `netCashflow`
  - Breakdown by category (income vs expense riêng biệt)
  - Budget status (nếu có)

---

## 4. Model/DTO Mapping

### Expense/Income Item (từ /expense/entries)
```dart
class TransactionItem {
  String id;            // photoId
  String imageUrl;
  String thumbnailUrl;
  BigDecimal amount;
  String? note;
  String? categoryId;
  String? categoryName;
  DateTime takenAt;
  DateTime createdAt;
}
```

### Cashflow Summary (từ /expense/cashflow)
```dart
class CashflowSummary {
  String monthKey;
  BigDecimal totalIncome;
  BigDecimal totalExpense;
  BigDecimal netCashflow;
  BigDecimal? budgetLimit;
  BigDecimal? budgetRemaining;
  int? budgetUsedPct;
  bool budgetExceeded;
  List<CategorySpend> incomeByCategory;
  List<CategorySpend> expenseByCategory;
}

class CategorySpend {
  String categoryId;
  String categoryName;
  BigDecimal totalAmount;
}
```

---

## 5. API Call Sequence (FE Perspective)

### Scenario 1: User mở màn Expense entries tháng hiện tại
```
1. GET /expense/entries?monthKey=202604&type=EXPENSE&page=0&size=20
2. Render danh sách transaction (chi tiêu)
3. Khi user click "Income tab":
   GET /expense/entries?monthKey=202604&type=INCOME&page=0&size=20
4. Render danh sách income transaction
```

### Scenario 2: User mở màn Cashflow overview
```
1. GET /expense/categories (cache nếu có)
2. GET /expense/cashflow?monthKey=202604
3. Render tổng tiền vào/ra + breakdown by category
4. Nếu có budget, hiển thị budget status từ response
```

### Scenario 3: User upload photo (income)
```
1. User chọn transactionType=INCOME
2. POST /api/v1/photos (multipart)
   - file: image data
   - amount: 5000000
   - categoryId: "uuid-salary"
   - transactionType: "INCOME"
3. Response: PhotoResponse (giống cũ)
4. Refresh entries list (gọi lại API entries)
```

---

## 6. Error Handling

### Error Case: Invalid transactionType
- API sẽ reject với `400 Bad Request` nếu `transactionType` không phải `INCOME` | `EXPENSE`.
- FE validate dropdown chỉ cho 2 option.

### Error Case: Empty month (no data)
- API trả `200 OK` với `totalIncome=0, totalExpense=0, netCashflow=0`.
- FE không cần xử lý 404, chỉ hiển thị "0" hoặc placeholder text.

### Error Case: Budget không set
- API trả `budgetLimit=null, budgetRemaining=null, budgetUsedPct=null`.
- FE check null trước khi hiển thị budget widget.

---

## 7. UI/UX Suggestions

### Transaction Type Selector (Upload Photo)
- Dropdown/Toggle: "Chi tiêu" (default) ↔ "Tính tăng"
- Icon: 🔴 (expense) vs 🟢 (income)
- Color coding: Red for expense, green for income

### Entries List Tabs
- Tab 1: "All Transactions" (filter=null → default EXPENSE)
- Tab 2: "Income" (filter=INCOME)
- Tab 3: "Expense" (filter=EXPENSE)
- Or simple 2-tab: "Chi tiêu" | "Tính tăng"

### Cashflow Card/Widget
```
┌────────────────────┐
│  Apr 2026          │
│                    │
│  💰 Tính tăng      │
│  15,000,000 VND    │
│                    │
│  💸 Chi tiêu       │
│  3,000,000 VND     │
│                    │
│  📊 Net Cashflow   │
│  +12,000,000 VND   │
└────────────────────┘
```

### Budget Status (still expense-only)
```
Budget this month: 5,000,000 VND
Spent: 3,000,000 VND (60%)
Remaining: 2,000,000 VND

[████████░░░░░░░░░░░░] 60%
```

---

## 8. Backward Compatibility

- **Old FE code** (không gửi `transactionType`):
  - POST /photos → backend default = EXPENSE ✓
  - GET /expense/entries (không param type) → backend default = EXPENSE ✓
  - GET /expense/summary → vẫn trả summary EXPENSE như cũ ✓

- **Upgrade path**:
  1. Deploy backend (V13 migration, new endpoints).
  2. FE add transaction type selector (optional at first).
  3. FE add income entries tab (optional).
  4. FE add cashflow view (optional).
  5. Rollout từng feature một, không cần hard cutoff.

---

## 9. Testing with Postman

### Collection: Expense/Cashflow Flow

**1. Create EXPENSE transaction**
```
POST /api/v1/photos
Form data:
  file: [image]
  amount: 50000
  categoryId: [expense-category-uuid]
  transactionType: EXPENSE
```

**2. Create INCOME transaction**
```
POST /api/v1/photos
Form data:
  file: [image]
  amount: 5000000
  categoryId: [income-category-uuid]
  transactionType: INCOME
```

**3. Get EXPENSE entries**
```
GET /api/v1/expense/entries?monthKey=202604&type=EXPENSE
```

**4. Get INCOME entries**
```
GET /api/v1/expense/entries?monthKey=202604&type=INCOME
```

**5. Get Cashflow Summary**
```
GET /api/v1/expense/cashflow?monthKey=202604
```

---

## 10. Known Limitations (v1)

- Category chưa strict-type (một category dùng cho cả income/expense).
- Budget chỉ tính expense, không có "income budget" hoặc "savings goal" riêng.
- Không có recurring transaction (mỗi giao dịch phải upload riêng).
- Không export report (chỉ xem online).

---

## 11. Next Steps

1. **Immediate**: FE integrate cashflow GET endpoint (simple view).
2. **Week 2**: Thêm transactionType selector khi upload.
3. **Week 3**: Thêm income tab vào entries list.
4. **Backlog**: Advanced features (recurring, transfer, forecast).

---

**Questions?** Refer to `/docs/API_CONTRACT.md` section 7 (Expense module) untuk chi tiết API.

