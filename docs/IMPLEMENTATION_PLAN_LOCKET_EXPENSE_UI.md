# Ke hoach trien khai app Locket + Expense (co hoan thien UI/UX)

**Thoi gian:** 18/03/2026 -> 27/05/2026
**Pham vi:** Mot app hop nhat gom chia se anh kieu Locket + quan ly chi tieu

---

## 1) Muc tieu tong

- Hoan thien MVP co the release, gom 2 module chinh trong cung 1 app:
  - Social Photo: Auth, Profile, Friendship, Photo feed/detail
  - Expense Tracking: Category, Expense, Budget, Monthly report
- Dong bo FE-BE theo contract trong `docs/API_CONTRACT.md`.
- Hoan thien UI/UX den muc production-ready:
  - Design system
  - Day du trang thai loading/error/empty
  - Responsive
  - Accessibility co ban
  - Performance baseline

---

## 2) Nguyen tac thuc thi

- Nhip sprint theo tuan: **Plan -> Build -> Test -> Review**.
- Moi sprint bat buoc co:
  - User stories ro rang
  - API mapping/contract cap nhat
  - Test case + Postman test
  - Demo cuoi tuan
- Scope control:
  - Uu tien flow chinh truoc, nang cao de sau
  - Mọi thay doi contract phai duoc log trong `docs/API_CONTRACT.md`

---

## 3) Roadmap theo tuan (18/03/2026 -> 27/05/2026)

## W1 (18/03 - 24/03) - Discovery & Architecture

### Muc tieu
- Chot scope MVP va user journey tong the.

### Cong viec
- Chot flow: Register -> OTP verify -> Login -> Onboarding -> Home (Photo + Expense tab).
- Chot IA/navigation va FE architecture.
- Chot ERD tong quan va API gap list.
- Dinh nghia NFR ban dau (security, performance, reliability).

### Dau ra
- PRD v1, backlog v1, architecture note.
- Draft API contract v1.

### Done khi
- Team thong nhat scope, estimate, milestone.

---

## W2 (25/03 - 31/03) - Design System v1 + FE Foundation

### Muc tieu
- Dat nen UI/UX de code nhanh va dong nhat.

### Cong viec
- Tao design tokens: color, typography, spacing, radius, elevation.
- Build component core: button, input, card, modal, snackbar, app bar.
- Setup FE project structure, navigation, state management skeleton.
- Chuan hoa form validation + error presentation pattern.

### Dau ra
- UI kit v1.
- FE scaffolding san sang cho auth/photo/expense.

### Done khi
- Co the tao man hinh moi bang component reuse, khong hard-code style roi rac.

---

## W3 (01/04 - 07/04) - Auth + Profile UI/State

### Muc tieu
- Hoan tat luong account moi end-to-end.

### Cong viec
- FE screens: Register, Verify OTP, Login, Create/Update profile.
- Tich hop APIs auth/profile theo contract.
- Xu ly state day du: loading, wrong OTP, expired OTP, invalid token.
- Mapping error code BE -> UI message nhat quan.

### Dau ra
- Auth flow chay that voi backend, khong can mock.

### Done khi
- User moi co the hoan tat dang ky -> verify -> login -> onboarding profile.

---

## W4 (08/04 - 14/04) - Photo Capture & Upload

### Muc tieu
- Cho phep chup/gui anh on dinh.

### Cong viec
- FE screens: Camera/Picker, Preview, Upload progress.
- Tich hop `POST /api/v1/photos`, `GET /api/v1/photos/feed`.
- Xu ly camera permission, upload fail, retry.
- Chuẩn hoa thong bao khi khong co ban be accepted (neu policy cho gui van duoc).

### Dau ra
- Gui anh thanh cong va thay duoc trong feed.

### Done khi
- Flow chup -> upload -> feed chay on tren local/staging.

---

## W5 (15/04 - 21/04) - Feed/My Photos/Photo Detail

### Muc tieu
- Hoan thien trai nghiem doc anh.

### Cong viec
- FE screens: Feed, My Photos, Photo Detail.
- Infinite scroll/pagination + skeleton/loading.
- Tich hop `GET /api/v1/photos/{photoId}`.
- Hardening UI state cho empty feed/network timeout.

### Dau ra
- Trải nghiem feed/day-photo on dinh va de dung.

### Done khi
- Khong con blocker cho man hinh chi tiet anh.

---

## W6 (22/04 - 28/04) - Expense Core Screens

### Muc tieu
- Hoan tat quan ly chi tieu co ban.

### Cong viec
- FE screens: Category list/form, Expense list/form, Budget monthly.
- Tich hop CRUD category/expense + budget APIs.
- Chuan hoa month format `yyyyMM` de tranh sai contract.
- Validate amount/date/category tren client.

### Dau ra
- User co the tao/sua/xoa khoan chi va dat ngan sach thang.

### Done khi
- Expense core hoat dong end-to-end.

---

## W7 (29/04 - 05/05) - Expense Report + Link Photo-Expense

### Muc tieu
- Co bao cao thang va diem tich hop 2 module.

