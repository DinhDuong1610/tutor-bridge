# ADR-0010 Nền backend: tầng web dùng chung và khung xác thực

**Trạng thái:** Đã chọn (Đính, 06/11/2026), gồm cả cơ chế CSRF ở mục 3 (duyệt sau khi xem B2). Mục 1 chạm TB-08 nên cần Huy đồng ý. **Chủ trì:** Đính (CR-012).

## Vấn đề
Backend đã có Problem, receipt, outbox, phân trang (TB-04) nhưng chưa có controller nào. Mỗi Issue backend sắp tới sẽ cần cùng một bộ: đọc `Idempotency-Key` và `If-Match`, đặt `Cache-Control`, biết ai đang gọi, chặn khi chưa đăng nhập. Nếu Issue đầu tiên tự dựng, Issue sau phải chờ hoặc dựng lại theo kiểu khác.

## Quyết định
1. **Khung xác thực do nền dựng, TB-08 nối phần tra phiên thật** (Đính). Nền định nghĩa trong `platform`:
   - `Actor`: tài khoản, vai trò, phiên đang gọi. Service nhận `Actor` làm tham số, không tự đọc request.
   - Giao diện `SessionResolver`: từ token trả về `Actor` hoặc không có. TB-08 viết bản tra `auth_sessions` và `auth_epoch` (ADR-0004) trong mô-đun `identity`.
   - Bộ lọc đọc `Authorization: Bearer <token>`, gọi `SessionResolver`, trả 401 dạng Problem khi thao tác cần phiên mà không có.
   - Trong test có helper giả lập `Actor` để Issue khác test được trước khi TB-08 merge.
2. **Dùng Spring Security, cấu hình tối thiểu** (Đính). Spring Security chỉ lo xác thực, chặn khi chưa đăng nhập, header bảo mật, 401 và 403 dạng Problem. Cấu hình: không tạo session máy chủ, tắt form login và HTTP Basic; CSRF chỉ bật cho lệnh ghi xác thực bằng cookie (mục 3). Bộ lọc xác thực ở mục 1 là một bộ lọc trong chuỗi của Spring Security. Controller lấy `Actor` qua `@AuthenticationPrincipal` rồi truyền xuống service. Phân quyền theo quan hệ (phụ huynh và con, gia sư và lớp) vẫn kiểm trong service bằng truy vấn DB, không dùng `@PreAuthorize` cho việc này.
3. **API nhận cả bearer và cookie, theo hợp đồng** (Đính chọn giữ hợp đồng, 06/11/2026). `contracts/openapi.yaml` khai báo `bearerAuth` cho mobile và `cookieAuth` (cookie `tb_session`, Secure, HttpOnly) cho web quản trị (ADR-0004). Bộ lọc xác thực đọc bearer trước, không có thì đọc cookie; cả hai đi qua cùng `SessionResolver`. Yêu cầu xác thực bằng cookie mà thay đổi dữ liệu (không phải GET, HEAD, OPTIONS) phải qua kiểm CSRF và Origin; yêu cầu bằng bearer không cần, vì trình duyệt không tự gửi bearer. Cơ chế CSRF do agent đề xuất, Đính duyệt ngày 06/11/2026:
   - CSRF của Spring Security theo kiểu double-submit: cookie `tb_csrf` không HttpOnly, client gửi lại giá trị trong header `X-CSRF-Token`.
   - Origin, hoặc Referer khi thiếu Origin, phải nằm trong danh sách nguồn web quản trị lấy từ cấu hình; thiếu cả hai thì từ chối.
   - Vi phạm trả 403 dạng Problem với mã `CSRF_REJECTED`.
4. **Tầng web dùng chung trong `platform/web`:**
   - Đọc và kiểm `Idempotency-Key` (16–128 ký tự), `If-Match`, `If-None-Match: *`; sinh `ETag` từ số phiên bản.
   - Mặc định `Cache-Control: private, no-store` cho phản hồi có dữ liệu riêng.
   - JSON từ chối thuộc tính lạ, thời điểm dạng ISO-8601 UTC.
   - Lỗi validate của Bean Validation ánh xạ về Problem `VALIDATION_FAILED` tại `ProblemHandler`.
5. **Test:** lớp cơ sở cho test HTTP trên PostgreSQL thật, fixture tạo dữ liệu theo mô-đun, kiểm phản hồi khớp `contracts/openapi.yaml` bằng `Contract.matches()`, dựng trên `com.networknt:json-schema-validator` vì hợp đồng là OpenAPI 3.1 (JSON Schema 2020-12); agent chọn ở B3, Đính duyệt ngày 06/11/2026.
6. **Vận hành local:** health check qua Actuator, log JSON có `traceId`, che token, mật khẩu, email trong log; `Clock` và sinh ID tiêm qua bean để test được; dữ liệu seed giả chỉ chạy ở profile `local`.

## Vì sao không chọn phương án khác
| Phương án | Lý do bỏ |
|---|---|
| Để TB-08 dựng cả khung | Issue backend khác phải chờ TB-08 merge mới test được quyền |
| Bộ lọc servlet tự viết, không Spring Security | Ít code hơn nhưng phải tự làm thứ tự bộ lọc, chặn đường dẫn lạ, header bảo mật, xử lý 401 và 403, đánh dấu thao tác cần quyền quản trị hoặc đã qua MFA (TB-09, TB-13). Phần nhạy cảm nhất lại là phần tự viết, trong khi phần lớn code do agent viết |
| API chỉ nhận bearer, cookie và CSRF đặt ở máy chủ Next | Lệch hợp đồng (`cookieAuth`, design 10 "lệnh dùng cookie phải kiểm CSRF và Origin"). Đính chọn giữ hợp đồng thay vì sửa nó |

## Hệ quả
- TB-08 không dựng bộ lọc riêng; viết `SessionResolver` trong `identity` và test với phiên thật.
- Thêm phụ thuộc `spring-boot-starter-security`. Test HTTP cần cấu hình bảo mật; helper giả lập `Actor` gói phần này lại.
- Không dùng `SecurityContextHolder` trong service; chỉ controller đọc người dùng hiện tại.
- Mỗi yêu cầu vẫn tra phiên một lần (ADR-0004).
