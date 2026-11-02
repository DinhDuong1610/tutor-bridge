# Tóm tắt và quy ước

> Nguồn: TB G2 02 v1.1 (`docs/source-docx/04_G2_PRD_SRS_Hop_nhat_v1_1.docx`), giữ nguyên văn. Đọc kèm [change-requests](../decisions/change-requests.md).
> **Lưu ý:** phần trạng thái ở đây ghi tại ngày 29/10/2026. Trạng thái hiện hành ở sổ quyết định.

## Tóm tắt điều hành

## Mục tiêu và đối tượng sử dụng

Hồ sơ PRD và SRS trình bày giá trị sản phẩm, phạm vi thí điểm, yêu cầu chức năng và điều kiện chất lượng của TutorBridge. Tài liệu phục vụ Hội đồng đánh giá, đại diện khách hàng và nhóm triển khai trong quá trình thẩm định, thống nhất yêu cầu và thiết kế giải pháp.

## Phạm vi sản phẩm

TutorBridge hỗ trợ phụ huynh tìm gia sư và theo dõi lớp học; hỗ trợ gia sư chuẩn bị nội dung từ học liệu được phép sử dụng. Phạm vi cơ sở tập trung vào học kèm trực tuyến một gia sư với một học sinh, môn Toán lớp 8, chủ đề đại số, sử dụng tiếng Việt. Hai năng lực cần kiểm chứng là gợi ý gia sư có căn cứ và tạo bản nháp bài soạn có nguồn để gia sư kiểm tra, phê duyệt.

## Cấu trúc hồ sơ yêu cầu

Hồ sơ gồm 43 yêu cầu chức năng, 16 trường hợp sử dụng, 64 tiêu chí nghiệm thu, 16 yêu cầu phi chức năng và 20 kịch bản kiểm thử dự kiến. Ma trận quyền, trạng thái, từ điển dữ liệu và bảng truy vết hỗ trợ chuyển yêu cầu sang thiết kế và kiểm thử. Nội dung chi tiết được tổ chức từ mục 1 đến mục 13.

## Trạng thái và nội dung trình xem xét

Kết quả rà soát hồ sơ là Đạt có điều kiện. Các quyết định D01–D08, phân công và nguồn lực cần được Dương Đính và Đức Huy xác nhận trước khi đóng G2 chính thức. Chỉ tiêu hiệu năng, chất lượng AI và giá trị sản phẩm là điều kiện cần kiểm chứng; kết quả khảo sát và kiểm thử được ghi nhận trong các báo cáo thực hiện sau này.

## Kiến nghị triển khai

Sử dụng hồ sơ làm cơ sở khởi động thiết kế G3, đồng thời hoàn tất xác nhận phạm vi và kế hoạch nguồn lực. Việc tiếp nhận dữ liệu thực tế, sử dụng dịch vụ AI trả phí và phát hành thí điểm tuân theo các điều kiện kiểm soát được quy định tại mục 13.1.

## Quy ước mã và mức ưu tiên

FR-01 đến FR-38 giữ thứ tự yêu cầu trong PRD [S2], chuẩn hóa cách viết mã; FR-39 đến FR-43 bổ sung phần khởi động còn thiếu. UC là trường hợp sử dụng; AC là tiêu chí kiểm chứng của UC; NFR là yêu cầu phi chức năng; AT là kịch bản kiểm thử liên thông. K FR là mã của hồ sơ G2 v0.1, không trùng với mã chính thức ở đây.

P0 là lõi cần làm trước. P1 là phần tiếp theo nhưng vẫn bắt buộc cho R0 đầy đủ; muốn cắt phải lập thay đổi phạm vi. P2 nằm ngoài R0. R0a là bản kiểm chứng nội bộ; R0 là thí điểm kín; R1 là phát hành giới hạn; R2 là mở rộng. Các mốc R0–R5 của PRD [S2] được đổi sang nhóm backlog E1–E6 để tránh trùng tên lần phát hành.

PRD mô tả giá trị và phạm vi; SRS mô tả hành vi và chất lượng. Phạm vi cơ sở là tập yêu cầu được xác nhận và kiểm soát thay đổi. RAG là truy xuất nguồn rồi sinh nội dung. RPS là số yêu cầu/giây; p95 là mức không bị vượt bởi 95% mẫu; RPO là khoảng mất dữ liệu mục tiêu; RTO là thời gian khôi phục mục tiêu.
