# Postman flow cho auth email/OTP

## Files
- `locket-clone-auth-flow.postman_collection.json`
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

