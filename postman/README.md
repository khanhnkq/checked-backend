# Postman flow cho auth email/OTP

## Files
- `locket-clone-auth-flow.postman_collection.json`
- `locket-clone-expense-flow.postman_collection.json`
- `locket-clone-local.postman_environment.json`

## Biến mặc định
- email: `khanhnguyenkim30825@gmail.com`
- username: `khanhnguyenkim30825`
- password: `SnapWidget@123`

## Thứ tự chạy
1. `1. Register`
2. Mở email `khanhnguyenkim30825@gmail.com`, lấy OTP 6 số
3. Điền OTP vào biến `otp`
4. `2. Verify OTP`
5. `4. Get Current User`
6. `5. Complete Profile`
7. `6. Get Current User After Profile Update`
8. Nếu muốn test đăng nhập lại, chạy `3. Login`

## Ghi chú
- Request `Verify OTP` và `Login` sẽ tự lưu `token` vào collection variable.
- Nếu `Register` trả 400 vì email/username đã tồn tại, đổi `username` hoặc dùng account mới rồi chạy lại.
- Nếu `Login` trả 403, nghĩa là account chưa verify OTP.

---

# Postman flow cho Expense module

## Collection
- `locket-clone-expense-flow.postman_collection.json`

## Endpoints duoc cover
- `GET /api/v1/expense/categories`
- `POST /api/v1/expense/categories`
- `PATCH /api/v1/expense/categories/{categoryId}`
- `PUT /api/v1/expense/budgets/{monthKey}`
- `GET /api/v1/expense/budgets/{monthKey}`
- `GET /api/v1/expense/entries?monthKey=...`
- `GET /api/v1/expense/summary?monthKey=...`
- `PATCH /api/v1/photos/{photoId}/expense`

## Thu tu chay de test full flow
1. `1. Login`
2. `2. Get Categories`
3. `3. Create Custom Category`
4. `4. Update Custom Category`
5. `5. Upsert Monthly Budget`
6. `6. Get Monthly Budget`
7. `7. Get My Photos (for photoId)`
8. `8. Patch Photo Expense`
9. `9. Get Expense Entries`
10. `10. Get Expense Summary`

## Luu y bien
- `monthKey`: dinh dang `yyyyMM`, vi du `202603`
- `photoId`: request 7 se tu dong set tu anh dau tien trong `/photos/me`
- `expenseCategoryId`: request 2 (hoac 3) se tu dong set

