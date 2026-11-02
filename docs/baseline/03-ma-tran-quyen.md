# Ma trận quyền

> Nguồn: TB G2 02 v1.1 (`docs/source-docx/04_G2_PRD_SRS_Hop_nhat_v1_1.docx`), giữ nguyên văn. Đọc kèm [change-requests](../decisions/change-requests.md).
> **Lưu ý:** phạm vi quyền tạm cho hỗ trợ còn mở tại OP-02.

## 4 Ma trận quyền

Quyền được tính từ vai trò, quan hệ được xác minh, quyền học liệu và trạng thái đối tượng. Dưới đây là quyền tối đa; mọi đường API/tệp/AI đều phải kiểm tra lại. Quản trị hỗ trợ và người xác minh là hai nhóm quyền riêng dù một người có thể được phân công cả hai.

Bảng 3 Ma trận quyền nghiệp vụ

| Đối tượng | Phụ huynh | Gia sư lớp | Học sinh P1 | Quản trị |
|---|---|---|---|---|
| Hồ sơ con | Tạo/sửa con của mình | Phần cần cho đề nghị/lớp | Xem tối thiểu của mình | Không mặc định |
| Hồ sơ gia sư | Xem bản công khai | Sửa của mình | Xem công khai | Duyệt theo phân công |
| Minh chứng | Xem nhãn kết quả | Xem/tải của mình | Không | Chỉ người xác minh được giao |
| Nhu cầu/đề nghị | Tạo/gửi/hủy của con | Chỉ đề nghị gửi mình | Không | Metadata vụ việc |
| Buổi/lịch lớp | Xác nhận/đổi/hủy trong lớp | Xác nhận/đổi/hủy trong lớp | Xem trong quyền | Không tự chốt thay |
| Tệp gốc | Theo cấp quyền riêng | Tải/chọn trong quyền | Theo chia sẻ riêng | Quyền tạm có lý do |
| Nháp/đáp án dự kiến | Không | Soạn/duyệt của lớp | Không | Không mặc định |
| Bản công bố | Xem của con | Quản lý của lớp | Xem của mình | Không mặc định |
| Đáp án | Chỉ khi gia sư mở | Xem và quyết định mở | Chỉ khi đã mở | Không mặc định |
| Nhật ký/bài nộp | Xem, xác nhận/nộp thay | Ghi chuyên môn/nhận xét | Nộp bài mình | Theo vụ việc được cấp |
| Xóa/thu hồi | Dữ liệu/quyền mình quản lý | Dữ liệu/quyền mình quản lý | Yêu cầu qua phụ huynh | Xử lý được giao |
| Audit | Không mặc định | Không mặc định | Không | Chỉ quyền kiểm toán |

Quyền tạm đọc học liệu cần người quản lý dữ liệu đồng ý cho đúng vụ việc, giới hạn 24 giờ, ghi lý do và có thu hồi. Tài khoản bị khóa bị từ chối phiên và không tham gia matching; xử lý lớp hiện hữu qua hỗ trợ, không âm thầm xóa lịch sử.
