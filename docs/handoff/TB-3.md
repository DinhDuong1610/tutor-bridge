# TB-3 Khung backend

- **Chủ task:** Đính
- **Nhánh:** `feature/TB-3-backend-skeleton`, tách từ `feature/TB-1-monorepo-foundation` vì PR #25 chưa merge
- **Agent đang làm:** Claude
- **Cập nhật lần cuối:** 03/11/2026
- **Mã liên quan:** ADR-0001, CR-005, CR-009, Issue #3

## Mục tiêu
Khung Spring Boot, Flyway V1–V7, Testcontainers, test kiến trúc, tài khoản DB quyền tối thiểu.

## Trạng thái hiện tại
Đã xong phần việc, chưa push. Sau khi PR #25 merge: `git rebase --onto origin/dev feature/TB-1-monorepo-foundation`, rồi push và mở PR vào `dev`.

## Lệnh kiểm tra
```bash
cd backend && ./gradlew test   # cần Docker; mong đợi 12 test đạt
```

## Kết quả chạy gần nhất
- `./gradlew test` 03/11/2026: 12 đạt, 0 hỏng (ArchitectureTest 2, BaselineMigrationsTest 6, SchemaMigrationTest 4).
- Compose với `bootRun` profile `local`: Flyway áp dụng 7 migration bằng `tutorbridge_migrator`, ứng dụng khởi động bằng `tutorbridge_app`, 80 bảng.

## Quyết định đã chọn
- Gradle 9.8.2 Kotlin DSL, tự tải JDK 25 qua foojay. Spring Boot 4.1.2.
- Package gốc `com.tutorbridge`. Truy cập dữ liệu bằng Spring JDBC `JdbcClient`, không dùng JPA: giao dịch dựa nhiều vào SQL, khóa và ràng buộc DB.
- Hai tài khoản: `tutorbridge_migrator` sở hữu schema, không phải superuser; `tutorbridge_app` chỉ có quyền đọc ghi dữ liệu. Vai trò tạo ở `infra/db-init/01-roles.sh`, test dùng đúng script này.
- `vector` không phải extension trusted, nên superuser cài ở bước khởi tạo DB. Hệ thống thật cũng phải cài trước khi chạy V6.
- V1–V6 giữ nguyên từng byte, có `BaselineMigrationsTest`. Chúng tự có `BEGIN`/`COMMIT` nên file `.conf` đặt `executeInTransaction=false`.
- Lịch sử Flyway ở schema `public`, để extension vẫn nằm ở `public` như lần kiểm V1–V6.
- Flyway tắt mặc định, chỉ bật ở profile `local`. Cách chạy migration khi triển khai thật làm ở task triển khai.

## Câu hỏi còn mở
- Huy rà: dùng JDBC thay JPA, và V7 đi chung PR này thay vì PR hợp đồng riêng vì Issue #3 gộp chung.
- Có cấm `UPDATE`, `DELETE` ở tầng quyền với bảng chỉ thêm như `audit_events` không? Hiện `tutorbridge_app` có quyền DML đồng đều.
- UUIDv7 và công cụ kiểm hợp đồng vẫn TBD.

## Bước kế tiếp
Rebase khi PR #25 merge, mở PR, tự rà `review-pr`, cho Codex rà, xóa file này.
