# TB-7 TX05 chốt lớp

- **Chủ task:** Đính
- **Nhánh:** `feature/TB-7-tx05-accept-offer`, tách từ `feature/TB-4-platform-foundation`
- **Agent đang làm:** Claude
- **Cập nhật lần cuối:** 03/11/2026
- **Mã liên quan:** UC06, AC-UC06-01 đến 04, TX05, BR04–BR06, INV-10 đến 14, INV-16 đến 18, INV-20 đến 25, Issue #7

## Mục tiêu
TX05 ở tầng service, chứng minh đúng khi đồng thời qua hai bản ứng dụng. Nối HTTP thuộc TB-19.

## Thiết kế giao dịch
**Thứ tự khóa (INV-16):**
1. Receipt, qua unique index
2. `learning_needs` rồi `class_offers`, `FOR UPDATE`
3. Tài nguyên: khóa tư vấn theo giao dịch, sắp theo khóa `STUDENT:<id>`, `TUTOR:<id>` (ADR-0007)
4. Dữ liệu phụ: tài khoản, hồ sơ `FOR SHARE`

**Ranh giới:** một giao dịch, không có I/O ngoài (INV-18). Thông báo đi qua outbox.

**Kiểm lại trong giao dịch:** trạng thái, hạn theo `clock_timestamp()` sau khi có khóa, phiên bản đề nghị, phiên bản nhu cầu, tài khoản, hồ sơ, giá, cả chuỗi buổi trên lịch hiệu lực của hai bên.

**Cùng một commit:** lớp, mục tiêu đầu, mọi buổi, hai booking mỗi buổi, đề nghị được nhận, đề nghị cạnh tranh, đóng nhu cầu, outbox, receipt.

| Cách thất bại | Kết quả |
|---|---|
| Không phải gia sư đích, không có đề nghị | 404 `OFFER_NOT_FOUND`, che sự tồn tại |
| Tài khoản gia sư không hoạt động | 403 `ACCESS_UNAVAILABLE` |
| Đề nghị đã kết thúc | 409 `SCOPE_CHANGED` |
| Đã hết hạn | 409 `OFFER_EXPIRED` |
| `If-Match` cũ | 412 `VERSION_CONFLICT` |
| Nhu cầu, tài khoản, hồ sơ, giá, lịch hiệu lực đổi | 409 `SCOPE_CHANGED` |
| Buổi ngoài lịch rảnh hoặc trùng ngoại lệ | 409 `SCHEDULE_CONFLICT` |
| Trùng booking `23P01` | 409 `SCHEDULE_CONFLICT`, rollback toàn bộ |
| Trùng unique `23505` | 409 `SCOPE_CHANGED`, rollback toàn bộ |
| Deadlock, serialization | Thử lại tối đa 3 lần, rồi 503 `SERVICE_BUSY` |
| Cùng khóa, cùng nội dung | Kết quả cũ, sau khi kiểm lại quyền |
| Cùng khóa, khác nội dung | 409 `IDEMPOTENCY_KEY_REUSED` |

## Quyết định đã chọn
- Mục tiêu đầu ở trạng thái `EFFECTIVE`: phụ huynh đồng ý từ lúc gửi (BR05), nên `parent_approved_at = sent_at`, `tutor_approved_at` là lúc nhận, `proposed_by` là phụ huynh.
- `AVAILABLE` và `PREFERRED` đều tính là rảnh. Xung đột lịch rollback nên không để lại receipt; gửi lại sẽ được đánh giá lại.
- V8 chuyển `sync_session_bookings` sang `SECURITY DEFINER` và thu quyền ghi `resource_bookings`, đúng ghi chú trong V2. V7 đã cấp thừa quyền này.

## Ràng buộc cho task sau
- TB-15 đổi lịch và TB-18 từ chối, hủy, hết hạn phải khóa theo cùng thứ tự: nhu cầu trước đề nghị, và cùng khóa tài nguyên.
- `lock_timeout` chưa đặt. Cần quyết định khi có số đo tải.
- Chưa ghi `audit_events`: TX05 không yêu cầu; xem lại ở TB-23.

## Trạng thái hiện tại
Đã xong phần việc, chưa push. Mở PR sau khi TB-4 vào `dev`.

## Lệnh kiểm tra
```bash
cd backend && ./gradlew test   # cần Docker; mong đợi 66 test đạt
```

## Kết quả chạy gần nhất
- `./gradlew test --rerun` 03/11/2026: 66 đạt, 0 hỏng. Test đồng thời chạy lặp 11 lần, không chập chờn.
- Hai bản ứng dụng là hai Spring context, hai connection pool, trong cùng một JVM, gọi service. Chưa phải hai tiến trình, chưa qua HTTP: phần đó ở TB-19, TB-23.
- Đồng thời có bằng chứng chồng nhau: test giữ một dòng tới khi `pg_stat_activity` thấy cả hai lệnh đang chờ khóa.
- Cố tình phá: bỏ ánh xạ `23P01`, thử lại deadlock, kiểm hạn, kiểm lịch, kiểm phiên bản, hủy đề nghị cạnh tranh. Mỗi lần đều có test hỏng.
- Bỏ khóa tài nguyên: test vẫn đạt, vì exclusion constraint vẫn chặn. Đây là kết quả mong đợi của ADR-0007.

## Bước kế tiếp
Mở PR sau TB-4, tự rà `review-pr`, cho Codex rà, Huy đọc từng dòng, xóa file này.
