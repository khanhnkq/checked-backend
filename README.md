# checked-backend

Spring Boot backend cho `locket-clone`, dùng Gradle, Java 21 và có sẵn `Dockerfile` để build container image.

## CI/CD

Repo hiện có GitHub Actions cho:
- build + test + validate Docker build trên `pull_request` và `push` vào `main`
- release file JAR + publish Docker image lên GHCR khi push tag version dạng `v*.*.*`

Xem chi tiết tại `docs/CI_CD.md`.
