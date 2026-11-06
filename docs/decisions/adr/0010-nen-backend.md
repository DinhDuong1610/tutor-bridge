# ADR-0010 Nền backend: tầng web dùng chung và khung xác thực

**Trạng thái:** Mục 1 đã chọn (Đính, 06/11/2026). Các mục còn lại là đề xuất của agent, chờ Đính chốt; mục 2 cần Huy đồng ý vì chạm TB-08. **Chủ trì:** Đính (CR-012).

## Vấn đề
Backend đã có Problem, receipt, outbox, phân trang (TB-04) nhưng chưa có controller nào. Mỗi Issue backend sắp tới sẽ cần cùng một bộ: đọc `Idempotency-Key` và `If-Match`, đặt `Cache-Control`, biết ai đang gọi, chặn khi chưa đăng nhập. Nếu Issue đầu tiên tự dựng, Issue sau phải chờ hoặc dựng lại theo kiểu khác.

## Quyết định
1. **Khung xác thực do nền dựng, TB-08 nối phần tra phiên thật** (Đính). Nền định nghĩa trong `platform`:
   - `Actor`: tài khoản, vai trò, phiên đang gọi. Service nhận `Actor` làm tham số, không tự đọc request.
   - Giao diện `SessionResolver`: từ token trả về `Actor` hoặc không có. TB-08 viết bản tra `auth_sessions` và `auth_epoch` (ADR-0004) trong mô-đun `identity`.
   - Bộ lọc đọc `Authorization: Bearer <token>`, gọi `SessionResolver`, trả 401 dạng Problem khi thao tác cần phiên mà không có.
   - Trong test có helper giả lập `Actor` để Issue khác test được trước khi TB-08 merge.
2. **Không dùng Spring Security ở Lát 1** (đề xuất). Phiên opaque tra DB, không cần OAuth hay form login. Một bộ lọc servlet và kiểm quyền trong service là đủ, và dễ đọc hơn khi review.
3. **API chỉ nhận bearer token** (đề xuất). Cookie Secure, HttpOnly và CSRF của web quản trị (ADR-0004) đặt ở máy chủ Next: trình duyệt giữ cookie với Next, Next gọi API bằng bearer phía máy chủ. API không cần CSRF.
4. **Tầng web dùng chung trong `platform/web`** (đề xuất):
   - Đọc và kiểm `Idempotency-Key` (16–128 ký tự), `If-Match`, `If-None-Match: *`; sinh `ETag` từ số phiên bản.
   - Mặc định `Cache-Control: private, no-store` cho phản hồi có dữ liệu riêng.
   - JSON từ chối thuộc tính lạ, thời điểm dạng ISO-8601 UTC.
   - Lỗi validate của Bean Validation ánh xạ về Problem `VALIDATION_FAILED` tại `ProblemHandler`.
5. **Test** (đề xuất): lớp cơ sở cho test HTTP trên PostgreSQL thật, fixture tạo dữ liệu theo mô-đun, kiểm phản hồi khớp `contracts/openapi.yaml` bằng trình kiểm theo OpenAPI, tên công cụ chốt khi làm module B3.
6. **Vận hành local** (đề xuất): health check qua Actuator, log JSON có `traceId`, che token, mật khẩu, email trong log; `Clock` và sinh ID tiêm qua bean để test được; dữ liệu seed giả chỉ chạy ở profile `local`.

## Vì sao không chọn phương án khác
| Phương án | Lý do bỏ |
|---|---|
| Để TB-08 dựng cả khung | Issue backend khác phải chờ TB-08 merge mới test được quyền |
| Spring Security | Nhiều cấu hình mặc định cần tắt; lợi ích chính (form login, OAuth, CSRF cho phiên cookie) không dùng tới ở API |
| Cookie thẳng tới API cho web | API phải xử lý CSRF và hai kiểu xác thực |

## Hệ quả
- TB-08 không dựng bộ lọc riêng; viết `SessionResolver` trong `identity` và test với phiên thật.
- Nếu sau này cần OAuth hoặc SSO quản trị, xét lại mục 2.
- Mỗi yêu cầu vẫn tra phiên một lần (ADR-0004).
