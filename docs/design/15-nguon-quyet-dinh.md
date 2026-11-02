# Nguồn, quyết định và quản lý phiên bản

> Nguồn: TB G3 TH 01 v0.1 (`docs/source-docx/06_G3_Thiet_ke_Hop_nhat_de_Ra_soat_v0_1.docx`), giữ nguyên văn. Đọc kèm [change-requests](../decisions/change-requests.md) và [ADR](../decisions/adr/).

Căn cứ: TB G3 01–08; PRD/SRS và sổ xác nhận hiện hành

| Nguồn được hợp nhất | Nội dung giữ trong quyển này |
|---|---|
| TB G3 01 v0.1 | 52 màn hình; phạm vi sáu luồng và trạng thái chung |
| TB G3 03 v0.1 | F02/F04, giao dịch, quyền nguồn và quy ước gửi lặp |
| TB G3 04 v0.1 | Sửa R01–R03; giới hạn bằng chứng |
| TB G3 05 v0.1 | F03/F05, trạng thái, quyền, nhật ký, bài tập và TX17–TX30 |
| TB G3 06 v0.1 | F01/F06, kiến trúc, bảo mật, AI và vận hành |
| TB G3 07 v0.1 | API và dữ liệu hợp nhất; các lựa chọn D07 |
| TB G3 08 v0.1 | Trạng thái kiểm chứng mới nhất; hợp đồng không đổi so với 07 |

Thứ tự đối chiếu: yêu cầu và thay đổi đã được duyệt → quyết định thiết kế đã được duyệt phù hợp yêu cầu → hợp đồng cùng phiên bản → tài liệu lịch sử. Khi các nguồn được duyệt mâu thuẫn, lập thay đổi và xử lý phần chịu ảnh hưởng; không mặc định bản mới nhất luôn thắng.

Tệp 08_So_Xac_nhan_Dinh_Huy_v0_1.docx là nơi ghi ý kiến riêng theo 42 mã; không tạo thêm phiếu xác nhận trùng trong quyển này. Giữ phân biệt G2-D07 với G3-D07-01…08. Việc tuyển chọn hồ sơ không thay thế quyết định của hai thành viên.

Sau khi xử lý ý kiến: cập nhật tài liệu chịu ảnh hưởng, kiểm tra truy vết, ghi ngoại lệ còn mở và lập biên bản khóa thiết kế. Bộ context và skills cho code chỉ được xây từ đường cơ sở đã được xác nhận khi có yêu cầu triển khai.
