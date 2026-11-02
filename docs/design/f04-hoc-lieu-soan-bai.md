# F04 — Học liệu, soạn bài và công bố

> Nguồn: TB G3 TH 01 v0.1 (`docs/source-docx/06_G3_Thiet_ke_Hop_nhat_de_Ra_soat_v0_1.docx`), giữ nguyên văn. Đọc kèm [change-requests](../decisions/change-requests.md) và [ADR](../decisions/adr/).

Căn cứ: TB G3 03 mục 7, 9; TB G3 04 mục 2

### Giao dịch học liệu và bài soạn F04

| Giao dịch | Xử lý và điều kiện chuyển trạng thái |
|---|---|
| TX07 | Upload riêng theo ticket hạn tối đa 60 giây. Đếm cả UPLOADING và PROCESSING trong giới hạn 20 phiên bản hoạt động mỗi lớp. Xác minh object thật trước ghi nhận hoàn tất; dọn orphan có đối soát. |
| TX08 | PDF có văn bản hoặc DOCX tối đa 10 MiB; PDF tối đa 100 trang, tối đa 200.000 ký tự trích xuất. Từ chối OCR, macro, mã hóa, tệp hỏng hoặc giả MIME. Gia sư kiểm văn bản và công thức trước READY. |
| TX09 | Khóa nguồn, thu hồi grant, tăng epoch, ghi ledger và outbox trong cùng commit. Chặn dẫn xuất ở lần đọc, duyệt, công bố và trả kết quả AI; dọn cache/index bất đồng bộ. |
| TX10 | Cố định lớp, buổi, mục tiêu, topic, thời lượng, tối đa 5 nguồn và các phiên bản nhận xét đủ xác nhận. Reserve quota, tiền và queue trước tạo job. |
| TX11 | Lease và fencing kiểm soát worker. Kiểm lại nguồn tại retrieval, gửi provider, nhận/lưu/trả kết quả. AI lỗi có soạn thủ công; kết quả muộn không đè nháp đã sửa. |
| TX12 | Nháp có revision. Bản đã duyệt hoặc công bố được sửa bằng phiên bản mới. Nguồn phụ thuộc bao phủ cả nội dung lấy từ nguồn, kể cả khi không có citation. |
| TX13–TX14 | Gia sư duyệt đúng bản sau kiểm Toán, nguồn, cảnh báo và tổng phút bằng thời lượng. Công bố kiểm lại quyền rồi thay con trỏ nguyên tử cùng outbox và receipt. |
| TX15–TX16 | Đọc theo DTO và quyền hiện hành. Đối soát thao tác chỉ trong scope actor; kết quả cũ bị mất quyền không được trả lại nội dung riêng. |

Không coi upload, extraction/index sẵn sàng, duyệt nguồn, duyệt bài và công bố là cùng trạng thái. DB và kho tệp là quy trình theo chặng có cleanup, không được mô tả là một giao dịch phân tán nguyên tử.

Lớp chờ bắt đầu hoặc đang học được soạn; lớp đã kết thúc không nhận AI hoặc công bố mới. Soạn trước buổi đầu không cần nhật ký giả. Nhật ký đưa vào context phải có gia sư xác nhận chuyên môn, phụ huynh xác nhận tham gia và không tranh chấp.

### Ma trận quyền và vòng đời nguồn

| Chủ thể | Đề nghị và lớp | Nháp và đáp án | Công bố và nguồn |
|---|---|---|---|
| Phụ huynh | Đúng con mình; gửi/hủy đề nghị; đọc lớp của con. | Không đọc nháp hoặc đáp án dự kiến của gia sư. | Đọc công bố còn quyền dẫn xuất; không tự có quyền gốc. |
| Gia sư | Nhận đề nghị gửi đến; đúng lớp được giao. | Soạn, sửa, duyệt và công bố trong trạng thái hợp lệ. | Quản lý nguồn mình tải; cấp/thu hồi theo căn cứ và phạm vi. |
| Học sinh P1 | Liên kết chủ động còn hiệu lực; chỉ lớp mình. | Không đọc nháp hoặc đáp án chưa mở. | Đọc công bố theo quyền hiện hành; quyền gốc tách riêng. |
| Người ngoài lớp | Không có quyền từ việc biết ID. | Không có quyền. | Không có quyền. |
| Quản trị | Metadata phục vụ quản trị đúng vai trò. | Không mặc định đọc nội dung riêng. | Nội dung hỗ trợ cần vụ việc và grant đúng phạm vi, tối đa 24 giờ. |
| Worker | Danh tính dịch vụ và scope job. | Chỉ context đã tối thiểu hóa, không tự công bố. | Kiểm epoch và quyền ở mọi mốc; chỉ ghi kết quả với fencing hợp lệ. |

