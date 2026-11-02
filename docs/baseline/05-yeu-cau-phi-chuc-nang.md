# Yêu cầu phi chức năng NFR01–NFR16

> Nguồn: TB G2 02 v1.1 (`docs/source-docx/04_G2_PRD_SRS_Hop_nhat_v1_1.docx`), giữ nguyên văn. Đọc kèm [change-requests](../decisions/change-requests.md).

## 7 Yêu cầu phi chức năng

Các ngưỡng là tiêu chí cần đạt khi có sản phẩm chạy; chưa phải kết quả đo hoặc SLA đã cam kết. Yêu cầu an toàn dữ liệu và tính đúng không được đổi lấy điểm AI hoặc độ trễ thấp hơn.

Bảng 8 Yêu cầu chất lượng 1 đến 6

| Mã | Yêu cầu và mục tiêu | Cách kiểm chứng |
|---|---|---|
| NFR01 | Quyền theo đối tượng Mọi đường đọc/ghi/tải/truy xuất kiểm tra vai trò, quan hệ và trạng thái; mặc định từ chối. Không có ca vượt quyền trong bộ nghiệm thu. | Ca dương và âm từng ô ma trận; đổi ID/role, gọi API trực tiếp và URL cũ. AT01, AT07, AT11. |
| NFR02 | Tách dữ liệu AI Không đưa nguồn ngoài quyền vào prompt, kết quả, cache, trích dẫn hoặc nội dung công bố. | Hai lớp có chuỗi đánh dấu khác nhau; kiểm tra toàn tuyến và khi đổi mã nguồn. AT07, AT12. |
| NFR03 | Thu hồi hiệu lực Kiểm tra quyền ở lúc đọc nguồn, trước lưu/trả và trước công bố; tạm ẩn dẫn xuất liên quan ngay. | Thu hồi tại từng điểm của tác vụ, thử lại và khôi phục cache. AT07, AT12, AT19. |
| NFR04 | Nhất quán đồng thời Không tạo lịch/lớp/bài nộp trùng; chuỗi buổi và đổi lịch toàn bộ thành công hoặc không đổi. Khóa thao tác lặp lưu 7 ngày. | Hai phiên và hai bản API đồng thời; dừng tiến trình sau giao dịch rồi gửi lại; cùng khóa khác payload bị từ chối. AT04, AT09, AT13. |
| NFR05 | Lỗi AI và nháp AI lỗi không ảnh hưởng lớp/lịch hoặc dữ liệu đã lưu; có soạn thủ công. Chỉ báo đã lưu sau xác nhận máy chủ. | Timeout, trả cấu trúc sai, ngắt mạng và hết hạn mức; kiểm tra bản đang sửa. AT08, AT13. |
| NFR06 | Nội dung không đáng tin Tệp không đổi được quyền hoặc chỉ dẫn điều khiển; không thực thi macro, lệnh hoặc công cụ do học liệu yêu cầu. | Bộ học liệu chứa chỉ dẫn bỏ quyền và công bố tự động; đầu ra vẫn qua duyệt. AT12. |

Bảng 8 Yêu cầu chất lượng 7 đến 12

