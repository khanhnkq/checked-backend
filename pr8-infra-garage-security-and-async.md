# Plan: PR #8 - Secure Garage S3 Admin Port, Protect Secrets & Configure Async ThreadPool TaskExecutor

## Goal
Đóng các cổng nhạy cảm của dịch vụ lưu trữ Garage S3 (Admin API port 3903 và Internal RPC port 3901) khỏi môi trường mạng công khai, loại bỏ các secret hardcode trong file cấu hình `garage.toml` chuyển sang biến môi trường an toàn, đồng thời thiết lập bộ điều phối luồng chuyên dụng `ThreadPoolTaskExecutor` cho cơ chế `@Async` gửi email để ngăn ngừa cạn kiệt luồng (Thread Starvation) và đảm bảo an toàn tài nguyên máy chủ.

---

## Root Cause & Phân Tích Kỹ Thuật

### 1. Phơi Bày Cổng Quản Trị Garage S3 & RPC Ra Public (CWE-284 / OWASP A05 - Security Misconfiguration)
- **Hiện trạng**: Trong [`compose.yaml`](compose.yaml#L22-L26):
  ```yaml
  ports:
    - '3900:3900' # S3 API
    - '3901:3901' # RPC
    - '3902:3902' # S3 Web (Public Image CDN)
    - '3903:3903' # Admin API
  ```
- **Rủi ro**:
  - Cổng **3903** là Admin API của Garage S3 (cho phép gọi API quản trị xem status, layout, tạo/xóa key, tạo/xóa bucket). Khi mở ra `0.0.0.0:3903`, bất kỳ ai trên Internet đều có thể tiếp cận endpoint quản trị này.
  - Cổng **3901** là cổng giao tiếp RPC nội bộ giữa các node trong cụm Garage cluster. Trong mô hình single-node (`replication_factor = 1`), việc mở cổng này ra Internet là hoàn toàn dư thừa và tiềm ẩn rủi ro tấn công vào giao thức RPC.
  - Container `garage-init` chạy trên cùng Docker network bridge với container `garage`. Do đó, `garage-init` có thể kết nối trực tiếp đến `garage:3901` và `garage:3903` thông qua Docker internal DNS mà **không cần** phải bind cổng ra máy chủ host.
- **Giải pháp**: Xóa bỏ hoàn toàn port mapping `3901:3901` và `3903:3903` khỏi `compose.yaml`. Chỉ giữ lại port `3900:3900` (S3 API cho backend) và `3902:3902` (Web CDN cho client tải ảnh).

### 2. Hardcode Secret Trong Cấu Hình Garage S3 (CWE-798 / OWASP A02 - Cryptographic Failures)
- **Hiện trạng**: Trong [`docker/garage/garage.toml`](docker/garage/garage.toml#L9-L22):
  ```toml
  rpc_secret = "b4a8e23f98234857ef129845cd782345ba892345ef129845cd782345ba892345"
  admin_token = "garage_admin_secret_token_change_in_production"
  ```
  Các chuỗi bí mật này bị commit trực tiếp vào git repository. Bất kỳ ai đọc được mã nguồn đều biết token quản trị mặc định.
- **Giải pháp**:
  - Garage hỗ trợ ghi đè cấu hình qua các biến môi trường chuẩn: `GARAGE_RPC_SECRET` và `GARAGE_ADMIN_TOKEN`.
  - Khai báo các biến này trong `compose.yaml` cho cả 2 service `garage` và `garage-init`.
  - Cập nhật file `.env.example` với hướng dẫn khởi tạo secret ngẫu nhiên (`openssl rand -hex 32` và `openssl rand -base64 32`).

### 3. Thiếu Quản Lý Thread Pool Cho `@Async` Email (CWE-400 / Thread Starvation DoS)
- **Hiện trạng**: Trong [`SmtpEmailService.java`](src/main/java/com/codegym/locketclone/notification/SmtpEmailService.java#L25) và [`LoggingEmailService.java`](src/main/java/com/codegym/locketclone/notification/LoggingEmailService.java#L11), phương thức `sendOtpEmail` được đánh dấu `@Async`. Tuy nhiên, trong toàn bộ mã nguồn không hề có cấu hình bean `Executor` nào.
- **Rủi ro**:
  - Mặc định, Spring Boot sử dụng `SimpleAsyncTaskExecutor` (hoặc executor mặc định không giới hạn). Mỗi khi có request gọi hàm async, một OS native thread mới sẽ được tạo ra mà không có giới hạn trần (`maxPoolSize`).
  - Mỗi thread JVM tiêu tốn khoảng 1MB bộ nhớ Stack ngoài Heap. Khi gặp đợt cao điểm hoặc bị spam request đăng ký tài khoản, việc tạo hàng nghìn thread đồng thời sẽ dẫn đến **Thread Starvation**, ngốn sạch CPU do liên tục context switching, và gây lỗi sập hệ thống `java.lang.OutOfMemoryError: unable to create new native thread`.
  - Không có cơ chế Graceful Shutdown: Khi ứng dụng restart hoặc redeploy, các tác vụ gửi email đang chờ trong bộ nhớ sẽ bị hủy đột ngột mà không kịp gửi tới người dùng.
- **Giải pháp**:
  - Xây dựng file cấu hình `AsyncConfig.java` triển khai `AsyncConfigurer` với bean `ThreadPoolTaskExecutor` chuyên dụng (`name = "mailTaskExecutor"`):
    - `corePoolSize = 4`: Đảm bảo 4 luồng xử lý sẵn sàng.
    - `maxPoolSize = 16`: Cho phép mở rộng tối đa 16 luồng khi chịu tải.
    - `queueCapacity = 200`: Hàng đợi chứa tối đa 200 tác vụ email chờ gửi.
    - `threadNamePrefix = "mail-exec-"`: Định danh rõ ràng trong log hệ thống.
    - `rejectedExecutionHandler = CallerRunsPolicy()`: Khi hàng đợi và pool đều đầy, tác vụ sẽ được thực thi trên thread gọi để tạo áp lực ngược (Backpressure) thay vì vứt bỏ email hoặc ném exception.
    - `waitForTasksToCompleteOnShutdown = true` và `awaitTerminationSeconds = 30`: Chờ tối đa 30 giây để hoàn thành các email đang gửi dở khi tắt server.
    - Cấu hình `AsyncUncaughtExceptionHandler` bắt và log lại các exception phát sinh trong tác vụ async `void`.

---

## Kiến Trúc & Thiết Kế Kỹ Thuật

```mermaid
flowchart TD
    subgraph HostEnvironment["Môi Trường Mạng Ngoài Host"]
        Browser["User Browser / Client"] -- "GET http://host:3902/locket-photos/* (Public CDN)" --> Port3902["Port 3902"]
        BackendApp["Spring Boot Backend"] -- "S3 Client http://localhost:3900 (API)" --> Port3900["Port 3900"]
        Attacker["Internet Attacker"] -- "x BỊ CHẶN (Không bind port) x" -.-> Port3903["Port 3903 (Admin)"]
    end

    subgraph DockerNetwork["Mạng Nội Bộ Docker Bridge"]
        Port3900 --> GarageDaemon["Garage Daemon Container (locket-clone-garage)"]
        Port3902 --> GarageDaemon
        GarageInit["Garage Init Container (locket-clone-garage-init)"] -- "garage:3901 (RPC)\ngarage:3903 (Admin)" --> GarageDaemon
        EnvVars["Docker Compose Environment\n- GARAGE_RPC_SECRET\n- GARAGE_ADMIN_TOKEN"] --> GarageDaemon
        EnvVars --> GarageInit
    end

    subgraph SpringAsync["Spring Boot Async Architecture"]
        RegisterCall["AuthServiceImpl#register"] --> AsyncProxy["@Async('mailTaskExecutor')"]
        AsyncProxy --> MailQueue["BlockingQueue (Capacity: 200)"]
        MailQueue --> WorkerThreads["ThreadPoolTaskExecutor\nCore: 4, Max: 16\nThread: mail-exec-*\nCallerRunsPolicy\nGraceful Shutdown: 30s"]
        WorkerThreads --> SmtpSender["JavaMailSender -> SMTP Server"]
    end
```

---

## Danh Sách Công Việc (Tasks)

- [x] **Task 1: Tạo nhánh Git `fix/infra-garage-security-and-async` từ `main`**
  - Chạy `git checkout -b fix/infra-garage-security-and-async` và kiểm tra trạng thái nhánh.

- [x] **Task 2: Đóng Cổng Quản Trị 3903 và Cổng RPC 3901 trong `compose.yaml`**
  - Xóa dòng `- '3901:3901'` và `- '3903:3903'` khỏi service `garage` trong `compose.yaml`.
  - Giữ lại cổng `3900:3900` (S3 API) và `3902:3902` (S3 Web Public CDN).
  - Kiểm tra cú pháp bằng lệnh `docker compose config`.

- [x] **Task 3: Chuyển Secret Garage Sang Biến Môi Trường**
  - Trong `compose.yaml`, bổ sung biến môi trường cho service `garage`:
    - `GARAGE_RPC_SECRET=${GARAGE_RPC_SECRET:-b4a8e23f98234857ef129845cd782345ba892345ef129845cd782345ba892345}`
    - `GARAGE_ADMIN_TOKEN=${GARAGE_ADMIN_TOKEN:-garage_admin_secret_token_change_in_production}`
  - Trong service `garage-init`, truyền cùng cặp biến môi trường `GARAGE_RPC_SECRET` và `GARAGE_ADMIN_TOKEN`.
  - Trong `docker/garage/garage.toml`, loại bỏ các giá trị secret cố định hoặc chú thích rõ ràng việc override qua biến môi trường.
  - Cập nhật `.env.example` khai báo hướng dẫn tạo và thiết lập `GARAGE_RPC_SECRET` và `GARAGE_ADMIN_TOKEN`.

- [x] **Task 4: Xây Dựng Cấu Hình `AsyncConfig.java`**
  - Tạo `src/main/java/com/codegym/locketclone/common/config/AsyncConfig.java` triển khai `AsyncConfigurer`:
    - Khởi tạo bean `ThreadPoolTaskExecutor` với tên `"mailTaskExecutor"`.
    - Thiết lập `corePoolSize = 4`, `maxPoolSize = 16`, `queueCapacity = 200`.
    - Thiết lập `threadNamePrefix = "mail-exec-"`.
    - Thiết lập `rejectedExecutionHandler = new ThreadPoolExecutor.CallerRunsPolicy()`.
    - Thiết lập `waitForTasksToCompleteOnShutdown = true` và `awaitTerminationSeconds = 30`.
    - Cung cấp `AsyncUncaughtExceptionHandler` log chi tiết lỗi khi có exception xảy ra trong luồng async.

- [x] **Task 5: Gắn Executor Định Danh Cho Dịch Vụ Gửi Email**
  - Cập nhật `SmtpEmailService.java`: Gắn `@Async("mailTaskExecutor")` lên phương thức `sendOtpEmail`.
  - Cập nhật `LoggingEmailService.java`: Gắn `@Async("mailTaskExecutor")` lên phương thức `sendOtpEmail`.

- [x] **Task 6: Viết Unit Test Kiểm Thử Cho `AsyncConfig` và `SmtpEmailService`**
  - Tạo `src/test/java/com/codegym/locketclone/common/config/AsyncConfigTest.java`:
    - Kiểm tra các thuộc tính của `mailTaskExecutor` bean (core pool size, max pool size, queue capacity, thread name prefix, rejection policy).
    - Kiểm tra `AsyncUncaughtExceptionHandler` xử lý lỗi mà không làm sập tiến trình.
  - Tạo `src/test/java/com/codegym/locketclone/notification/SmtpEmailServiceTest.java`:
    - Kiểm tra `sendOtpEmail` khởi tạo và gửi email đúng nội dung/tiêu đề qua `mailSender.send`.
    - Kiểm tra cơ chế try/catch log lỗi an toàn khi `mailSender.send` gặp sự cố mạng.
  - Tạo `src/test/java/com/codegym/locketclone/notification/LoggingEmailServiceTest.java`.

- [x] **Task 7: Chạy Toàn Bộ Kiểm Thử, Cập Nhật Roadmap, Commit & Merge Vào `main`**
  - Chạy `docker compose config` xác nhận cấu hình Docker hợp lệ và không còn cổng 3901, 3903 exposed.
  - Chạy `./gradlew clean test bootJar` đảm bảo 100% test suites pass.
  - Đánh dấu hoàn thành PR #8 trong `docs/PULL_REQUESTS_ROADMAP.md`.
  - Commit với thông điệp chuẩn mực: `fix(infra): secure garage ports, protect secrets and configure async thread pool`.
  - Merge vào `main`.

---

## Tiêu Chí Nghiệm Thu (Done When)

- [x] `compose.yaml` không còn bind cổng 3901 (RPC) và cổng 3903 (Admin) ra host.
- [x] Container `garage` và `garage-init` đọc `GARAGE_RPC_SECRET` và `GARAGE_ADMIN_TOKEN` từ biến môi trường.
- [x] File `.env.example` có đầy đủ các biến môi trường của Garage với hướng dẫn tạo mã an toàn.
- [x] Bean `mailTaskExecutor` được cấu hình chuẩn (`corePoolSize=4`, `maxPoolSize=16`, `queueCapacity=200`, `prefix=mail-exec-`).
- [x] Các tác vụ gửi email được thực thi trên thread pool `mailTaskExecutor` thay vì unmanaged default threads.
- [x] Có đầy đủ unit tests cho `AsyncConfig` và `SmtpEmailService`.
- [x] Toàn bộ test suite `./gradlew test` chạy pass 100% (153 tests passed).
- [x] Build `bootJar` thành công không có lỗi.
- [x] Code được merge sạch sẽ vào nhánh `main`.