### Cong viec
- FE screen: Monthly report (totalSpent, totalBudget, percentUsed, category breakdown).
- Xu ly du lieu thang moi chua co expense (empty state dung, khong vo UI).
- Tich hop `PATCH /api/v1/photos/{photoId}/expense` (neu da co endpoint).
- Tao flow: chup anh -> gan expense -> thay trong report.

### Dau ra
- Bao cao thang su dung duoc, co lien ket photo-expense.

### Done khi
- Khong con loi "Khong the tai bao cao chi tieu" do null/404 mismatch.

---

## W8 (06/05 - 12/05) - UI/UX Completion Sprint

### Muc tieu
- Hoan thien giao dien nguoi dung o muc release-ready.

### Cong viec
- Ra soat toan bo loading/error/empty states cho tat ca screen.
- Responsive rules (mobile chinh; neu co tablet thi set breakpoints ro rang).
- Accessibility baseline: focus order, semantic labels, contrast.
- UX micro-improvements: retry action, pull-to-refresh, message clarity.

### Dau ra
- UI/UX checklist pass >= 95%.

### Done khi
- Khong con critical visual/usability issues.

---

## W9 (13/05 - 19/05) - Performance + Reliability + CI

### Muc tieu
- Dat quality gate ky thuat truoc release.

### Cong viec
- Toi uu image caching, list rendering, startup latency.
- Retry/backoff network strategy cho endpoint quan trong.
- Hoan thien Postman full flow Auth/Profile/Friendship/Photo/Expense.
- Dam bao CI pass: build + test + basic static checks.

### Dau ra
- Performance report + regression result.
- CI pipeline xanh on dinh.

### Done khi
- Dat performance budget va khong con bug nghiem trong chua triage.

---

## W10 (20/05 - 26/05) - UAT + Release Candidate

### Muc tieu
- Chot chat luong va tao RC.

### Cong viec
- UAT end-to-end theo checklist nghiep vu.
- Bug bash va fix P0/P1.
- Chot tai lieu: API contract final, runbook, known issues.
- Go/no-go review.

### Dau ra
- Release Candidate build.
- UAT report + decision log.

### Done khi
- Open Sev-1/Sev-2 = 0.

---

## W11 (27/05) - Launch Day

### Muc tieu
- Phat hanh v1.0 an toan.

### Cong viec
- Deploy production.
- Monitoring war-room 24h.
- Rollback checklist san sang.

### Dau ra
- v1.0 release + post-release report.

### Done khi
- He thong on dinh, metric chinh trong nguong cho phep.

---

## 4) Milestones

- **M1 (31/03):** Foundation done (scope + design system + FE architecture)
- **M2 (14/04):** Photo core done (camera/upload/feed)
- **M3 (05/05):** Expense core + report + photo-expense link done
- **M4 (19/05):** Quality gate done (UI/UX completion + perf + CI)
- **M5 (27/05):** Production launch

---

## 5) Definition of Done (DoD)

### Feature DoD
- Co thiet ke duoc duyet.
- Co API mapping ro rang.
- Co success + loading + error + empty states.
- Co test case toi thieu va demo duoc.

### FE DoD
- UI dung design token/component system.
- Responsive theo breakpoints da thong nhat.
- Accessibility baseline dat muc toi thieu.
- Khong con blocker visual bug.

### BE/Integration DoD
- Endpoint dung contract trong `docs/API_CONTRACT.md`.
- Error format nhat quan.
- Xu ly edge case/null-safe.

### Release DoD
- Sev-1/Sev-2 = 0.
- UAT pass >= 95%.
- PM + FE lead + BE lead sign-off.

---

## 6) KPI

- Sprint completion >= 85%.
- FE screen completion dung spec >= 95% truoc 12/05.
- FE-BE contract mismatch < 3 loi/sprint (tu W4 tro di).
- Crash-free sessions >= 99.5% truoc launch.
- UAT pass rate >= 95% truoc 27/05.

---

## 7) FE-BE Integration Checkpoints

1. **CP1 (W3):** Auth/Profile contract lock.
2. **CP2 (W4):** Photo upload/feed + permission + pagination schema.
3. **CP3 (W6):** Expense category/budget/report + month format `yyyyMM`.
4. **CP4 (W7):** Photo-expense update flow lock.
5. **CP5 (W10):** End-to-end regression va release contract final.

---

## 8) Rui ro chinh va giam thieu

1. **Scope tang nhanh (UI)**
   - Freeze design som (cuoi W2), change-control theo muc uu tien.

2. **Lech contract FE-BE**
   - Weekly contract review va cap nhat `docs/API_CONTRACT.md` bat buoc.

3. **Hieu nang feed anh kem**
   - Thumbnail-first, lazy loading, cache policy tu W5.

4. **Accessibility de cuoi ky**
   - Dua a11y vao DoD tung screen, khong de den W8 moi bat dau.

5. **Thieu data test/UAT**
   - Seed data + staging fixtures + fallback mock ngay tu W2.

---

## 9) Ban giao tai lieu

- `docs/API_CONTRACT.md` (contract final)
- `docs/CI_CD.md` (pipeline/deploy)
- `docs/IMPLEMENTATION_PLAN_LOCKET_EXPENSE_UI.md` (ke hoach nay)
- Postman collection full flow cho toan bo module

