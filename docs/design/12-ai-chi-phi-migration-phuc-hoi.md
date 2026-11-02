# AI, chi phí, migration và phục hồi

> Nguồn: TB G3 TH 01 v0.1 (`docs/source-docx/06_G3_Thiet_ke_Hop_nhat_de_Ra_soat_v0_1.docx`), giữ nguyên văn. Đọc kèm [change-requests](../decisions/change-requests.md) và [ADR](../decisions/adr/).
> **Lưu ý:** giới hạn hai tác vụ AI là giới hạn vận hành R0, không phải giới hạn thiết kế, xem ADR-0005 và ADR-0006.

Căn cứ: TB G3 06 mục 10–11; cập nhật hợp nhất TB G3 07

### AI chi phí và vận hành tác vụ

Matching giữ thuật toán S và đối chứng B theo BR03, không tuyên bố trọng số tối ưu. Tách lọc điều kiện cứng khỏi xếp hạng; lưu phiên bản đầu vào và cấu hình để phát lại. Thiếu ứng viên khác với lỗi dịch vụ. Chưa thay model hoặc nhà cung cấp bằng suy đoán.

AI soạn bài chỉ nhận tập nguồn có quyền và phiên bản cụ thể. Trước đọc nguồn, trước gọi dịch vụ, trước lưu/trả và trước duyệt đều kiểm lại quyền, lớp và revision. Nội dung đầu vào bỏ tên, email, địa chỉ và dữ liệu không cần thiết. Đáp án có DTO và endpoint riêng.

Giới hạn cơ sở: tối đa hai tác vụ AI toàn hệ thống và một tác vụ mỗi gia sư; nhận tác vụ p95 ≤2 giây, có kết quả hoặc lỗi rõ trong 120 giây kể cả chờ. Reservation quota và ngân sách phải được ghi nguyên tử giữa các worker; timeout không có nghĩa nhà cung cấp chưa tính phí.

Đề xuất sổ chi phí gồm reserve, settle, release và UNKNOWN. Chỉ giải phóng phần dự toán khi biết kết quả; lỗi không rõ chi phí giữ khoản dự phòng tới đối soát. Trần ngày/tháng cần xác nhận bằng tiền và phiên bản cấu hình trước bật AI. AI mặc định tắt nếu chưa có ngân sách, nhà cung cấp và quyết định xử lý dữ liệu; soạn thủ công tiếp tục hoạt động.

| Tình huống | Hành vi | Bằng chứng cần có |
|---|---|---|
| Hết quota hoặc trần tiền | Chặn trước gọi; hiện lý do; giữ nháp | Hai worker tranh quota và lệnh thử lại |
| Dịch vụ timeout hoặc lỗi | Kết thúc có trạng thái; không thử lại vô hạn | Log mã giả, deadline, chi phí và số lần gọi |
| Nguồn bị thu hồi | Không trả nội dung; đánh dấu dẫn xuất | Thu hồi ở từng điểm của pipeline |
| Sai cấu trúc hoặc nguồn | Giữ lỗi thật; không tự công bố | Gia sư sửa và duyệt; thẩm định Toán độc lập |

### Migration sao lưu và quy trình phát hành

Chuỗi thiết kế dữ liệu hiện hành đã hợp nhất 001–005; mọi thay đổi tiếp theo cần đối chiếu trước khi mở rộng. Dùng expand, backfill, validate rồi enforce; không sửa tệp migration đã áp dụng. Chạy trên DB trống và bản sao có dữ liệu cũ, ghi thời gian khóa và đối soát số bản ghi. Không dùng rollback phá dữ liệu làm phương án phục hồi mặc định.

CI đề xuất kiểm lint, unit nghiệp vụ trọng yếu, hợp đồng API, migration và tích hợp PostgreSQL máy chủ. Kiểm bí mật và dependency là điều kiện xuất bản build. Staging tách khóa, DB và bucket khỏi dữ liệu thật; không chép nội dung học sinh sang môi trường phát triển.

Mục tiêu phục hồi theo NFR11 là RPO ≤1 giờ, RTO ≤4 giờ cho DB và tệp nhất quán. Đề xuất backup nền cộng nhật ký DB, object version và manifest nhất quán; chu kỳ thực tế phải chứng minh được RPO. Backup mã hóa giữ 7 ngày; khóa giải mã tách bản sao lưu và có người chịu trách nhiệm.

| Bước phục hồi | Thao tác | Điều kiện mở dịch vụ |
|---|---|---|
| 1 Cách ly | Khóa ghi; chọn mốc DB/tệp; phục hồi vào môi trường kín | Không phục vụ người dùng trong lúc kiểm |
| 2 Áp quyền hiện hành | Đọc ledger xóa/thu hồi bền vững ngoài snapshot cũ | Áp tombstone, epoch và quyền trước mọi read/job |
| 3 Đối soát | Kiểm booking, object, nguồn, dẫn xuất; dựng lại index | Không phục hồi dữ liệu bị xóa từ cache hoặc index |
| 4 Kiểm tra và mở | Chạy ca âm; đo RPO/RTO; ghi người quyết định | Đạt kiểm quyền/xóa; có biên bản và kế hoạch theo dõi |

Các mục tiêu lưu giữ, xóa và phục hồi là giá trị cơ sở của dự án, cần được rà soát chuyên môn trước dữ liệu thật. Chưa chọn nhà cung cấp/model AI hoặc khóa trần tiền; không suy từ việc có đặc tả rằng AI thật đã được bật hay kiểm chứng.
