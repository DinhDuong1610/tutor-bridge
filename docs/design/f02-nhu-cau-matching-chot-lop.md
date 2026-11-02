# F02 — Nhu cầu, matching và chốt lớp

> Nguồn: TB G3 TH 01 v0.1 (`docs/source-docx/06_G3_Thiet_ke_Hop_nhat_de_Ra_soat_v0_1.docx`), giữ nguyên văn. Đọc kèm [change-requests](../decisions/change-requests.md) và [ADR](../decisions/adr/).
> **Lưu ý:** chưa có nơi lưu embedding cho điểm s, xem ADR-0006.

Căn cứ: TB G3 03 mục 6; PRD/SRS BR02–BR03

### Giao dịch chốt lớp F02

F02 giữ nguyên điều kiện giá VND trên 60 phút, ngân sách phụ huynh trên mỗi buổi; giá buổi làm tròn lên theo ceil(rate × D / 60). Thời lượng 60, 90 hoặc 120 phút; tần suất 1–3 trong mỗi khối 7 ngày; chu kỳ đầu 28 ngày.

| Giao dịch | Xử lý bắt buộc |
|---|---|
| TX01–TX02 | Lưu nháp và xác nhận khác nhau. Bản xác nhận cần đủ dữ liệu cứng; ngày đầu từ hôm nay đến hôm nay cộng 28 theo giờ Việt Nam; từng buổi ở tương lai. |
| TX03 | Lọc đúng phạm vi, tài khoản, hồ sơ, giá và lịch cả chuỗi trước chấm điểm. Tối đa 5 hồ sơ thật; không nới điều kiện. AI lỗi chỉ sang B khi phụ huynh chủ động chọn. |
| TX04 | Khóa nhu cầu; kiểm phiên bản và giá hiện hành; cố định mục tiêu, giá, đầy đủ buổi. Hạn phản hồi là nhỏ hơn giữa 48 giờ và giờ bắt đầu buổi đầu. Chờ chưa giữ chỗ. |
| TX05 bước 1 | Kiểm actor; khóa receipt rồi need và offer; khóa calendar và tài nguyên theo thứ tự ổn định. Tái kiểm tài khoản, phê duyệt, giá, phiên bản và tất cả khoảng lịch. |
| TX05 bước 2 | Tạo một lớp, mục tiêu đầu, toàn bộ buổi và hai booking mỗi buổi; accept đề nghị; kết thúc đề nghị cạnh tranh; đóng need; ghi outbox và receipt trong cùng transaction. |
| TX05 bước 3 | Một buổi lỗi thì rollback toàn bộ. Chỉ trả thành công sau commit. Exclusion và unique là chốt DB qua hai API replica; không thay bằng khóa RAM hoặc nút bị vô hiệu. |
| TX06 | Hủy, từ chối và hết hạn chỉ áp dụng đề nghị chờ. Chấp nhận là đồng ý nguyên bản; thay điều kiện cần đề nghị mới. |

### Công thức và nhãn kết quả

S = 0.60s + 0.25e + 0.15a; B = 0.625e + 0.375a. s = clip((cos + 1) / 2, 0, 1); e = min(tháng kinh nghiệm, 60) / 60; a là tỷ lệ khung ưu tiên khớp, không có ưu tiên thì 0.5. Làm tròn 6 chữ số; hòa S ưu tiên s, e, ID gia sư tăng; hòa B ưu tiên e, ID tăng. Điểm không phải xác suất tiến bộ học tập.

Chốt lớp phải kiểm lại toàn chuỗi lịch và điều khoản tại commit; gợi ý không giữ chỗ. Khoảng thời gian [start, end) cho phép các buổi sát biên; booking bảo vệ cả tài nguyên gia sư và học sinh.