| Mã | Yêu cầu và mục tiêu | Cách kiểm chứng |
|---|---|---|
| NFR07 | Dữ liệu gửi nhà cung cấp Dùng mã giả; không gửi tên/liên hệ/địa chỉ không cần thiết. Chỉ gửi nguồn được chọn còn quyền; không dùng dữ liệu thử thật để huấn luyện ngoài phạm vi cho phép. | Rà payload và điều khoản nhà cung cấp trước bật AI; có danh mục trường gửi. AT12 và GATE03. |
| NFR08 | Bí mật và vận hành Truyền bằng TLS; bí mật ngoài mã nguồn/ứng dụng/log; phân quyền kho tệp riêng; mật khẩu băm phù hợp, quản trị thêm yếu tố xác thực. | Rà cấu hình, kho mã và log; thử truy cập kho trực tiếp, phiên thu hồi. Thiết kế cụ thể G3. AT11. |
| NFR09 | Nhật ký kiểm toán Ghi người, đối tượng, thao tác, phiên bản, thời điểm, kết quả và mã truy vết cho hành động nhạy cảm; không lưu bí mật/học liệu đầy đủ. Giữ 180 ngày. | Đối chiếu duyệt/đổi quyền/lịch/công bố/xóa; chỉ người được phân công đọc. AT15, AT19. |
| NFR10 | Hiệu năng nghiệp vụ 20 RPS trong 30 phút sau 5 phút làm nóng; API thường p95 <500 ms, lỗi ngoài dự kiến <1%; matching tách nhóm p95 <3 giây. Thông báo p95 ≤10 giây. | Dữ liệu 1.000 tài khoản, 200 lớp, 5.000 buổi; phối 70% đọc, 20% cập nhật, 10% matching. Ghi cấu hình, độ trễ mạng, mức đồng thời; không tính lỗi chủ đích vào tải chuẩn. |
| NFR11 | Lưu xóa và phục hồi RPO ≤1 giờ, RTO ≤4 giờ cho DB và tệp nhất quán; backup 7 ngày, có nhật ký xóa áp lại trước mở. Chính sách BR08. | Diễn tập trên bản sao môi trường; khôi phục DB/tệp/quyền, kiểm tra nguồn đã xóa và lịch. AT19. |
| NFR12 | Giao diện và trợ năng Web thao tác bàn phím/focus/nhãn; mobile có nhãn trợ năng, phóng chữ 200% không mất nút chính; mọi tác vụ có chờ/rỗng/lỗi/thử lại. | Bộ màn hình mục 9 trên thiết bị hỗ trợ; kiểm tra văn bản dài và mạng yếu. AT14. |

Bảng 8 Yêu cầu chất lượng 13 đến 16

| Mã | Yêu cầu và mục tiêu | Cách kiểm chứng |
|---|---|---|
| NFR13 | Xử lý tệp và AI Ở tối đa 2 tác vụ AI toàn hệ thống và 1/gia sư: nhận tác vụ p95 ≤2 giây, kết thúc hoặc lỗi rõ trong 120 giây kể cả chờ. Tệp chuẩn 50 trang/100.000 ký tự xử lý p95 ≤60 giây. | Tối thiểu 30 lần mỗi loại trên cấu hình G3; báo cỡ mẫu, lỗi và chi phí. Quá thời hạn chuyển lỗi có thử lại, không bỏ tác vụ treo. |
| NFR14 | Hạn mức và ngân sách 5 lần tạo/lớp/ngày, 20/gia sư/ngày, 2 tác vụ chạy và hàng chờ tối đa 20; mọi thử lại tính chi phí. Nếu chưa đặt trần chi tiền thực tế, AI ở trạng thái tắt. | Chạm hạn mức, hàng chờ đầy và dự toán vượt trần thì từ chối trước gọi; lỗi nhà cung cấp không tự thử lại vô hạn. GATE03. |
| NFR15 | Khả năng mở rộng và tương thích Nghiệp vụ giữ tính đúng khi có 2 bản API; trạng thái phiên/tệp/tác vụ cần thiết không chỉ nằm ở RAM một tiến trình. API phiên bản rõ, hỗ trợ ứng dụng hiện tại và một bản liền trước. | Bài tải/đồng thời qua 2 API, restart một bản; kiểm tra ứng dụng hỗ trợ. Nhu cầu hàng nghìn người phải đo lại theo mô hình tải G3, không suy từ số đăng ký. |
| NFR16 | Theo dõi và phát hành Có mã lỗi/truy vết, số đo độ trễ/lỗi/hàng chờ/chi phí; cảnh báo ngừng AI khi có sự cố. Build và kiểm tra quyền/lịch/nguồn được tích hợp trước phát hành. | Rà log đã tối thiểu hóa, kích hoạt cảnh báo thử, diễn tập rollback và migration trên bản sao; gắn phiên bản triển khai vào kết quả. |
