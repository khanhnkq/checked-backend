# Backend Features Completed Log

Cap nhat lan cuoi: 2026-04-24
Nguon doi chieu: `docs/API_CONTRACT.md`

## 1) Tinh trang tong quan

- Trang thai: backend da co day du cac module chinh cho Auth, User/Profile, Friendship, Photo, Reaction, Expense (co support Income/Cashflow).
- Muc tieu su dung: theo doi nhanh nhung gi da hoan thanh de FE tich hop.

## 2) Auth module (DONE)

Da hoan thanh:
- `POST /api/v1/auth/register`
- `POST /api/v1/auth/verify`
- `POST /api/v1/auth/login`

Ket qua:
- Register tra `RegisterResponse` + huong `VERIFY_OTP`.
- Verify OTP tra `JwtResponse`.
- Login tra `JwtResponse` (co `nextStep` de FE dieu huong onboarding/home).

## 3) User/Profile module (DONE)

Da hoan thanh:
- `GET /api/v1/users/me`
- `PATCH /api/v1/users/me/profile`
- `GET /api/v1/users/me/settings/personal-info`
- `PATCH /api/v1/users/me/settings/personal-info`
- `PATCH /api/v1/users/me/settings/avatar` (multipart file)
- `GET /api/v1/users/{id}`

Ket qua:
- Ho tro onboarding sau login.
- Ho tro sua thong tin ca nhan va upload avatar bang file.

## 4) Friendship module (DONE)

### 4.1 Friend invite link flow (DONE)
Da hoan thanh:
- `POST /api/v1/friend-invite-links`
- `GET /api/v1/friend-invite-links/current`
- `DELETE /api/v1/friend-invite-links/current`
- `POST /api/v1/friend-invite-links/accept`

Ket qua:
- Tao/rotate link moi.
- Lay link active hien tai.
- Revoke link active.
- Accept link tao friendship trang thai `ACCEPTED`.

### 4.2 Friend list (DONE)
Da hoan thanh:
- `GET /api/v1/friendships`

Ket qua:
- Tra danh sach ban be da accepted cho user hien tai.

## 5) Photo module (DONE)

Da hoan thanh:
- `POST /api/v1/photos` (multipart)
- `GET /api/v1/photos/feed` (Slice, co `friendId` filter)
- `GET /api/v1/photos/{photoId}`
- `GET /api/v1/photos/my-photos`
- `GET /api/v1/photos/me` (alias)
- `PATCH /api/v1/photos/{photoId}/expense`
- `PATCH /api/v1/photos/{photoId}/transaction` (full update)
- `DELETE /api/v1/photos/{photoId}` (soft delete)

Ket qua:
- Ho tro gui anh cho `ALL_FRIENDS` va `SELECTED_FRIENDS`.
- Feed dung `Slice` toi uu infinite scroll.
- Co cap nhat metadata chi tieu truc tiep tren photo.

## 6) Reaction module (DONE, co 1 muc can bo sung)

Da hoan thanh:
- `PUT /api/v1/photos/{photoId}/reactions/me`
- `DELETE /api/v1/photos/{photoId}/reactions/me`
- `GET /api/v1/photos/{photoId}/reactions/summary`

Ghi chu can bo sung:
- Contract mo ta `reactors` tra day du danh sach reaction, kem `avatarUrl`.
- DTO summary da tra tong quan (`photoId`, `totalCount`, `myReaction`, `countsByType`) va full `reactors`.
- Danh sach `reactors` duoc sap xep moi nhat truoc.

## 7) Expense module (DONE - WITH INCOME/CASHFLOW SUPPORT)