### Tác động thu hồi

Công bố và thu hồi khóa cùng phiên bản nguồn. Nếu công bố commit trước, thu hồi sau đó khiến lần đọc tiếp theo bị chặn; nếu thu hồi commit trước, công bố bị từ chối. Middleware đọc kiểm quan hệ lớp, trạng thái nguồn, grant, thời hạn và epoch trước khi trả nội dung hoặc cache.

Phụ huynh có quyền đọc dẫn xuất vẫn xem công bố hợp lệ khi không được xem tệp gốc. Ngược lại, quyền đọc dẫn xuất không cho phép đọc đoạn nguồn thô. Khi bỏ nguồn, phải loại nội dung chịu ảnh hưởng rồi duyệt bản mới; đổi nhãn sang thủ công không tự xóa nghĩa vụ quyền.

Thu hồi không thu lại được byte đã truyền hoặc bản người dùng đã tải. API gốc dùng proxy xác thực cho từng request, kể cả range, không phát URL GET công khai hoặc dài hạn. Cleanup bất đồng bộ phải được theo dõi mà không trì hoãn chặn đọc.

### Các sửa ràng buộc được tiếp nhận

### Phát hiện và thay đổi đã thực hiện

### R01 Nguồn sẵn sàng thiếu số đo văn bản

READY vẫn nhận extracted_characters = NULL. CHECK cho phép kết quả NULL; điều kiện > 0 chưa bắt buộc NOT NULL. Nguồn thiếu số đo văn bản có thể được coi là sẵn sàng nếu lớp service bỏ sót.

Khắc phục: 003 thêm ready_requires_text_count. Kiểm tra hồi quy SQL08 nhận SQLSTATE 23514 sau sửa; trên bản nền thao tác sai vẫn được chấp nhận.

### R02 Thay đổi quan hệ buổi học và lớp

Có thể đổi class_id của buổi chưa được bảng khác tham chiếu; booking giữ thông tin cũ. Trigger đồng bộ chỉ theo during/state; chưa khóa định danh và lớp của buổi. Quan hệ buổi và lớp có thể sai, dẫn đến lịch đặt không khớp đối tượng của lớp mới.

Khắc phục: 003 thêm protect_session_identity và trigger trước UPDATE. Kiểm tra hồi quy SQL09 nhận SQLSTATE 23514 sau sửa; trên bản nền thao tác sai vẫn được chấp nhận.

### R03 Vượt số nguồn khi cập nhật bối cảnh

Có thể chuyển nguồn thứ sáu vào context bằng UPDATE. Giới hạn tối đa năm nguồn chỉ được kiểm khi INSERT. Context vượt phạm vi nguồn đã thiết kế và giả định giới hạn xử lý.

Khắc phục: 003 mở guard cho INSERT và UPDATE OF context_id. Kiểm tra hồi quy SQL11 nhận SQLSTATE 23514 sau sửa; trên bản nền thao tác sai vẫn được chấp nhận.

| Mã | Phạm vi | Bằng chứng và trạng thái |
|---|---|---|
| R01 | E12 / UC10 / TX08 | SQL08; đã sửa và kiểm chứng trên engine nhúng; chờ rà soát chéo. |
| R02 | E10 / UC06 UC07 / TX05 | SQL09; đã sửa và kiểm chứng trên engine nhúng; chờ rà soát chéo. |
| R03 | E15 / UC11 / TX10 | SQL11; đã sửa và kiểm chứng trên engine nhúng; chờ rà soát chéo. |

Đây là các điểm hở ở tầng ràng buộc của bản thiết kế, chưa phải kết luận về lỗ hổng API đang chạy. Lớp service theo thiết kế vẫn phải kiểm quyền, quan hệ, trạng thái và khóa trong cùng transaction. Các sửa được bổ sung ở migration 003; bản nền được giữ nguyên để tái hiện và truy vết.
