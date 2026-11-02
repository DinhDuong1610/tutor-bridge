# F01 — Tài khoản, hồ sơ và xác minh

> Nguồn: TB G3 TH 01 v0.1 (`docs/source-docx/06_G3_Thiet_ke_Hop_nhat_de_Ra_soat_v0_1.docx`), giữ nguyên văn. Đọc kèm [change-requests](../decisions/change-requests.md) và [ADR](../decisions/adr/).

Căn cứ: TB G3 06 mục 2, 3; TB G3 07 về hợp đồng hợp nhất

### Luồng F01 và trạng thái giao diện

AUTH01 chỉ cho phụ huynh hoặc gia sư tự đăng ký. Người dùng nhập email, mật khẩu 15–128 ký tự, tên hiển thị và chấp thuận điều khoản có phiên bản. Máy chủ gán vai trò theo danh sách cho phép; không tiếp nhận quyền quản trị do ứng dụng gửi. Tài khoản chờ xác minh chưa được tạo lớp hoặc tải học liệu.

AUTH03 đăng nhập; AUTH02 xác minh liên hệ. Liên kết xác minh hạn 30 phút, khôi phục hạn 15 phút và lời mời học sinh hạn 24 giờ. Mã chỉ được dùng một lần, đúng mục đích và đúng người. Thông báo khôi phục dùng nội dung chung để không xác nhận sự tồn tại tài khoản.

AUTH06 cho xem phiên, thu hồi phiên và đổi mật khẩu sau tái xác thực. Đổi hoặc khôi phục mật khẩu thu hồi toàn bộ phiên cũ trong cùng giao dịch. Dữ liệu nháp đã lưu tại máy chủ vẫn tồn tại sau đăng nhập lại.

Đăng nhập sai năm lần trong 15 phút kích hoạt trì hoãn 15 phút; kiểm soát theo tài khoản và nguồn gửi. Không ghi mật khẩu, mã liên kết hoặc token vào log. Hạn phiên người dùng là 24 giờ không hoạt động, tối đa 7 ngày; quản trị là 30 phút và 12 giờ, kèm yếu tố xác thực bổ sung trước thí điểm.

| Màn hình | Tác vụ | Trạng thái bắt buộc |
|---|---|---|
| AUTH01–03 | Đăng ký, đăng nhập, xác minh | Đang gửi; lỗi mạng; hết hạn; đã dùng; bị khóa; giới hạn gửi |
| AUTH04–06 | Khôi phục, đổi mật khẩu, phiên | Thông báo chung; mã sai mục đích; phiên đã thu hồi; cần đăng nhập lại |
| AUTH07 P1 | Nhận lời mời học sinh | Sai email; hết hạn; đã nhận; bị thu hồi; tranh chấp liên kết |
| AD01 | Đăng nhập quản trị | Không có tự đăng ký; bước xác thực bổ sung; phiên hết hạn |

### Hồ sơ học sinh gia sư và xác minh

PH02 tạo hồ sơ con bằng tên hiển thị, khối lớp và mục tiêu ngắn; không bắt buộc ngày sinh, trường, ảnh hoặc địa chỉ. owner được lấy từ phiên phụ huynh đã xác minh, không lấy từ payload. Chọn rõ hồ sơ con trước thao tác và không gộp dữ liệu theo tên hoặc email.

Một phụ huynh quản lý mỗi hồ sơ trong R0. P1 dùng lời mời riêng tới email học sinh. Người nhận phải chứng minh quyền với email đích và xác nhận trước liên kết; không tìm ghép học sinh theo mã lớp. Thu hồi liên kết tăng revision quyền, vô hiệu mọi yêu cầu đọc/nộp tiếp theo nhưng giữ dấu vết bài đã nộp.

GS02/GS03 tách bản tự khai, bản chuyên môn đang duyệt và phiên bản đã xác minh. Kinh nghiệm 0–600 tháng; giá nguyên dương VND/60 phút. Giá và lịch mới chỉ áp dụng đề nghị mới, không sửa thỏa thuận lớp cũ. Sửa chuyên môn chuyển phần đó về chờ duyệt; không dùng nhãn duyệt cũ cho nội dung mới.

AD03/AD04 chỉ hiển thị hồ sơ được giao. Người xác minh không tự duyệt hồ sơ của mình; ghi phiên bản, phạm vi môn/khối, kết quả, lý do và thời điểm. Minh chứng gốc không xuất hiện ở DTO tìm gia sư. Nhãn xác minh chỉ mô tả phạm vi đã kiểm tra.

| Đối tượng | Chuyển trạng thái | Điều kiện |
|---|---|---|
| Lời mời | PENDING → ACCEPTED / REVOKED / EXPIRED | Một lần; đúng email và hồ sơ; kiểm lại tại commit |
| Phiếu xác minh | PENDING → VERIFIED / REJECTED | NEEDS_INFO giữ PENDING kèm lý do (D07-05); đúng revision, đúng người; cấm tự duyệt. |
| Hồ sơ công khai | Bản đã duyệt → HIDDEN khi bị khóa | Tìm kiếm lọc trạng thái tài khoản và bản chuyên môn hiện hành |

Bản hợp nhất bổ sung hợp đồng cho tài khoản học sinh qua lời mời, xác minh email và chấp thuận riêng. Đăng ký thông thường chỉ nhận PARENT/TUTOR; không có tự đăng ký ADMIN. Các giới hạn minh chứng chuyên môn được nêu tại D07-04 và vẫn chờ hai thành viên kết luận.
