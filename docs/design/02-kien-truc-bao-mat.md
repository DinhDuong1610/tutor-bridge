# Kiến trúc tổng thể và bảo mật

> Nguồn: TB G3 TH 01 v0.1 (`docs/source-docx/06_G3_Thiet_ke_Hop_nhat_de_Ra_soat_v0_1.docx`), giữ nguyên văn. Đọc kèm [change-requests](../decisions/change-requests.md) và [ADR](../decisions/adr/).
> **Lưu ý:** kiến trúc hiện hành xem [kien-truc.md](kien-truc.md); phần này được ADR-0002 và ADR-0005 bổ sung.

Căn cứ: TB G3 06 mục 6, 9

### Kiến trúc tổng thể và ranh giới mô đun

Đề xuất Spring Boot theo kiến trúc nguyên khối chia mô đun, PostgreSQL làm nguồn dữ liệu giao dịch; React Native cho Android, Next.js cho web quản trị và giới thiệu. API và worker chạy tiến trình riêng cùng phiên bản hợp đồng. Hai API có thể dùng chung DB và kho tệp; không lưu phiên hoặc khóa nghiệp vụ chỉ trong bộ nhớ tiến trình.

Ranh giới tin cậy gồm thiết bị người dùng, cổng API, DB và kho tệp riêng, worker xử lý tệp/AI, nhà cung cấp AI và dịch vụ email xác thực. API không chuyển nguyên token sang worker hoặc nhà cung cấp. Job chỉ mang mã đối tượng và phiên bản; worker truy xuất lại theo quyền đã kiểm tra.

Thông báo dùng transactional outbox và consumer khử lặp theo event/người nhận. Hàng đợi DB là phương án đầu tiên để giảm vận hành; việc chuyển sang broker riêng cần ADR theo bằng chứng tải. Matching đọc phiên bản hồ sơ/lịch hợp lệ; lớp và lịch cam kết luôn do giao dịch PostgreSQL quyết định.

| Mô đun | Sở hữu dữ liệu | Giao tiếp |
|---|---|---|
| Identity và Profiles | Tài khoản, phiên, hồ sơ, liên kết, xác minh | F01; cung cấp principal và quan hệ |
| Matching và Classes | Nhu cầu, gợi ý, đề nghị, lớp, booking | F02/F03; khóa lịch và receipt |
| Materials và Planning | Nguồn, quyền, đoạn trích, AI, phiên bản duyệt | F04; truy xuất theo giao quyền |
| Learning | Nhật ký, xác nhận, bài tập, tiến trình | F05; bản bất biến và đáp án riêng |
| Support và Privacy | Vụ việc, quyền tạm, xóa, tombstone | F06; lệnh thu hồi và audit |
| Platform | Outbox, cấu hình, quan sát, quota | Hạ tầng dùng chung; không bỏ qua quyền mô đun |

### Bảo mật và mô hình đe dọa

Ưu tiên kiểm quyền theo đối tượng tại dịch vụ nghiệp vụ, bao gồm cả đường tải, operation lookup, worker và cache. Giao diện ẩn nút không được coi là kiểm soát quyền. Quản trị hỗ trợ và người xác minh có tập quyền riêng; thao tác nhạy cảm yêu cầu tái xác thực và có audit.

Đề xuất phiên opaque có tra trạng thái phía máy chủ cho R0 để thực hiện thu hồi ngay. Web quản trị dùng cookie Secure, HttpOnly và bảo vệ CSRF; mobile lưu bí mật phiên bằng kho bảo mật của hệ điều hành. Mật khẩu dùng hàm băm chuyên dụng có salt; thuật toán và tham số khóa ở ADR sau benchmark runtime, không tự triển khai mật mã.

| Đe dọa | Biện pháp thiết kế | Ca kiểm chứng cần chạy |
|---|---|---|
| Đổi ID để đọc con/lớp khác | Kiểm role, quan hệ, trạng thái và quyền nguồn mỗi lần | Hai gia đình, hai lớp; mọi API/tệp/job và DTO |
| Chiếm hoặc dùng lại mã reset | Digest, TTL, purpose, consume nguyên tử; chặn log | Đồng thời hai kết nối; sai purpose; token đã dùng |
| Hỗ trợ tự đọc học liệu | Grant theo vụ việc, đối tượng, hành động; audit | Không grant; hết hạn; thu hồi; tự cấp; xin đáp án |
| Prompt injection và rò lớp | Nguồn là dữ liệu; không cấp công cụ tùy ý; lọc ACL | Nguồn chứa lệnh bỏ quyền; marker riêng từng lớp |
| Tệp nguy hiểm và URL cũ | Kho cách ly; kiểm byte/loại; proxy đọc tái kiểm quyền | Sai MIME; macro; file lỗi; đọc sau revoke |
| Phục hồi làm sống lại dữ liệu | Tombstone ngoài bản backup phục hồi; cổng mở dịch vụ | Xóa rồi restore bản cũ; đối soát object và chỉ mục |
