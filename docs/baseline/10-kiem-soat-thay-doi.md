# Kiểm soát thay đổi và tham chiếu

> Nguồn: TB G2 02 v1.1 (`docs/source-docx/04_G2_PRD_SRS_Hop_nhat_v1_1.docx`), giữ nguyên văn. Đọc kèm [change-requests](../decisions/change-requests.md).

## 13 Kiểm soát thay đổi và tài liệu tham chiếu

## 13.1 Điều kiện còn lại và quản trị phạm vi cơ sở

Bảng 17 Điều kiện quản trị và phát hành

| Mã | Điều kiện và chủ trì | Mốc phải hoàn thành |
|---|---|---|
| GATE01 | Dương Đính và Đức Huy xác nhận D01–D08, phân công, giờ/tuần, ngân sách và mốc mong muốn; ghi xác nhận bằng văn bản. | Trước đóng G2 chính thức và chốt kế hoạch G3. |
| GATE02 | Dương Đính chủ trì xác thực nhu cầu, tuyển mẫu, quyền dữ liệu và nội dung thông báo/điều khoản, người giám sát trẻ; Đức Huy rà nguồn học liệu. | Trước thu thập/dùng dữ liệu người dùng thật trong thí điểm. |
| GATE03 | Đức Huy chọn/cấu hình nhà cung cấp sau thử nghiệm; Dương Đính duyệt trần chi bằng tiền, xử lý dữ liệu và phân công xác minh/thẩm định/hỗ trợ. | Trước bật AI trên nguồn thật và trước tiếp nhận thí điểm. |
| GATE04 | Cả hai hoàn tất thiết kế G3, sản phẩm P0/P1, NFR/AT và đánh giá theo mục 10; ghi kết quả go/no go. | Trước phát hành R0, không phải kết quả đã đạt tại G2. |

Mỗi thay đổi có mã CR, lý do/bằng chứng, yêu cầu bị tác động, tác động quyền/lịch/nguồn/chi phí, phương án và người quyết định. Hai thành viên ghi kết quả trước nhập phạm vi cơ sở mới. Bản 1.1 cho làm rõ không đổi phạm vi; thay đổi phạm vi/chính sách trọng yếu tăng phiên bản và đánh giá lại phần bị ảnh hưởng.

## 13.2 Lịch sử phiên bản

Phiên bản 1.0 ngày 29/10/2026: hợp nhất hai nguồn G2; lựa chọn phạm vi cơ sở; 43 FR, 16 UC với 64 AC, 16 NFR, 20 AT; bổ sung quyền, trạng thái, dữ liệu, đánh giá, backlog và điều kiện nghiệm thu. Báo cáo đi kèm: TB G2 03 v1.1. Phiên bản 1.1 ngày 29/10/2026: chuẩn hóa văn phong và hình thức báo cáo phục vụ thẩm định; giữ nguyên mã yêu cầu và trạng thái nghiệm thu.

## 13.3 Tài liệu tham chiếu

[S1] Nhóm TutorBridge. Khởi động giai đoạn G2 và đề xuất yêu cầu sản phẩm. TB G2 01, phiên bản 0.1, ngày 25/10/2026.

[S2] Đức Huy. PRD_TutorBridge.docx. Bản được cung cấp cho lần hợp nhất ngày 29/10/2026, không ghi phiên bản trong tệp.

[S3] Nhóm TutorBridge. Báo cáo tổng kết G0 G1 hợp nhất. TB G01 01, phiên bản 1.1, ngày 25/10/2026.

[T1] OWASP. Authorization Cheat Sheet. Truy cập ngày 29/10/2026. https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html

[T2] NIST. SP 800-63B-4 Digital Identity Guidelines Authentication and Authenticator Management. Truy cập ngày 29/10/2026. https://pages.nist.gov/800-63-4/sp800-63b.html

[T3] Grafana. k6 Thresholds. Truy cập ngày 29/10/2026. https://grafana.com/docs/k6/latest/using-k6/thresholds/

T1 hỗ trợ nguyên tắc quyền tối thiểu và kiểm tra từng yêu cầu; T2 tham khảo chính sách mật khẩu; T3 tham khảo cách viết điều kiện đạt bài tải. Các số đo, trọng số, ngưỡng đánh giá và chính sách lưu ở đây là giá trị cơ sở của TutorBridge, chưa là kết quả thực nghiệm hay chứng nhận tuân thủ các nguồn trên.

## 13.4 Cách sử dụng nguồn tham chiếu

Thứ tự ưu tiên là yêu cầu được nhóm xác nhận tại phạm vi cơ sở mới nhất, sau đó đến ghi nhận gốc trong S1–S3. Nguồn kỹ thuật giúp thiết kế và kiểm tra nguyên tắc; không thay thế yêu cầu nghiệp vụ hoặc bằng chứng chạy thực tế. Khi nguồn đầu vào khác nhau, dùng nhật ký D và CR để ghi lựa chọn thay vì duy trì hai cách hiểu song song.
