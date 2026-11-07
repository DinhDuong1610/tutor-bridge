# Định hướng thiết kế

Đính chọn ngày 07/11/2026: tính cách "tin cậy, điềm tĩnh"; màu chủ đạo xanh dương đậm đang dùng; thoáng, bo tròn vừa; font Be Vietnam Pro; điểm nhấn là thẻ lịch buổi học; đã có logo; Lát 1 chỉ nền sáng.

## Người dùng và bối cảnh
| Người dùng | Kênh | Họ cần gì từ giao diện |
|---|---|---|
| Phụ huynh có con học Toán 8 | Android | Biết chắc đang thao tác cho con nào, giá bao nhiêu, lịch nào đã chốt. Nhiều người không rành công nghệ, đọc trên điện thoại tầm trung, có thể phóng chữ to |
| Gia sư Toán | Android | Thấy nhanh việc cần làm: hồ sơ còn thiếu gì, đề nghị nào sắp hết hạn, buổi nào sắp tới |
| Người xác minh (quản trị) | Web, màn hình máy tính | Đọc hồ sơ và minh chứng được giao, ghi kết quả có lý do, không nhầm hồ sơ |

Bối cảnh của sản phẩm: chốt lớp và lịch là cam kết giữa hai gia đình, kèm tiền và dữ liệu trẻ em. Lỗi đắt nhất là người dùng **tưởng** đã chốt, đã lưu, đã gửi trong khi máy chủ chưa xác nhận. Giao diện phải nói rõ sự thật hơn là gây ấn tượng.

## Tính cách: tin cậy, điềm tĩnh
| Là | Không là |
|---|---|
| Rõ ràng, nói thẳng điều đang xảy ra | Hào hứng, cổ vũ, dùng dấu chấm than |
| Ổn định: cùng một việc luôn ở cùng một chỗ | Bất ngờ, hiệu ứng, mỗi màn một kiểu |
| Chừng mực: một điểm nhấn, còn lại tiết chế | Trang trí, gradient, minh họa ở mọi nơi |
| Lịch sự, xưng "bạn" | Thân mật quá mức, đùa, emoji |

## Nguyên tắc
1. **Sự thật trước, đẹp sau.** Trạng thái máy chủ (chờ, đã chốt, chưa rõ kết quả, bản cũ) luôn thấy được bằng chữ. Không bao giờ hiện thành công trước khi máy chủ xác nhận.
2. **Ngữ cảnh luôn hiện.** Phụ huynh luôn thấy con đang chọn; mọi màn hình lớp, nhu cầu, đề nghị ghi rõ của con nào, gia sư nào, phiên bản nào.
3. **Tiền và giờ không mơ hồ.** Giá luôn kèm đơn vị, theo `formatVnd` của `@tutorbridge/shared`: `250.000 ₫ / 60 phút`, `375.000 ₫ / buổi 90 phút`. Giờ dạng 24 giờ theo giờ Việt Nam: `Thứ 4, 18/11/2026, 18:00–19:30`. Hàm định dạng thứ trong tuần chưa có trong `@tutorbridge/shared`, thêm ở Issue giao diện đầu tiên cần nó.
4. **Một việc chính mỗi màn hình.** Một nút chính, đặt cố định ở cuối màn hình trên mobile. Việc phụ là nút phụ hoặc liên kết.
5. **Phân biệt bằng chữ và hình, không chỉ bằng màu.** Tự khai và đã xác minh, nháp và đã xác nhận, đọc và chưa đọc: luôn có nhãn chữ hoặc icon đi kèm.
6. **Đọc được với chữ phóng to 200%.** Không cắt chữ, không chiều cao cố định quanh chữ, nút chính vẫn bấm được.
7. **Cùng câu chữ trên mobile và web** cho cùng một trạng thái ([04](04-trang-thai-cau-chu.md)).

## Điểm nhấn duy nhất: thẻ lịch buổi học
Lịch là phần lõi của sản phẩm: chu kỳ 28 ngày, các buổi cụ thể, đã chốt hay chỉ là rảnh. Thẻ lịch buổi học là nơi duy nhất được nổi bật về hình khối. Mọi phần khác tiết chế.

Thẻ lịch có:
- **Khối ngày** bên trái: thứ viết tắt (`T4`) và số ngày lớn, nền `primary`, chữ `onPrimary`.
- **Giờ** `18:00–19:30` cỡ chữ heading, thời lượng `90 phút`.
- **Ai và lớp nào:** tên con (phụ huynh xem) hoặc tên học sinh và phụ huynh (gia sư xem), không hiện liên hệ trước khi chốt.
- **Trạng thái bằng chữ và icon:** `Đã chốt`, `Đề nghị, chưa chốt`, `Đã hủy`. Buổi chưa chốt dùng viền nét đứt và nền `surface`, không dùng khối ngày đặc, để không trông như đã đặt.
- **Dải chu kỳ 28 ngày** (PH05, PH08, GS05, CL02): 4 khối 7 ngày, mỗi buổi một dấu; đánh số khối `Tuần 1`–`Tuần 4` vì đây là chuỗi thật.

Biến thể của thẻ ở [03](03-thanh-phan.md#thẻ-lịch-buổi-học-sessioncard). Thẻ này được thiết kế kỹ nhất và duyệt trước mọi màn hình dùng nó.

## Logo
Đã có logo (Đính). File chưa có trong repo, theo dõi ở OP-16. Khi có: đặt bản gốc trong Figma trang `01 Foundations`, xuất SVG cho web và PNG cho Android bằng PR riêng. Logo không đổi màu tùy tiện; vùng trống quanh logo và cỡ nhỏ nhất ghi trong Figma.

## Ngoài phạm vi ở Lát 1
- Chế độ tối. Token vẫn đặt tên theo vai trò (`surface`, `textMuted`) để thêm sau không phải sửa màn hình.
- Minh họa, ảnh. Màn hình rỗng dùng câu chữ và nút hành động, không dùng hình minh họa.
- iOS, web gia sư, web phụ huynh.
