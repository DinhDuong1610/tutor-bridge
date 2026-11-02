# ADR-0003 Outbox và hàng đợi trong PostgreSQL

**Trạng thái:** Đã chấp nhận (G3-D16-05). **Chủ trì:** Đính.

## Vấn đề
Sự kiện nghiệp vụ không được mất khi giao dịch đã commit và không được phát khi giao dịch rollback.

## Quyết định
- Ghi `outbox_events` cùng commit với nghiệp vụ.
- Worker nhận việc bằng `FOR UPDATE SKIP LOCKED`, có lease; consumer khử lặp theo `dedupe_key` và người nhận.
- Không dùng broker ở R0.

## Hệ quả
- Giảm một hạ tầng phải vận hành và không có bài toán ghi đôi.
- Người gọi chỉ phụ thuộc giao diện phát sự kiện, nên đổi sang broker không đụng nghiệp vụ.
- Outbox vẫn là bước đệm bền vững ngay cả khi có broker.
- **Điều kiện chuyển broker:** số đo cho thấy hàng đợi DB gây tải đáng kể hoặc tồn đọng kéo dài. Chuyển bằng ADR mới.
