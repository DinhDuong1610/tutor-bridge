# Trạng thái và dữ liệu nghiệp vụ

> Nguồn: TB G2 02 v1.1 (`docs/source-docx/04_G2_PRD_SRS_Hop_nhat_v1_1.docx`), giữ nguyên văn. Đọc kèm [change-requests](../decisions/change-requests.md).

## 5 Trạng thái và dữ liệu nghiệp vụ

## 5.1 Trạng thái cam kết lớp và lịch

Bảng 4 Chuyển trạng thái lớp và lịch

| Đối tượng | Chuyển hợp lệ | Chủ thể và điều kiện |
|---|---|---|
| Nhu cầu | Nháp → xác nhận; sửa → bản nháp mới | Phụ huynh. Bản đã xác nhận không ghi đè; chỉ bản hiệu lực được gửi mới. |
| Đề nghị | Chờ → chấp nhận / từ chối / hủy / hết hạn | Gia sư đích chấp nhận/từ chối; phụ huynh hủy; máy chủ hết hạn. Chấp nhận chỉ sau khi giao dịch được xác nhận lịch/lớp. |
| Lớp | Chờ bắt đầu → hoạt động → kết thúc; chờ bắt đầu → kết thúc | Tạo sau chốt. Đến buổi đầu chuyển hoạt động; một bên kết thúc có lý do, hủy lịch tương lai. Không mở lại âm thầm. |
| Buổi | Đã xác nhận → chờ xác nhận kết quả → hoàn thành / vắng / có phản hồi | Gia sư ghi, phụ huynh xác nhận. Hoàn thành cần đủ xác nhận; bất đồng không tự hoàn thành. |
| Buổi tương lai | Đã xác nhận → hủy | Một bên hủy có lý do; giải phóng lịch. Đã hủy không chuyển trực tiếp hoàn thành. |
| Đề nghị đổi | Chờ → áp dụng / từ chối / hết hạn / không thể áp dụng | Bên kia đồng ý; kiểm tra phiên bản và xung đột. Chỉ áp dụng mới thay buổi cũ. |
| Mục tiêu lớp | Đề xuất → đồng ý / từ chối / hủy | Hai bên đồng ý cùng phiên bản thì mục tiêu mới có hiệu lực; mục tiêu cũ còn truy vết. |

Mọi chuyển trạng thái khác bị từ chối và trả trạng thái hiện tại. Khi hai lệnh tranh chấp, chỉ lệnh hợp lệ trên phiên bản hiện hành được xác nhận. Tạo lớp, buổi và thông báo có hiệu lực nhất quán; không để trạng thái đã chấp nhận khi chưa có lịch hợp lệ.

## 5.2 Trạng thái học liệu và nội dung

Bảng 5 Chuyển trạng thái học liệu và nội dung

| Đối tượng | Trạng thái | Điều kiện chuyển |
|---|---|---|
| Tài liệu | Tiếp nhận → xử lý → chờ kiểm tra → sẵn sàng; hoặc lỗi; thu hồi/xóa | Sẵn sàng chỉ sau trích xuất và gia sư xác nhận dùng nguồn. Thu hồi/xóa chặn truy cập/tác vụ mới ngay. |
| Tác vụ AI | Chờ → đang xử lý → có nháp / lỗi / hủy | Hạn mức và quyền hợp lệ; trước lưu/trả kiểm tra lại. Không tự chuyển nháp thành đã duyệt. |
| Kế hoạch | Nháp → đã duyệt → công bố → thay thế / tạm ẩn | Gia sư duyệt riêng phiên bản. Công bố cần đúng buổi và nguồn còn quyền; sửa tạo nháp mới. |
| Nhật ký | Nháp → gia sư công bố; tham gia: chờ / đồng ý / có phản hồi | Hai trục trạng thái riêng; hết 72 giờ không tự đồng ý. Chỉ bản đủ xác nhận được dùng AI. |
| Bài giao | Nháp → đã giao → đóng; sửa tạo phiên bản | Chỉ nội dung đã duyệt được giao; đóng chặn nộp. Mở đáp án là cờ/quyền riêng do gia sư quyết định. |
| Bài nộp | Chưa nộp → đã nộp → đã kiểm tra; nộp lại → bản mới | Gắn tác giả thao tác và học sinh; giữ nhận xét của từng bản, phân biệt muộn. |
| Xóa dữ liệu | Tiếp nhận → xác minh → chặn quyền → xử lý → hoàn tất / cần bổ sung | Có danh sách kho và dẫn xuất; không báo hoàn tất nếu còn truy cập được bản bị thu hồi. |
| Hỗ trợ | Mới → đang xử lý ↔ chờ bổ sung → giải quyết → đóng | Có chủ trì, lý do và kết quả; mở lại giữ lịch sử. |

Trạng thái xử lý tệp, tác vụ AI, phê duyệt chuyên môn và quyền công bố là các trạng thái khác nhau. Không dùng một cờ thành công để suy ra tất cả bước đã hoàn tất.

## 5.3 Từ điển dữ liệu sản phẩm nhóm một

