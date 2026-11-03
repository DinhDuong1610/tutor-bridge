# ADR-0007 Khóa tài nguyên bằng khóa tư vấn theo giao dịch

**Trạng thái:** Đề xuất, chờ Huy rà. **Chủ trì:** Đính.

## Vấn đề
TX05 phải khóa gia sư và học sinh theo thứ tự ổn định. Thiết kế 8.1 yêu cầu ghi cơ chế cụ thể vào ADR. Khóa dòng `calendars` không đủ: lịch có phiên bản nên id thay đổi, và tài nguyên có thể chưa có lịch hiệu lực.

## Quyết định
Dùng `pg_advisory_xact_lock` với khóa `hashtextextended('STUDENT:<id>' hoặc 'TUTOR:<id>', 0)`, lấy theo thứ tự chuỗi tăng dần, sau khi đã khóa nhu cầu và đề nghị. Mọi giao dịch đặt hoặc đổi lịch dùng cùng khóa này.

Exclusion constraint `booking_no_overlap` vẫn là chốt cuối (ADR-0002). Khóa tư vấn chỉ giúp giao dịch xếp hàng, tránh deadlock; nó không thay ràng buộc.

## Vì sao không chọn phương án khác
| Phương án | Lý do bỏ |
|---|---|
| Chỉ dựa exclusion constraint | Đúng dữ liệu nhưng hai giao dịch cùng tài nguyên dễ deadlock khi chèn nhiều buổi theo thứ tự khác nhau |
| Khóa dòng `calendars` | Id đổi theo phiên bản, không có dòng thì không khóa được |
| Bảng tài nguyên riêng để khóa | Thêm bảng và dữ liệu phải giữ đồng bộ |
| Khóa cấp phiên | Không tương thích connection pooler (INV-83) |

## Hệ quả
- Khóa tự nhả khi giao dịch kết thúc, dùng được qua pooler chế độ giao dịch.
- Va chạm băm chỉ làm hai tài nguyên khác nhau chờ nhau, không sai dữ liệu.
- Giao dịch cùng tài nguyên chạy lần lượt. Ảnh hưởng thông lượng chưa đo.
