# PH01 trên Expo Go, đối chiếu bản duyệt (TB-14a, PR #192)

Huy chụp ngày 21/11/2026 trên iPhone, Expo Go SDK 58, cỡ chữ mặc định (riêng ảnh chữ lớn dùng cỡ chữ lớn của Trợ năng). App chạy từ `feature/TB-14-parent-home` tại `6fb36d7`, backend local. Tên các con là dữ liệu giả. Mỗi ảnh đặt ảnh app (trái) cạnh frame tương ứng, cắt từ `docs/design/ui/screens/img/PH01-v1.png` (phải). Nút bánh răng màu xanh là menu của Expo Go, không phải của app.

| Ảnh | Frame đã duyệt | Thấy gì |
|---|---|---|
| [PH01-chua-co-con.png](PH01-chua-co-con.png) | Chưa có con (18:109) | Câu chữ và nút đúng nguyên văn |
| [PH01-co-con.png](PH01-co-con.png) | Có dữ liệu (18:2) | Thanh "Đang xem" sau khi đổi sang con thứ hai. Các khối buổi, việc chờ, thông báo đang ẩn theo quyết định của Đính (#14, 15/11/2026) |
| [PH01-doi-con.png](PH01-doi-con.png) | Đổi con (18:471) | Bảng "Chọn con", con đang chọn được đánh dấu. Tên dài xuống dòng, không bị cắt |
| [PH01-chu-200.png](PH01-chu-200.png) | Chữ 200% (18:688) | Cỡ chữ lớn trong Trợ năng của iOS (chưa ghi chính xác mức nào). Mọi chữ xuống dòng, không bị cắt, không chồng nhau |

## Khác bản duyệt
1. Ô thông báo ở "Chưa có con" hiện tiêu đề "Thông tin" (mặc định của `Notice`). Bản duyệt chỉ có icon và câu, không có tiêu đề.
2. "Tổng quan" là tiêu đề lớn của `QueryScreen`. Bản duyệt dùng thanh tiêu đề nhỏ (`TopAppBar`).
3. Thanh "Đang xem" là thẻ bo góc thụt lề. Bản duyệt trải hết chiều ngang, có đường kẻ dưới.
4. Bảng chọn con không có thanh kéo ở đầu bảng. Lớp nền mờ không phủ thanh trạng thái.
5. Dòng phụ của lựa chọn chỉ có "Lớp 8." vì hai API lớp và đề nghị chưa merge (`TBD` đã ghi trong PR).
6. Chưa có thanh tab (chờ gói icon). Nút tạm "Bảo mật" vẫn còn.
7. Chữ lớn: "Đổi con ⌄" chiếm gần nửa thanh nên cột trái hẹp, "Đang xem" bị bẻ thành 2 dòng. Bản duyệt giữ "Đang xem" trên một dòng, chỉ tên xuống dòng.

## Chưa chụp
Đang tải, lỗi tải, dữ liệu cũ.