Đây là hợp đồng dữ liệu ở mức yêu cầu, không phải schema vật lý. ID do máy chủ gán; thời điểm có múi giờ, tiền là số nguyên VND, chuỗi được kiểm tra chiều dài và dữ liệu được phân loại theo quyền.

Bảng 6 Dữ liệu tài khoản và thỏa thuận

| Đối tượng | Trường bắt buộc và giới hạn | Bất biến |
|---|---|---|
| Tài khoản | ID, email duy nhất, tên hiển thị 1–100, vai trò, trạng thái xác minh/khóa; phiên có hạn và trạng thái thu hồi. | Không lưu mật khẩu rõ; role đặc biệt không nhận từ form đăng ký. |
| Học sinh/liên kết | ID, tên hiển thị 1–100, khối, người quản lý; liên kết học sinh P1 có trạng thái/thời điểm xác nhận. | Một chủ quản ở R0; biết ID/email không đủ để tạo quyền. |
| Gia sư | ID, môn/khối, mô tả 50–3.000 ký tự, tháng kinh nghiệm 0–600, giá nguyên dương, hình thức, phiên bản duyệt. | Thông tin tự khai tách kết quả xác minh; chứng cứ gốc không công khai. |
| Nhu cầu | Học sinh, mục tiêu 20–2.000 ký tự, môn/khối, hình thức, ngân sách >0, D=60/90/120, buổi/tuần 1–3, ngày bắt đầu, lịch 28 ngày, phiên bản. | Chỉ bản xác nhận hiện hành dùng gửi; khu vực không bắt buộc trực tuyến. |
| Lịch rảnh | Chủ lịch, ngày/thứ, start/end, múi giờ, ngoại lệ, phiên bản/xác nhận; các khoảng ưu tiên tùy chọn. | Start < end; ngoại lệ và buổi đã đặt được trừ; không sửa lịch cam kết. |
| Kết quả matching | Request/profile/config version, bộ lọc, s/e/a/S hoặc B, loại AI/phương án thay thế, thứ tự và lý do. | Lý do từ dữ liệu; không lưu mã nguồn bên ngoài quyền hoặc suy diễn phẩm chất. |
| Đề nghị/lớp | Các bên, bản mục tiêu, giá/buổi, D, các buổi cụ thể, expiry, trạng thái, phiên bản, khóa thao tác. | Một nhu cầu một lớp; cả chuỗi buổi chốt nguyên tử. |

## 5.4 Từ điển dữ liệu sản phẩm nhóm hai

Bảng 7 Dữ liệu học tập và quyền

| Đối tượng | Trường bắt buộc và giới hạn | Bất biến |
|---|---|---|
| Buổi/nhật ký | Lớp, giờ kế hoạch/thực tế, trạng thái; nội dung/nhận xét mỗi phần ≤5.000 ký tự; xác nhận riêng, phiên bản. | Không xác nhận thay; buổi vắng/hủy không thành hoàn thành; sửa có lịch sử. |
| Tài liệu/đoạn | Owner/uploader, source, quyền sử dụng, môn/khối/chủ đề, scope, file/version/hash, loại/kích thước, trạng thái, đoạn/vị trí. | 10 MiB, 100 trang PDF/200.000 ký tự; 20 bản đang hoạt động/lớp. Thay tệp không thay nguồn cũ. |
| Yêu cầu AI | Lớp, chủ đề 1–500, D, phiên bản mục tiêu/nguồn, nhận xét hợp lệ, cấu hình, tác vụ/mã thao tác, phiên bản quyền. | Tối đa 5 tài liệu; chỉ nguồn sẵn sàng và có quyền; không gửi tên/liên hệ không cần thiết. |
| Kế hoạch | Mục tiêu, ôn tập, danh sách hoạt động/phút, bài tập, đáp án dự kiến, nguồn, loại AI/thủ công, cảnh báo, duyệt/công bố. | Tổng phút=D; mã nguồn thuộc tập truy xuất; bài tạo mới có nhãn; đáp án tách quyền. |
| Bài giao/nộp | Class/session/plan version, nội dung, due/closed, đáp án mở; student/actor, text≤10.000, tối đa 5 ảnh, mỗi ảnh 5 MiB, phiên bản/nhận xét. | Đề và bản nộp gắn phiên bản; không đè lần cũ; trả payload không chứa đáp án chưa mở. |
| Sự kiện/audit | Mã thao tác, actor/object/version, thời điểm/kết quả, trace; analytics dùng mã giả và loại sự kiện. | Không ghi mật khẩu/token/nội dung nguyên văn; hạn lưu riêng theo BR08. |
| Quyền/yêu cầu xóa | Chủ thể, scope, người cấp, expiry, revoke, lý do, trạng thái/kết quả và các kho ảnh hưởng. | Áp dụng cả nguồn/chỉ mục/cache/dẫn xuất/backup; restore phải áp lại danh sách xóa. |

Mỗi lỗi xác thực, thiếu quyền, sai phiên bản, xung đột, quá hạn mức hoặc thiếu nguồn có mã ổn định, thông báo tiếng Việt, trường liên quan và hành động tiếp theo. Không trả stack trace, tệp riêng hoặc thông tin người khác trong lỗi.
