# Playbook: transaction-change

Bắt buộc khi đụng: chốt lớp, lịch, booking, idempotency, khóa, thu hồi nguồn, công bố, xác nhận buổi. Đây là phần rủi ro nhất dự án; người review đọc từng dòng.

## Trước khi code
1. Đọc TX tương ứng trong `docs/design/f0X-*.md` và BR04–BR06.
2. Viết vào handoff, trước khi code:
   - Thứ tự khóa, đúng INV-16
   - Ranh giới giao dịch, xác nhận không có I/O ngoài bên trong (INV-18)
   - Điều kiện kiểm lại tại commit
   - Những gì cùng một commit: nghiệp vụ, receipt, outbox
   - Mọi cách thất bại và kết quả của từng cách

## Khi code
1. Nhận lệnh: xác thực, kiểm quyền, tra receipt theo actor + lệnh + khóa.
2. Khóa theo thứ tự chung, tài nguyên sắp theo khóa ổn định.
3. Kiểm lại toàn bộ tại đây: tài khoản, phê duyệt, giá, phiên bản, mọi khoảng lịch.
4. Ghi nghiệp vụ, outbox, receipt trong cùng giao dịch.
5. Commit xong mới trả thành công.
6. Ánh xạ lỗi: trùng lịch `23P01` thành 409 `SCHEDULE_CONFLICT`; bản cũ thành 412. Có rollback toàn bộ.
7. Deadlock hoặc serialization: thử lại có giới hạn với cùng khóa.

## Test bắt buộc
| Ca | Kỳ vọng |
|---|---|
| Hai lệnh đồng thời trên cùng tài nguyên, nhiều kết nối thật | Tối đa một thành công, không còn dữ liệu dở |
| Lỗi ở buổi cuối của chuỗi | Rollback toàn bộ |
| Gửi lại cùng khóa, cùng nội dung | Kết quả cũ, không phát sinh hiệu lực |
| Cùng khóa, khác nội dung | 409 |
| Dừng tiến trình sau commit rồi gửi lại | Kết quả cũ |
| Hai bản API cùng chạy | Vẫn đúng |
| Phiên bản cũ | 412, không đổi dữ liệu |
| Mất quyền giữa chừng | Từ chối, không lộ nội dung |

Test đồng thời phải chứng minh hai lệnh thực sự chồng nhau, ví dụ quan sát được chờ khóa. Hai lệnh chạy lần lượt không tính là bằng chứng đồng thời.

## Không được
- Dùng khóa trong RAM, khóa nút giao diện hoặc mock để thay chốt DB.
- Bắt lỗi rồi bỏ qua khiến giao dịch dở vẫn commit.
- Gọi AI, email, kho tệp trong giao dịch.
