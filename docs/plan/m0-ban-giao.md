# Bàn giao sau M0

Viết ngày 03/11/2026, khi M0 vào `dev`. Dành cho Huy khi vào repo, và cho mọi agent. Nội dung lấy từ các file handoff đã xóa của TB-1, TB-3, TB-4, TB-7, TB-2, TB-5; chi tiết từng phần ở PR #25 đến #29 và #31.

## Đã có trên `dev`
| Phần | Nội dung | PR |
|---|---|---|
| Nền repo | LF, `contracts/openapi.yaml`, PostgreSQL 17 + pgvector qua Docker Compose, khóa phiên bản | #25 |
| Backend | Spring Boot 4.1 trên Java 25, 6 mô-đun có test kiến trúc, Flyway V1–V8, hai tài khoản DB | #27 |
| Platform | Problem, traceId, receipt, outbox có lease, phân trang con trỏ | #28 |
| TX05 | Chốt lớp nguyên tử, test đồng thời qua hai bản ứng dụng | #29 |
| CI | `repo.yml`, `backend.yml`, Dependabot | #26 |
| Ứng dụng | API client sinh từ OpenAPI, khung Expo SDK 57, khung Next.js 16.4, `frontend.yml` | #31 |

Theo OP-14, đợt M0 là ngoại lệ: agent viết phần tác giả, merge không qua duyệt chéo, Huy không cần rà lại. **Từ PR sau, quy trình Git áp dụng đầy đủ:** người làm và người duyệt khác nhau, tác giả tự viết phần giải thích.

## Bắt đầu cho người mới vào repo
1. Clone, rồi chạy `git config core.hooksPath .githooks`.
2. Cài Docker, Node 26 và Python 3.14. JDK 25 không cần cài: Gradle tự tải lần đầu.
3. `cp .env.example .env`, rồi làm theo phần Lệnh trong `AGENTS.md`.
4. Đọc `AGENTS.md` và `docs/context/invariants.md`, rồi lấy Issue và chạy `start-task`.

## Quyết định cần chốt
Đây là những chỗ agent đã chọn tạm vì tài liệu chưa có, cần người có quyền xác nhận hoặc đổi.

| Việc | Hiện tại | Người chốt | Hạn |
|---|---|---|---|
| ADR-0007 khóa tài nguyên bằng khóa tư vấn | Đề xuất | Đính | Trước TB-15 |
| Danh mục mã lỗi | Mã tự đặt trong `platform` và `classes`, xem dưới | Đính, Huy | Trước TB-10 |
| Thiếu `Idempotency-Key` | Trả 422; hợp đồng chỉ nói 428 cho `If-Match` | Đính, Huy | Trước TB-08 |
| Quyền trên bảng chỉ thêm như `audit_events` | `tutorbridge_app` ghi đồng đều | Đính | Trước TB-23 |
| Outbox thử tối đa bao nhiêu lần, xử lý sự kiện lỗi mãi | Thử lại không giới hạn | Đính | TB-21 |
| Mục tiêu đầu của lớp | `EFFECTIVE`, vì gửi đề nghị là phụ huynh đã đồng ý (BR05) | Đính | Trước TB-19 |
| `PREFERRED` có tính là rảnh không | Có | Đính | Trước TB-15 |
| Công cụ kiểm hợp đồng (`contract-check`) | Huy chốt 04/11/2026: validator trong test tích hợp backend, cộng `oasdiff` trên CI. `oasdiff` đã có trong `repo.yml` | Huy | TB-08 |
| UUIDv7 | TBD, đang dùng UUIDv4 | Đính | Trước khi có dữ liệu thật |
| Tên gói Android, thư viện điều hướng | TBD; Expo đề xuất Expo Router | Huy | TB-10 |
| `lock_timeout` cho giao dịch | Chưa đặt | Đính | Khi có số đo tải |

Mã lỗi đang dùng: `IDEMPOTENCY_KEY_INVALID`, `IDEMPOTENCY_KEY_REUSED`, `CURSOR_INVALID`, `LIMIT_INVALID`, `VALIDATION_FAILED`, `REQUEST_REJECTED`, `INTERNAL_ERROR`, `OFFER_NOT_FOUND`, `ACCESS_UNAVAILABLE`, `SCOPE_CHANGED`, `OFFER_EXPIRED`, `VERSION_CONFLICT`, `SCHEDULE_CONFLICT`, `SERVICE_BUSY`.

## Ràng buộc cho task sau
- **TB-15, TB-18:** khóa theo cùng thứ tự với TX05: nhu cầu trước đề nghị, rồi khóa tài nguyên `STUDENT:<id>`, `TUTOR:<id>` theo ADR-0007.
- **Lệnh ghi:** dùng `CommandReceipts` và `Outbox` trong giao dịch của service; hai lớp này báo lỗi nếu gọi ngoài giao dịch.
- **Lỗi:** ném `ApiException`, không tự dựng phản hồi lỗi. Problem có đủ 6 trường bắt buộc của hợp đồng.
- **Test đồng thời:** dùng `Concurrently`, không dùng `CompletableFuture.supplyAsync` mặc định. Common pool trên runner ít lõi làm các lệnh chạy lần lượt.
- **Migration:** số tiếp theo là V9, Đính cấp trong Issue. V1–V6 bị khóa bằng `BaselineMigrationsTest`, migration đã merge bị khóa bằng `immutable-history`.
- **`vector`** không phải extension trusted: môi trường thật phải cài bằng superuser trước V6.
- **Mobile:** cài phụ thuộc bằng `npx expo install`. Mobile dùng TypeScript 6, web và client dùng 5.9 vì công cụ sinh client chưa hỗ trợ 6.

## PR của Dependabot
Dependabot mở PR cập nhật hằng tuần vào `dev`, không cần phần tác giả. Người duyệt kiểm CI và ghi chú phát hành, rồi squash merge. Ngày 04/11/2026 Huy đã merge #30, #33, #38 và đóng #34–#37 vì lệch Expo SDK 57 hoặc TypeScript đã khóa. `.github/dependabot.yml` bỏ qua các gói Expo quản lý và bản chính của TypeScript; nâng chúng trong task nâng SDK riêng.

## Giới hạn của bằng chứng hiện có
- TX05 mới kiểm ở tầng service, qua hai Spring context trong một JVM. Kiểm qua HTTP và hai tiến trình thuộc TB-19, TB-23.
- Chưa đo tải. Không được nói về năng lực chịu tải (INV-73).
- `migration-check` mới kiểm từ DB trống. Kiểm tra kiến trúc chưa phát hiện SQL đọc bảng của mô-đun khác.
- `npm audit` báo lỗ hổng trong công cụ dòng lệnh của Expo; sửa tự động sẽ hạ SDK nên chưa sửa. Theo dõi qua Dependabot.

## Việc tiếp theo
| Việc | Người |
|---|---|
| Thêm Huy làm collaborator (OP-13) | Đính |
| TB-06 diễn tập đổi agent: Claude làm dở, Codex làm tiếp, người kia review | Cả hai |
| TB-08 đăng ký, đăng nhập, phiên | Huy |
| TB-11 hồ sơ con | Đính |