Da hoan thanh:
- `GET /api/v1/expense/categories`
- `POST /api/v1/expense/categories`
- `PATCH /api/v1/expense/categories/{categoryId}`
- `GET /api/v1/expense/budgets/{monthKey}`
- `PUT /api/v1/expense/budgets/{monthKey}`
- `GET /api/v1/expense/entries?monthKey=yyyyMM&type=EXPENSE|INCOME`
- `GET /api/v1/expense/entries?monthKey=yyyyMM&type=EXPENSE|INCOME|ALL`
- `GET /api/v1/expense/entries/by-period?period=DAY|WEEK|MONTH&referenceDate=yyyy-MM-dd&type=...`
- `GET /api/v1/expense/summary?monthKey=yyyyMM` (chi EXPENSE)
- `GET /api/v1/expense/cashflow?monthKey=yyyyMM` (INCOME + EXPENSE + NET)
- `GET /api/v1/expense/summary/yearly?year=yyyy`
- `GET /api/v1/expense/categories/top?...`
- `GET /api/v1/expense/savings-goals/{monthKey}`
- `PUT /api/v1/expense/savings-goals/{monthKey}`

Moi feature:
- Transaction type support: `EXPENSE` (mac dinh) hoac `INCOME`.
- Photo entity co `transactionType` field va `occurredAt` timestamp (tu `takenAt` fallback den `createdAt`).
- Category response/request co `transactionType` de FE phan loai danh muc thu/chi.
- Entries endpoint filter theo type (optional param `type`).
- Cashflow summary tra `totalIncome`, `totalExpense`, `netCashflow`, kem expense/income breakdown by category.
- Budget van chi dung cho EXPENSE (not affected bi income).
- Migration V13__add_transaction_type_support.sql da tao.

Ket qua:
- Ho tro category (default + user own).
- Ho tro budget theo thang (`monthKey` format `yyyyMM`).
- Ho tro entries va summary chi tieu theo thang.
- **MOI**: Ho tro entries va summary tien vao theo thang, va cashflow realtime.
- **MOI**: Category va expense item co `transactionType` de FE phan biet thu/chi ro hon.
- **MOI**: Ho tro list giao dich theo ngay/tuan/thang (`by-period`) cho UI list va bar chart.
- **MOI**: Ho tro thong ke nam, top category, va muc tieu tiet kiem theo thang.

## 8) Security scope (DONE)

Da ap dung:
- Public: register/verify/login.
- Protected: toan bo endpoint con lai (`users`, `photos`, `expense`, `friend-invite-links`, `friendships`).

## 9) Test coverage hien co

Da co test cho:
- Auth: controller + service
- User: controller + service
- Photo: controller + service
- Friendship: controller + service
- Friend invite link: controller + service

Can bo sung them:
- Test cho Expense controller/service de tang do tin cay regression.
- Test cho cashflow endpoints va transaction type logic.

## 10) Tinh trang san sang cho FE

- FE co the tich hop ngay cac flow:
  - Auth onboarding (register -> verify -> login -> complete profile)
  - Settings ca nhan + avatar upload
  - Friend invite link + danh sach ban
  - Upload photo/feed/photo detail/my photos
  - Reaction co ban (summary tra toi da 5 reactors moi nhat, kem avatar)
  - Expense categories/budget/entries/summary
  - **MOI**: Cashflow tien vao + tien ra, filter entries theo loai giao dich

- Muc can uu tien tiep theo:
  1. Hoan thien UI/FE cho top 5 `reactors` list va avatar trong reaction summary.
  2. Bo sung test cho Expense module (expense + income + cashflow).
  3. Test toan bo flow income/expense bang Postman (POST /photos voi transactionType, GET /cashflow).

## 11) Implementation notes for Income/Cashflow

- Migration: V13__add_transaction_type_support.sql them `transaction_type` + `occurredAt` vao photos va categories.
- API category: request/response da expose `transactionType`, va backend validate category type khop voi photo type khi gan category cho photo.
- Photo.PrePersist: auto-populate `occurredAt` tu `takenAt` neu chua co.
- Category field `transaction_type` da duoc dung de phan loai danh muc thu/chi va validate khi attach category vao photo.
- Budget van chi ap dung cho EXPENSE (tu, khong change business logic cu).
- Backward compatible: `type` param optional, default `EXPENSE` neu khong GUI.
- Queries dung COALESCE(p.occurredAt, p.takenAt, p.createdAt) de handle backfill old records.

