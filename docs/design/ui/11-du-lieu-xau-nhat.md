# Dữ liệu xấu nhất cho thiết kế

Theo skill `break-ui`: dữ liệu mẫu thường được chọn cho đẹp (tên ngắn, đủ trường, số tròn). Mỗi màn hình trong Figma có thêm một frame **"Dữ liệu xấu nhất"** dùng bộ dưới đây, và các trạng thái **Rỗng**, **Một mục**, **Nhiều mục**. Giới hạn lấy từ `contracts/openapi.yaml` ngày 08/11/2026; hợp đồng đổi thì sửa bảng này.

Mọi giá trị là tên và dữ liệu giả, nhưng có thể có thật ngoài đời. Không dùng chuỗi vô nghĩa kiểu `aaaa…`.

## Giới hạn theo hợp đồng
| Trường | Giới hạn | Ở màn hình |
|---|---|---|
| Tên hiển thị (phụ huynh, gia sư, con) | 1–100 ký tự | Mọi nơi có tên, `StudentSwitcher`, `TutorCard`, `SessionCard` |
| Email | tối đa 254 | AUTH01, AUTH03, AUTH04 |
| Mật khẩu | 15–128 | AUTH01, AUTH05, AUTH06 |
| Mục tiêu ngắn của con (`Student.shortGoal`) | tối đa **2.000** dù tên là "ngắn" | PH02 |
| Mục tiêu nhu cầu (`goal`), ảnh chụp mục tiêu của đề nghị | 20–2.000 | PH04, PH05, PH08, GS05, CL01 |
| Mô tả chuyên môn | 50–3.000 | GS02, PH07 |
| Kinh nghiệm | 0–600 tháng | GS02, PH06, PH07 |
| Giá 60 phút, ngân sách mỗi buổi, giá buổi | ≥ 1, **không có trần** | PH04, PH06–PH08, GS02, GS05, CL01 |
| Buổi trong đề nghị, lịch chung | 4–12 buổi | PH06, PH08, GS05, CL02 |
| Lý do khớp | tối đa 10 lý do × 300 ký tự | PH06 |
| Phạm vi xác minh | tối đa 20 × 200 ký tự | PH06, PH07, GS03 |
| Lý do quyết định xác minh | 1–2.000 | AD04, GS03 |
| Khung giờ rảnh của con | tối đa 100 khung | PH04 |
| Khung giờ rảnh gia sư | tối đa 200 khung | GS04 |
| Một trang danh sách (nhu cầu, thông báo...) | tối đa 50, tải thêm bằng `cursor` | PH03, COM01, AD03 |

Giá không có trần: `rateVnd60` và `budgetVndSession` chỉ có `minimum: 1`. Đính chốt 12/11/2026 không đặt trần (OP-20); app hiện số đã định dạng để người dùng tự thấy nếu nhập nhầm.

## Bộ giá trị
| Loại | Bình thường | Xấu nhất nên vẽ |
|---|---|---|
| Tên gia sư | Nguyễn Thị Lan | Tôn Nữ Hoàng Bảo Ngọc Phương Thảo (33 ký tự); "Lê Văn Tám – giáo viên Toán THCS, 12 năm luyện thi vào lớp 10" (61 ký tự, người dùng tự ghi chức danh vào tên); "Ý" (1 ký tự) |
| Tên con | Minh An | "Nguyễn Hoàng Gia Bảo Khôi Nguyên" (32); "Bin" |
| Email | an@example.com | `nguyen.hoang.bao.ngoc.phuong.thao.phuhuynh.lop8a2@truong-thcs-example.edu.vn` (76 ký tự, không có chỗ ngắt) |
| Mục tiêu | 1–2 câu | 2.000 ký tự nhiều đoạn, có xuống dòng và gạch đầu dòng tự gõ |
| Kinh nghiệm | 24 tháng | 0 tháng (hợp lệ, AC-UC03-01); 600 tháng (50 năm) |
| Giá 60 phút | 250.000 ₫ | 1.500.000 ₫; giá lẻ 250.001 ₫ với buổi 90 phút cho giá buổi 375.002 ₫ (làm tròn lên do máy chủ trả) |
| Giá buổi trên `PriceBlock` | 375.000 ₫ / buổi 90 phút | 3.000.000 ₫ / buổi 120 phút |
| Lý do khớp | 2 lý do ngắn | 10 lý do, lý do đầu dài 300 ký tự |
| Phạm vi xác minh | "Toán 8" | 20 phạm vi; phạm vi đầu dài 200 ký tự |
| Số buổi | 8 | 4 (ít nhất) và 12 (3 buổi/tuần × 4 tuần) |
| Giờ buổi | 18:00–19:30 | 21:30–23:30; buổi bắt đầu 00:00; hai buổi sát nhau 18:00–19:00 và 19:00–20:00 cùng ngày |
| Hạn trả lời | còn 1 ngày | còn dưới 1 phút; hạn rơi đúng giờ bắt đầu buổi đầu; đã quá hạn khi đang mở màn hình |
| Số thông báo chưa đọc | 3 | 0 (không hiện số); 1; 120 (hiện "99+", đề xuất) |
| Danh sách | 3 mục | 0; 1; 50 mục và nút "Xem thêm" |
| Lỗi từ máy chủ | Tiêu đề ngắn | Tiêu đề lỗi dài hai dòng; lỗi trường ở 4 ô cùng lúc |

## Mỗi frame "Dữ liệu xấu nhất" phải trả lời
- Chữ dài thì **xuống dòng, cắt có dấu `…`, hay giới hạn số dòng**? Quyết định theo từng trường, ghi trong `screens/<mã>.md`. Gợi ý: tên trong thẻ tối đa 2 dòng rồi cắt; mục tiêu và mô tả trong danh sách 3 dòng, xem đủ ở màn chi tiết; email xuống dòng ở bất kỳ ký tự nào.
- Chữ bị cắt thì xem đủ ở đâu (màn chi tiết, mở rộng).
- Danh sách 20 phạm vi xác minh, 10 lý do: hiện mấy cái đầu, phần còn lại "và 7 phạm vi khác".
- Với chữ 200% và dữ liệu xấu nhất cùng lúc, nút chính vẫn thấy và bấm được.
- Dấu tiếng Việt chồng (ệ, ẫ, ặ, Ỹ) không bị cắt đầu hoặc chân chữ.
