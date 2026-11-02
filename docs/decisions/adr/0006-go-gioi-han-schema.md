# ADR-0006 Gỡ giới hạn cứng trong schema bằng migration mới

**Trạng thái:** Đã chấp nhận theo ủy quyền của Đính ngày 03/11/2026, Huy đã xác nhận (CR-009). **Chủ trì:** Đính; phần AI Huy rà.

File: [006_scale_readiness.sql](../../design/migrations-proposed/006_scale_readiness.sql). Thành Flyway V6 khi dựng repo.

## Vấn đề
Rà V1–V5 ngày 03/11/2026 thấy một số chỗ chặn mở rộng hoặc thiếu dữ liệu. Giữ nguyên V1–V5 theo CR-005a, nên sửa bằng migration tiếp theo.

| # | Hiện trạng | Vị trí | Hậu quả |
|---|---|---|---|
| 1 | `ai_policies` khóa cứng giá trị: `global_running_limit=2`, `queue_limit=20`, ngày/lớp 5, ngày/gia sư 20, `timeout_seconds=120` | `001_foundation.sql:141-144` | Đổi hạn mức phải sửa schema, không phải cấu hình |
| 2 | `worker_slots` chỉ cho `slot_no` 1–2 | `001_foundation.sql:168` | Không thể chạy quá 2 tác vụ AI |
| 3 | `deadline_at = created_at + 120 giây` khóa bằng CHECK | `001_foundation.sql:151` | Thời hạn không đổi được theo loại tác vụ |
| 4 | Không có nơi lưu embedding hay vector | Toàn bộ V1–V5 | Không tính được điểm `s` của BR03 |
| 5 | `command_id NOT NULL UNIQUE REFERENCES command_receipts` ở các bảng nghiệp vụ, không `ON DELETE`. Áp dụng cả `audit_events` (nullable) | `004`, `001_foundation.sql:226` | Receipt không xóa được, tăng vô hạn dù chỉ cần 7 ngày |
| 6 | `notifications`, reminder và một bảng khác trỏ khóa ngoại vào `outbox_events` | `001`, `004` | Outbox không dọn được; bảng ghi nhiều nhất cứ lớn mãi |
| 7 | Không có bảng nào được chia partition | Toàn bộ V1–V5 | Khi lớn, dọn dữ liệu theo thời gian rất tốn |
| 8 | `CHECK(grade=8)` và các ràng buộc phân khúc R0 | `001_foundation.sql:16` | Mở môn/khối phải đổi schema |
| 9 | `budget_ledgers` một dòng cho mỗi chính sách | `001_foundation.sql:160` | Mọi tác vụ AI cùng ghi một dòng; chấp nhận được ở R0, cần xem lại khi nhiều worker |

## Quyết định
**Làm ngay trong V6:**
- Mục 1–3: đổi CHECK thành khoảng hợp lệ `> 0`; hạn mức 2/20/5/20/120 vẫn là giá trị mặc định của cột. `worker_slots` giữ nguyên cơ chế nhưng bỏ trần 2 chỗ, số chỗ do dữ liệu cấu hình. NFR13, NFR14 **không đổi**.
- Mục 4: bảng `embedding_configs`, `tutor_profile_embeddings`, `need_version_embeddings` dùng `pgvector`, kèm kiểm số chiều theo cấu hình. Đây đúng phương án TB G2 02 mục 8 nêu cần xác minh. Image PostgreSQL đổi sang `pgvector/pgvector:pg17`.
- Chưa tạo chỉ mục ANN: BR03 chỉ tính cosine trên tập ứng viên đã qua lọc cứng, nên tính chính xác là đủ. Embedding cho đoạn học liệu (RAG) để sang F04.
- Mục 5–6: bỏ mọi khóa ngoại trỏ vào `command_receipts` và `outbox_events`, giữ cột làm tham chiếu thường, thêm chỉ mục phục vụ dọn dữ liệu. Tính chống lặp nghiệp vụ vẫn do unique nghiệp vụ giữ.
- Mục 8: `grade` thành khoảng 1–12, `subject` chỉ kiểm định dạng. Phân khúc R0 do dịch vụ kiểm theo cấu hình, vì đây là phạm vi sản phẩm chứ không phải tính đúng dữ liệu. **Dịch vụ bắt buộc phải kiểm**, nếu không hệ thống sẽ nhận dữ liệu ngoài R0.
- Nhóm `timezone='Asia/Ho_Chi_Minh'` và các giới hạn tệp giữ nguyên vì không chặn mở rộng.

Không chọn: sửa thẳng V1–V5 (trái CR-005a), hoặc hoãn đến khi có tải (mỗi mục khi đó là đợt nâng cấp trên bảng lớn).

**Để sau, khi có số đo:** mục 7 chia partition theo tháng cho `audit_events`, `outbox_events`, `notifications`, `evaluation_events`; mục 9 chia nhỏ sổ ngân sách.

## Hệ quả
- V6 đổi bản thiết kế đã xác nhận nên Huy phải rà.
- Tổng số bảng thành 80. `docs/appendix` giữ nguyên bản, nên từ điển dữ liệu ở đó không có 3 bảng mới; từ điển cần sinh lại khi dựng repo.
- Một job định kỳ phải dọn receipt hết hạn và outbox đã giao. Phải xóa theo lô. Chưa có job này thì V6 mới chỉ cho phép dọn, chưa thực sự dọn.
- Sau khi receipt bị dọn, `command_id` có thể trỏ tới bản ghi không còn tồn tại. Code không được giả định nó luôn tìm thấy.
- Tên model và số chiều vector chưa chốt (OP-11). Đến lúc đó R0a chạy với phương án B, đúng BR03.

## Bằng chứng
Chạy ngày 03/11/2026 trên PostgreSQL 17.11 kèm pgvector ([kết quả](../../evidence/2026-11-03_v6_results.json)):

| Kịch bản | Kết quả |
|---|---|
| Nâng cấp V5 → V6 trên DB có dữ liệu | Áp thành công; 18/18 ca riêng của V6 |
| Đối chứng dọn dữ liệu | Trước V6 bị chặn `23503`; sau V6 xóa được, dòng tham chiếu còn nguyên |
| Dựng mới V1–V6 | 69/69 ca cũ không đổi; 18/18 ca V6 |

Giới hạn: chỉ kiểm ở tầng DB. Chưa kiểm hiệu năng `pgvector`, chưa kiểm job dọn dữ liệu, chưa nâng cấp trên dữ liệu lớn.
