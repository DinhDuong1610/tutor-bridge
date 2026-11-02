# TB-4 Platform

- **Chủ task:** Đính
- **Nhánh:** `feature/TB-4-platform-foundation`, tách từ `feature/TB-3-backend-skeleton` vì PR #25 chưa merge
- **Agent đang làm:** Claude
- **Cập nhật lần cuối:** 03/11/2026
- **Mã liên quan:** INV-20, INV-21, INV-25, INV-26, INV-80, INV-81, ADR-0003, Issue #4

## Mục tiêu
Phần dùng chung cho mọi lệnh: Problem, traceId, receipt, outbox, phân trang con trỏ.

## Trạng thái hiện tại
Đã xong phần việc, chưa push. Mở PR sau khi TB-3 vào `dev`.

## Lệnh kiểm tra
```bash
cd backend && ./gradlew test   # cần Docker; mong đợi 42 test đạt
```

## Kết quả chạy gần nhất
- `./gradlew test` 03/11/2026: 42 đạt, 0 hỏng.
- Cố tình phá để kiểm test: bỏ `SKIP LOCKED`, bỏ fencing, bỏ `MANDATORY`. Mỗi lần đúng một test liên quan hỏng.

## Quyết định đã chọn
- `CommandReceipts`, `Outbox.publish` dùng `Propagation.MANDATORY`: gọi ngoài giao dịch là lỗi, nên không thể quên INV-21.
- Bản sao gửi đồng thời chờ trên unique index, rồi trả lại kết quả, hoặc chạy mới nếu lệnh đầu rollback.
- `attempts` làm fencing token cho outbox: worker mất lease không hoàn tất được sự kiện.
- traceId do máy chủ sinh, không đọc từ client, không thêm header ngoài hợp đồng.
- Cursor gồm thời điểm micro giây và id, không ký: quyền nằm trong truy vấn danh sách.
- `Problem` là record riêng vì hợp đồng cấm thuộc tính ngoài danh sách. `type` dạng `urn:tutorbridge:problem:<CODE>`.

## Câu hỏi còn mở
- Tài liệu chưa có danh mục mã lỗi. Mã mới: `IDEMPOTENCY_KEY_INVALID`, `IDEMPOTENCY_KEY_REUSED`, `CURSOR_INVALID`, `LIMIT_INVALID`, `VALIDATION_FAILED`, `REQUEST_REJECTED`, `INTERNAL_ERROR`.
- Thiếu `Idempotency-Key` đang trả 422. Hợp đồng chỉ nói 428 cho `If-Match`. Huy xác nhận.
- Outbox chưa có số lần thử tối đa và xử lý sự kiện lỗi mãi: chưa có quyết định, làm ở TB-21 cùng bộ phát.

## Chưa làm, thuộc task khác
- Vòng lặp worker, vai trò `api`/`worker`: TB-21.
- Thử lại khi deadlock, ánh xạ `23P01`: TB-07.
- Tra trạng thái lệnh: TB-22. Dọn receipt và outbox: TB-24.

## Bước kế tiếp
Mở PR sau TB-3, tự rà `review-pr`, cho Codex rà, xóa file này.
