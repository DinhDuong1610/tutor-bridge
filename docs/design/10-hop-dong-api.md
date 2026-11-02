# Hợp đồng API thống nhất

> Nguồn: TB G3 TH 01 v0.1 (`docs/source-docx/06_G3_Thiet_ke_Hop_nhat_de_Ra_soat_v0_1.docx`), giữ nguyên văn. Đọc kèm [change-requests](../decisions/change-requests.md) và [ADR](../decisions/adr/).

Căn cứ: TB G3 07 mục 3–4; quy ước gửi lặp của TB G3 03 mục 8.1

### Các khác biệt đã xử lý trong ứng viên

Hai gói dùng cùng tên ReviewInput cho dữ liệu khác nhau. Bản hợp nhất giữ tên đó cho duyệt bài soạn và đổi nhận xét bài nộp thành SubmissionReviewInput; các tham chiếu được viết lại theo đúng ngữ nghĩa. Không ghép hai tập trường vào cùng DTO.

Problem thống nhất theo cấu trúc cơ sở F02/F04, dùng traceId làm mã tương quan trong payload. Tên correlationId trong phần diễn giải được hiểu là cùng khái niệm, không thêm hai trường bắt buộc khác nhau. Máy chủ ví dụ dùng /api/v1 và miền .invalid; không phải URL dịch vụ đã triển khai.

Các điểm có tác động chính sách được giữ là đề xuất cần nhóm kết luận, đặc biệt người cấp quyền hỗ trợ và giới hạn minh chứng chuyên môn. Việc người dùng yêu cầu tiếp tục không được ghi thay xác nhận riêng của thành viên.

| Mã | Nội dung tích hợp | Điều kiện còn lại |
|---|---|---|
| D07-01/02 | Tên schema duyệt; lỗi và prefix thống nhất | Rà soát client và hợp đồng trước phát triển |
| D07-03 | Chủ sở hữu nguồn cấp VIEW_ORIGINAL cho hỗ trợ | Đề xuất bảo thủ; không tự cho tải/AI/đáp án |
| D07-04 | Minh chứng PDF/JPEG/PNG, ≤5MiB, tối đa 10 tệp | Giới hạn mới cần Đính/Huy kết luận |
| D07-05/06 | NEEDS_INFO → PENDING kèm lý do; ETag lần tạo đầu | Cài service và thử lỗi phiên bản |
| D07-07/08 | Đăng ký học sinh qua lời mời; đóng yêu cầu xóa có bằng chứng | Chưa có email, storage hoặc export thật |

### Hợp đồng API và truy vết yêu cầu

Hợp đồng hợp nhất có schema đầu vào và đầu ra theo từng thao tác. Bổ sung đường cho xác thực quản trị, tái xác thực, tài khoản học sinh qua lời mời, minh chứng, lịch rảnh và mục tiêu lớp nhằm nối các điểm còn thiếu giữa nhóm chức năng. Danh mục thao tác ghi nguồn và mã TX hoặc UC; bảng FR ghi rõ bằng chứng cùng giới hạn.

Schema cấm thuộc tính ngoài danh sách ở các DTO mới. Đăng ký thông thường chỉ nhận PARENT hoặc TUTOR; owner hồ sơ con lấy từ phiên. STUDENT chỉ tạo qua lời mời hợp lệ rồi xác minh email và chấp thuận riêng. Không có đường tự chọn ADMIN.

Phiên mobile dùng Bearer opaque; web quản trị có phương án cookie HttpOnly Secure. Lệnh dùng cookie phải kiểm CSRF và Origin; mô tả chính sách trong OpenAPI không thay triển khai thực tế. Đọc nội dung riêng trả private, no-store; DTO bài tập và bài nộp không mang đáp án bí mật.

| Nhóm nguồn | Số thao tác | Phạm vi |
|---|---|---|
| F02/F04 | 40 | Nhu cầu, matching, đề nghị, lớp, nguồn, AI và công bố |
| F03/F05 | 32 | Đổi lịch, nhật ký, xác nhận, bài tập và tiến trình |
| Bổ sung F01/F06 và điểm nối | 51 | Tài khoản, hồ sơ, hỗ trợ, privacy; lịch và mục tiêu |
| Tổng | 123 | 108 đường dẫn; 138 schema; 1.594 tham chiếu đã phân giải |

Bearer actor do server xác thực. Mọi lệnh mutation cần Idempotency Key; lệnh sửa hoặc quyết định trên phiên bản cần If Match. Thiếu điều kiện trả 428; bản cũ trả 412. ETag của bản nháp là revision của version; planRevision dùng cho công bố theo aggregate. Client giữ phần nhập hợp lệ khi có lỗi.

Receipt được giữ tối thiểu 7 ngày theo actor, command và key; payload khác trả 409. Replay phải tái kiểm quyền rồi xử lý receipt trước điều kiện phiên bản hiện tại. Mất phản hồi mạng phải hỏi trạng thái hoặc gửi lại cùng key, không tạo key mới và đoán thành công. Business unique vẫn tồn tại sau khi receipt được dọn.

API publication dùng projection không có đáp án; Cache Control private no store. Original/segment chỉ đọc khi có quyền tương ứng. Tra chi tiết mã lỗi và response trong api_openapi_hop_nhat.yaml.

Hợp đồng hiện hành có 123 thao tác trên 108 đường dẫn và 138 schema. Giữ một bản YAML để tra cứu; không kèm thêm bản JSON trùng nội dung. Các ví dụ máy chủ là placeholder, không phải dịch vụ đã triển khai.
