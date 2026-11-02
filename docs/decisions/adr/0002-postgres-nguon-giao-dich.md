# ADR-0002 PostgreSQL là nguồn giao dịch

**Trạng thái:** Đã chấp nhận (G3-DP01). **Chủ trì:** Đính.

## Vấn đề
Lịch không được trùng kể cả khi nhiều bản API cùng ghi. Khóa trong bộ nhớ không dùng được khi có nhiều replica.

## Quyết định
- Ràng buộc DB là chốt cuối: exclusion constraint trên `resource_bookings` và các unique nghiệp vụ.
- Thứ tự khóa chung: lớp → thực thể nghiệp vụ → tài nguyên theo khóa ổn định → dữ liệu phụ.
- Receipt idempotency, nghiệp vụ và outbox cùng một commit.
- Khoảng thời gian `[bắt đầu, kết thúc)`, lưu `timestamptz`, hiển thị Asia/Ho_Chi_Minh.

## Bằng chứng
Ngày 02/11/2026, trên PostgreSQL 17.11: hai kết nối đặt cùng tài nguyên, kết nối sau bị chặn với `23P01` ([kết quả](../../evidence/2026-11-02_native_postgres17_results.json)). Chưa kiểm TX05 đầy đủ hay hai bản API.

## Hệ quả
- Ràng buộc theo từng tài nguyên nên tranh chấp cục bộ giữa người cùng giờ, không phải khóa toàn hệ thống.
- Không dùng DB dạng phân tán đa-chủ cho lịch nếu chưa có ADR mới.
- Mọi kết nối phải tương thích connection pooler: không dựa vào trạng thái phiên DB qua nhiều giao dịch.