## 12) Trang thai theo feature list quan ly chi tieu (2026-04-24)

Quy uoc:
- `DONE`: da co API/backend flow san sang cho FE.
- `PARTIAL`: da co mot phan, can them endpoint/aggregate/business rule.
- `NOT DONE`: chua co backend support ro rang trong contract hien tai.

| Nhom | Feature | Trang thai | Ghi chu |
|---|---|---|---|
| Chuc nang co ban | Them giao dich | DONE | `POST /api/v1/photos` ho tro `transactionType=EXPENSE|INCOME`. |
| Chuc nang co ban | Sua giao dich | DONE | Co `PATCH /api/v1/photos/{photoId}/transaction` de sua day du metadata giao dich. |
| Chuc nang co ban | Xoa giao dich | DONE | Co `DELETE /api/v1/photos/{photoId}` (soft delete). |
| Chuc nang co ban | Thu (luong, thuong...) | DONE | Ho tro `transactionType=INCOME`. |
| Chuc nang co ban | Chi (an uong, mua sam...) | DONE | Ho tro `transactionType=EXPENSE`. |
| Chuc nang co ban | Phan loai theo danh muc | DONE | Co category va `categoryId`; category co `transactionType` de tach thu/chi. |
| Chuc nang co ban | Danh sach giao dich theo thang | DONE | `GET /api/v1/expense/entries?monthKey=yyyyMM&type=...`. |
| Chuc nang co ban | Danh sach giao dich theo ngay/tuan | DONE | Co `GET /api/v1/expense/entries/by-period?period=DAY|WEEK|MONTH...`. |
| Chuc nang co ban | Xem tong thu-chi | DONE | `GET /api/v1/expense/cashflow` tra `totalIncome`, `totalExpense`, `netCashflow`. |
| Chuc nang co ban | So du hien tai | PARTIAL | Co `netCashflow` theo thang, chua co so du tong tich luy theo vi/tai khoan. |
| Thong ke & bao cao | Pie chart chi theo danh muc | DONE | Du lieu tu `summary.byCategory` hoac `cashflow.expenseByCategory`. |
| Thong ke & bao cao | Bar chart chi theo thoi gian | DONE | Co `entries/by-period` de FE nhom du lieu theo ngay/tuan/thang. |
| Thong ke & bao cao | So sanh thu vs chi | DONE | Co trong `cashflow` (`totalIncome` vs `totalExpense`). |
| Thong ke & bao cao | Thong ke theo thang | DONE | Ho tro bang `monthKey=yyyyMM`. |
| Thong ke & bao cao | Thong ke theo nam | DONE | Co `GET /api/v1/expense/summary/yearly?year=...`. |
| Thong ke & bao cao | Top danh muc tieu nhieu nhat | DONE | Co `GET /api/v1/expense/categories/top` (ho tro limit/type). |
| Quan ly muc tieu | Dat ngan sach thang | DONE | `PUT /api/v1/expense/budgets/{monthKey}`. |
| Quan ly muc tieu | Canh bao vuot ngan sach | PARTIAL | Co field `budgetExceeded`/`budgetUsedPct`; chua co co che canh bao chu dong (push/notification). |
| Quan ly muc tieu | Theo doi tien do tiet kiem | DONE | Co `savings-goals` endpoint (target/progress theo thang). |

De xuat uu tien tiep theo (backend):
1. Bo sung co che canh bao ngan sach chu dong (notification/job).
2. Bo sung so du tong tich luy theo vi/tai khoan (wallet/account module).
3. Bo sung yearly trends chi tiet hon (week/month chart endpoint aggregate san).
4. Mo rong savings goal: deadline/reminder/history.
