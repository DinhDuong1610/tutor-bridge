# Phạm vi và tình trạng thiết kế

> Nguồn: TB G3 TH 01 v0.1 (`docs/source-docx/06_G3_Thiet_ke_Hop_nhat_de_Ra_soat_v0_1.docx`), giữ nguyên văn. Đọc kèm [change-requests](../decisions/change-requests.md) và [ADR](../decisions/adr/).

Căn cứ: TB G2 02 v1.1; TB G3 07 v0.1; báo cáo rà soát tổng thể v0.1

R0 tập trung học kèm trực tuyến 1–1 Toán lớp 8 đại số, bằng tiếng Việt; Android cho phụ huynh/gia sư, web quản trị và giới thiệu. Học sinh và bài tập ở P1 vẫn thuộc R0 đầy đủ; R0a chỉ là bản nội bộ. R0 không xử lý thanh toán, chat, OCR thời khóa biểu hoặc chấm bài AI.

### Kết luận và phạm vi hợp nhất

Đã hợp nhất hợp đồng API và chuỗi schema dữ liệu ứng viên cho sáu luồng F01–F06. Thiết kế hiện hành có OpenAPI 3.1.1, chuỗi schema 001–005, từ điển vật lý, quan hệ khóa ngoại, bảng truy vết 43 FR và bằng chứng kiểm tra lịch sử. Hồ sơ đủ để tổ chức phiên rà soát kỹ thuật tổng thể; chưa phải kết luận nghiệm thu G3.

OpenAPI hiện có 123 thao tác trên 108 đường dẫn, 138 schema. Chuỗi DDL tạo 77 bảng trên môi trường PostgreSQL 17.5 nhúng. Sau hợp nhất, 64/64 ca SQL đạt, gồm 28 ca hồi quy F02/F04 và 36 ca bổ sung F01/F06. Kiểm tra cấu trúc OpenAPI, tham chiếu nội bộ và tám ca biên payload đã đạt.

F01/F06 còn wireframe tĩnh; F02/F04 và F03/F05 có bản mẫu mô phỏng. Chưa có xác nhận riêng của Huy, thử người dùng hoặc bằng chứng nghiệm thu G3. Ưu tiên hiện tại là rà và hoàn thiện tài liệu; phân bổ lại tiêu chí kiểm chứng sang các giai đoạn phải được hai thành viên xác nhận tại XC04/XC05.
