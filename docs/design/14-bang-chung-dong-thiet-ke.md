# Bằng chứng và điều kiện đóng thiết kế

> Nguồn: TB G3 TH 01 v0.1 (`docs/source-docx/06_G3_Thiet_ke_Hop_nhat_de_Ra_soat_v0_1.docx`), giữ nguyên văn. Đọc kèm [change-requests](../decisions/change-requests.md) và [ADR](../decisions/adr/).

Căn cứ: TB G3 07/08; báo cáo rà soát tổng thể; sổ xác nhận XC04–XC05

| Hạng mục | Trạng thái đã ghi | Giới hạn |
|---|---|---|
| Hợp đồng API | Đã kiểm cấu trúc; 123 thao tác | Chưa có API sản phẩm hoặc client vận hành |
| Dữ liệu hợp nhất | 64/64 ca SQL nhúng kế thừa; 77 bảng | Không chứng minh đồng thời máy chủ hoặc quyền HTTP |
| Logic bộ kiểm chứng | 12/12 ca hồi quy logic runner | Dữ liệu mock; không phải kiểm DB máy chủ |
| PostgreSQL máy chủ | Chưa thực thi trong hồ sơ | Chưa có bằng chứng nhiều kết nối thực tế |
| UX, AI, tải và phục hồi | Chưa có kết quả thực tế đầy đủ | Không tuyên bố đạt NFR hoặc mức phục vụ hàng nghìn người dùng |
| Xác nhận thiết kế | Chờ ý kiến theo từng mã | Không điền thay Huy hoặc tự đóng G3 |

Các kết quả trên là bằng chứng lịch sử được giữ để đánh giá đúng mức độ chắc chắn. Đợt bàn giao này chỉ biên tập tài liệu; không chạy lại bộ kiểm chứng hay thiết lập môi trường.

XC04 yêu cầu xác nhận lại ranh giới giữa nghiệm thu thiết kế, thử bản mẫu và kiểm thử sản phẩm sau phát triển. Nội dung chưa có bằng chứng phải giữ trạng thái mở hoặc có ngoại lệ được chấp nhận; không tự xóa tiêu chí cũ. Hai thành viên cần thống nhất việc nào bắt buộc trước khóa thiết kế và việc nào chuyển sang kế hoạch kiểm chứng sau.

| Điều kiện cần kết luận | Nơi ghi nhận |
|---|---|
| Phạm vi R0/P1 và bản nguồn dùng làm cơ sở | Sổ xác nhận G2-D và XC01–XC03 |
| Các quyết định kỹ thuật và ngoại lệ | Sổ DP, D35, D16, G3-D07 có namespace |
| Phân kỳ cổng G3 và tiêu chí trải nghiệm | XC04, XC05 |
| Giờ làm, ngân sách, mốc và trách nhiệm | Các mục nguồn lực trong sổ xác nhận; không tự điền số |
| Khoảng trống và bộ hồ sơ trình ngoài | Báo cáo rà soát tổng thể; danh mục HS01–HS26 |
