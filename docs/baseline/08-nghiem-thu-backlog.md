# Nghiệm thu và backlog

> Nguồn: TB G2 02 v1.1 (`docs/source-docx/04_G2_PRD_SRS_Hop_nhat_v1_1.docx`), giữ nguyên văn. Đọc kèm [change-requests](../decisions/change-requests.md).
> **Lưu ý:** cột chủ trì trong backlog đã thay bằng CR-001; thứ tự đợt đầu theo CR-003.

## 11 Nghiệm thu sản phẩm và backlog

## 11.1 Điều kiện phát hành R0

Nghiệm thu G2 đánh giá hồ sơ yêu cầu; nghiệm thu R0 cần sản phẩm chạy và bằng chứng. R0 đầy đủ yêu cầu toàn bộ FR P0/P1 và các NFR liên quan đạt; không còn lỗi nghiêm trọng về quyền, lịch, mất dữ liệu, nguồn hoặc công bố chưa duyệt. Nếu P1 bị cắt phải đổi tên/phạm vi lần phát hành và cập nhật quyết định trước nghiệm thu.

Bộ AT ở hai trang tiếp theo là kế hoạch kiểm thử chưa thực thi. Mỗi ca phải lưu cấu hình/dữ liệu có quyền, phiên bản, bước chạy, kết quả mong đợi/thực tế và bằng chứng. AC của UC và NFR vẫn áp dụng, không chỉ chạy AT là đủ.

Bảng 12 Kịch bản nghiệm thu AT01 đến AT10

| Mã | Kịch bản và kết quả cần quan sát | Truy vết |
|---|---|---|
| AT01 | Luồng Toán 8 trực tuyến: xác nhận nhu cầu, lọc/gợi ý, đề nghị, chốt lớp và chuyển đúng mục tiêu; kiểm tra hai gia đình. | UC02–UC07 |
| AT02 | Ngân sách/lịch không có ai đạt: trả rỗng đúng lý do, không nới và không bổ sung hồ sơ mẫu. | UC05 |
| AT03 | N=0..6 ứng viên, có người sai điều kiện và người mới kinh nghiệm 0: đúng min(5, N), điểm/hoà điểm tính lại được. | UC03, UC05 |
| AT04 | Hai phụ huynh chốt cùng giờ gia sư và một học sinh chốt hai gia sư: mỗi tài nguyên tối đa một buổi thành công. | UC06, UC07; NFR04 |
| AT05 | PDF/DOCX hợp lệ: nguồn đúng, thời lượng đúng, sửa/duyệt mới công bố, đáp án vẫn ẩn. | UC10–UC12 |
| AT06 | Tệp scan/hỏng/quá hạn mức hoặc thiếu nguồn/công thức không đọc được: lỗi rõ, không nguồn giả. | UC10, UC11 |
| AT07 | Đổi ID lớp/tệp và thu hồi ở giữa tác vụ: không rò nguồn qua trả kết quả, cache, trích dẫn hoặc dẫn xuất. | UC10, UC16; NFR01–03 |
| AT08 | Embedding/LLM timeout/hết quota: B có nhãn, nháp còn và soạn thủ công được; không giả AI thành công. | UC05, UC11 |
| AT09 | Đổi/hủy đồng thời: giữ lịch cũ nếu đổi lỗi; hủy kết thúc đề nghị đổi, học bù giữ liên kết. | UC08; NFR04 |
| AT10 | Nộp thay, nộp muộn, nộp lại, nhận xét, mở đáp án: đúng người thao tác/phiên bản/quyền. | UC12, UC13 |

## 11.2 Các kịch bản an toàn và vận hành

Bảng 13 Kịch bản nghiệm thu AT11 đến AT20

