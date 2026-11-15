# ADR-0007 Khóa tài nguyên bằng khóa tư vấn theo giao dịch

**Trạng thái:** Đã chọn (Đính, 13/11/2026), sau khi Huy rà ngày 12/11/2026 trên #15; sửa theo 1 điểm nên sửa và 3 góp ý của Huy. **Chủ trì:** Đính.

## Vấn đề
TX05 phải khóa gia sư và học sinh theo thứ tự ổn định. Thiết kế 8.1 yêu cầu ghi cơ chế cụ thể vào ADR. Khóa dòng `calendars` không đủ: lịch có phiên bản nên id thay đổi, và tài nguyên có thể chưa có lịch hiệu lực.

## Quyết định
Dùng `pg_advisory_xact_lock` với khóa `hashtextextended('STUDENT:<id>' hoặc 'TUTOR:<id>', 0)`, lấy theo **giá trị băm tăng dần** (không theo chuỗi), nên hai tài nguyên có cùng giá trị băm chỉ là cùng một khóa, không thể deadlock vì thứ tự.

Giao dịch phải lấy khóa này:
- Đặt hoặc đổi buổi: TX05 chốt lớp, đổi lịch và hủy của F03.
- **Thay hoặc xác nhận lịch rảnh** (TB-15: `replaceTutorCalendar`, `replaceStudentCalendar`), trước khi đổi `calendars` và `availability_windows`. Nếu không, TX05 có thể kiểm khả dụng theo lịch cũ trong lúc lịch mới đang commit, và chốt lớp ngoài khung rảnh mới; `booking_no_overlap` không bắt được trường hợp này vì nó chỉ chặn hai booking chồng nhau.

Vị trí trong thứ tự chung INV-16: khóa tài nguyên nằm ở bước "tài nguyên theo khóa ổn định", sau lớp và thực thể nghiệp vụ (ví dụ nhu cầu, đề nghị), trước dữ liệu phụ, cho mọi giao dịch F02, F03 và TB-15.

Mỗi giao dịch giữ khóa tài nguyên đặt `SET LOCAL lock_timeout = '5s'`. Hết giờ chờ khóa trả 503 `SERVICE_BUSY`; theo #83, client coi là chưa rõ kết quả và gửi lại cùng `Idempotency-Key`.

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
- Va chạm băm chỉ làm hai tài nguyên khác nhau chờ nhau, không sai dữ liệu. Vì lấy theo giá trị băm nên va chạm cũng không gây deadlock.
- Giao dịch treo không giữ giao dịch khác chờ quá 5 giây (`lock_timeout`).
- Giao dịch cùng tài nguyên chạy lần lượt, kể cả thay lịch rảnh với chốt lớp. Ảnh hưởng thông lượng chưa đo; chấp nhận cho Lát 1, đo bằng k6 trước khi lên nấc tải (`docs/process/ci.md`).
- Code TX05 hiện lấy khóa theo chuỗi và chưa đặt `lock_timeout`; sửa cùng code TB-15, có test.
