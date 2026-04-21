# Backend Features Completed Log

Cap nhat lan cuoi: 2026-04-21
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
- Contract hien mo ta `reactors` (toi da 5 nguoi gan nhat, kem avatar).
- DTO hien tai dang tra tong quan (`photoId`, `totalCount`, `myReaction`, `countsByType`).
- Can bo sung danh sach `reactors` neu muon match 100% contract.

## 7) Expense module (DONE - WITH INCOME/CASHFLOW SUPPORT)

Da hoan thanh:
- `GET /api/v1/expense/categories`
- `POST /api/v1/expense/categories`
- `PATCH /api/v1/expense/categories/{categoryId}`
- `GET /api/v1/expense/budgets/{monthKey}`
- `PUT /api/v1/expense/budgets/{monthKey}`
- `GET /api/v1/expense/entries?monthKey=yyyyMM&type=EXPENSE|INCOME`
- `GET /api/v1/expense/summary?monthKey=yyyyMM` (chi EXPENSE)
- `GET /api/v1/expense/cashflow?monthKey=yyyyMM` (INCOME + EXPENSE + NET)

Moi feature:
- Transaction type support: `EXPENSE` (mac dinh) hoac `INCOME`.
- Photo entity co `transactionType` field va `occurredAt` timestamp (tu `takenAt` fallback den `createdAt`).
- Entries endpoint filter theo type (optional param `type`).
- Cashflow summary tra `totalIncome`, `totalExpense`, `netCashflow`, kem expense/income breakdown by category.
- Budget van chi dung cho EXPENSE (not affected bi income).
- Migration V13__add_transaction_type_support.sql da tao.

Ket qua:
- Ho tro category (default + user own).
- Ho tro budget theo thang (`monthKey` format `yyyyMM`).
- Ho tro entries va summary chi tieu theo thang.
- **MOI**: Ho tro entries va summary tien vao theo thang, va cashflow realtime.

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
  - Reaction co ban
  - Expense categories/budget/entries/summary
  - **MOI**: Cashflow tien vao + tien ra, filter entries theo loai giao dich

- Muc can uu tien tiep theo:
  1. Bo sung `reactors` (top 5, co avatar) cho reaction summary de dong bo contract.
  2. Bo sung test cho Expense module (expense + income + cashflow).
  3. Test toan bo flow income/expense bang Postman (POST /photos voi transactionType, GET /cashflow).

## 11) Implementation notes for Income/Cashflow

- Migration: V13__add_transaction_type_support.sql them `transaction_type` + `occurredAt` vao photos va categories.
- Photo.PrePersist: auto-populate `occurredAt` tu `takenAt` neu chua co.
- Category field `transaction_type` chung chua dung trong luc nay (de expand sau), hien tai category la untyped.
- Budget van chi ap dung cho EXPENSE (tu, khong change business logic cu).
- Backward compatible: `type` param optional, default `EXPENSE` neu khong GUI.
- Queries dung COALESCE(p.occurredAt, p.takenAt, p.createdAt) de handle backfill old records.

