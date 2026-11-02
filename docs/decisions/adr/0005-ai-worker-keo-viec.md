# ADR-0005 Worker Python kéo việc qua API nội bộ

**Trạng thái:** Đã chấp nhận theo ủy quyền của Đính ngày 03/11/2026, Huy đã xác nhận (CR-007). **Chủ trì:** Huy.

## Vấn đề
AI viết bằng Python, còn quyền và dữ liệu thuộc Spring Boot. Tác vụ dài tới 120 giây và mỗi nguồn phải được kiểm quyền ở nhiều mốc (G3-DP03). Cần cách nối hai bên sao cho tăng được số worker sau này.

## Phương án
| Phương án | Ưu | Nhược |
|---|---|---|
| A. Worker đọc ghi DB trực tiếp | Đơn giản, nhanh làm | Quyền nằm ở hai ngôn ngữ; worker giữ khóa DB; khó đổi schema |
| B. Spring gọi FastAPI, chờ kết quả | Quen thuộc | Giữ luồng và kết nối trong tới 120 giây; khó chống tải; mất việc khi lỗi giữa chừng |
| C. Spring đẩy vào broker | Chuẩn khi lớn | Thêm hạ tầng và ghi đôi ngay từ R0 |
| **D. Worker kéo việc qua API nội bộ** | Một nơi kiểm quyền; worker không trạng thái; `ai_jobs` là hàng đợi bền | Thêm một hợp đồng nội bộ; chậm hơn A một chút |

## Quyết định
Chọn D.

```text
Gia sư ──> Spring API ──(commit)──> ai_jobs QUEUED
                                       ▲
Python worker ── claim/heartbeat ──────┤  SKIP LOCKED, lease, fencing_token
              ── lấy bối cảnh đã lọc   │  Spring kiểm quyền và tối thiểu hóa dữ liệu
              ── gọi nhà cung cấp AI   │
              ── nộp kết quả ──────────┘  Spring kiểm lại quyền, epoch, fencing rồi mới ghi nháp
```

- Worker không có thông tin đăng nhập DB, không mở cổng nhận việc từ ngoài, chỉ gọi ra Spring và nhà cung cấp AI.
- Khóa AI chỉ nằm ở worker; Spring và ứng dụng không giữ.
- API nội bộ tách khỏi 123 thao tác công khai, có tiền tố `/internal/v1`, xác thực danh tính dịch vụ, không ra Internet.
- Dịch vụ không tự công bố: kết quả chỉ thành nháp, gia sư mới duyệt.
- Cùng cơ chế dùng cho xử lý tệp và tạo embedding.

## Hệ quả
- Tăng năng lực AI bằng cách chạy thêm worker. Giới hạn còn lại là chính sách và ngân sách.
- Worker chết thì hết lease, tác vụ được nhận lại hoặc kết thúc có trạng thái. Kết quả muộn bị `fencing_token` chặn.
- Khi có broker, chỉ thay phần `claim` bên trong Spring, worker gần như giữ nguyên.
- `worker_slots` hiện chỉ cho 2 chỗ, phải gỡ bằng [ADR-0006](0006-go-gioi-han-schema.md).
- Cần viết hợp đồng `/internal/v1` trong Nhóm 3, mục kiến trúc chi tiết F04.