| Mã | Kịch bản và kết quả cần quan sát | Truy vết |
|---|---|---|
| AT11 | Đăng xuất/thu hồi/khóa/khôi phục và đăng ký role giả: phiên cũ bị chặn; quản trị MFA; không lộ bí mật. | UC01, UC14 |
| AT12 | Tệp có chỉ dẫn bỏ quyền và payload AI có tên/liên hệ: không bị điều khiển, dữ liệu gửi đúng danh mục tối thiểu. | UC10, UC11; NFR06–08 |
| AT13 | Mất mạng sau khi giao dịch được xác nhận và chạy lại qua 2 API: không trùng lớp/buổi/bài nộp/duyệt; nháp không bị đè. | UC06–UC12; NFR04–05 |
| AT14 | Thiết bị/trình duyệt hỗ trợ: văn bản dài, phóng chữ 200%, bàn phím/nhãn trợ năng, rỗng/chờ/lỗi/mạng yếu. | Mục 9; NFR12 |
| AT15 | Quản trị chưa có quyền đọc học liệu; cấp tạm thời 24 giờ đúng vụ việc rồi thu hồi; mọi hành động có audit. | UC14; NFR09 |
| AT16 | 20 RPS trong 30 phút với dữ liệu chuẩn, matching riêng; ghi p95/lỗi/tài nguyên và 2 API; không suy số người đồng thời. | NFR10, NFR15 |
| AT17 | Hai tác vụ AI đồng thời, hàng chờ đầy, quá quota/trần tiền và nhà cung cấp lỗi; chặn sớm, có trạng thái cuối. | NFR13–14 |
| AT18 | Sửa mục tiêu/nhật ký/bản công bố: phiên bản cũ còn đối chiếu; bản mới cần xác nhận/duyệt; tranh chấp không thành hoàn thành. | UC04, UC09, UC11 |
| AT19 | Xóa nguồn/tài khoản rồi khôi phục DB và tệp: không sống lại quyền; dẫn xuất bị ẩn, RPO/RTO đo được. | UC16; NFR11 |
| AT20 | Tiến trình và phễu ở nhóm đủ/thiếu 7–28 ngày, N=0 và sự kiện lặp: mẫu số đúng, thiếu dữ liệu rõ. | UC13; Mục 10 |

Điều kiện chặn phát hành: bất kỳ lỗi đọc/ghi ngoài quyền, trùng lịch cam kết, công bố nháp/đáp án chưa mở, mất dữ liệu đã lưu, khôi phục quyền đã xóa, nguồn giả hoặc lỗi Toán nghiêm trọng trong bản duyệt. Hiệu năng/chi phí không đạt phải tối ưu, giảm tải thí điểm hoặc đổi phạm vi có ghi nhận; không hạ tiêu chí tính đúng.

## 11.3 Backlog cơ sở và phụ thuộc

Bảng 14 Backlog theo phụ thuộc

| Nhóm | Kết quả và phụ thuộc | Chủ trì |
|---|---|---|
| E1 P0 | UC01–UC04: quyền, tài khoản, hồ sơ, nhu cầu/mục tiêu. UC14–UC16 làm xuyên suốt, không chờ cuối. | Dương Đính; Đức Huy rà soát UX |
| E2 P0 | UC05–UC08: bộ lọc/xếp hạng, phương án đối chứng B, đề nghị, lớp/lịch, đổi/hủy. Dựa E1; dữ liệu/nhãn đánh giá chuẩn bị song song. | Dương Đính lịch; Đức Huy matching |
| E3 P0 | UC10–UC11: quyền nguồn, xử lý tệp, bối cảnh, soạn/duyệt/phương án thay thế. Dựa quyền E1 và lớp E2. | Đức Huy; Dương Đính rà soát quyền |
| E4 P0/P1 | UC09, UC12, UC13 và phần học sinh UC02: nhật ký, xác nhận, bài tập và tiến trình. Dựa E2/E3; nhánh P1 làm sau lõi. | Dương Đính nghiệp vụ; Đức Huy mobile |
| E5 P0 | NFR01–16 và AT01–20; đánh giá AI/nguồn/chi phí; backup/restore; runbook và cấu hình thí điểm. Làm kiểm thử rủi ro từ đầu. | Cả hai; rà soát chéo |
| E6 trước R0 | Phỏng vấn, tuyển mẫu/gia sư, người xác minh/thẩm định, phạm vi dữ liệu, hỗ trợ, ngân sách và quyết định go/no go. | Dương Đính điều phối; Đức Huy chuyên môn |

Đây là phân công và thứ tự cơ sở, chưa là cam kết ngày hoặc giờ của thành viên. G3 ước lượng từng lát cắt đủ điều kiện triển khai, gắn FR/UC/NFR/AC, người thực hiện và người rà soát. Không chia công việc bằng số trang hay số lượng FR ngang nhau.

## Rủi ro cần theo dõi

Thiếu nguồn cung làm rỗng danh sách: tuyển gia sư đúng phân khúc và báo độ phủ. Ngữ nghĩa không cải thiện B: giữ B có nhãn và sửa giả thuyết. Công thức trích xuất sai: dùng nguồn rõ, cho kiểm tra và thu hẹp. Chi phí/API lỗi: quota, trần tiền và soạn thủ công. Gia sư duyệt qua loa: thẩm định độc lập mẫu bản đã duyệt. Phạm vi nhiều kênh: giữ Android và web quản trị trước, chỉ mở theo chứng cứ.
