# F03 — Đổi lịch, hủy, học bù và kết thúc

> Nguồn: TB G3 TH 01 v0.1 (`docs/source-docx/06_G3_Thiet_ke_Hop_nhat_de_Ra_soat_v0_1.docx`), giữ nguyên văn. Đọc kèm [change-requests](../decisions/change-requests.md) và [ADR](../decisions/adr/).

Căn cứ: TB G3 05 mục 9, 14

### Trạng thái và bất biến của lịch lớp

| Đối tượng | Chuyển trạng thái | Điều kiện |
|---|---|---|
| Đề nghị | PENDING → APPLIED | Bên còn lại chấp thuận; đúng phiên bản; còn hạn; cả hai lịch hợp lệ. |
| Đề nghị | PENDING → REJECTED hoặc CANCELLED | Từ chối hoặc người đề nghị rút; không đổi lịch gốc. |
| Đề nghị | PENDING → EXPIRED | Đã đến hạn theo giờ máy chủ; không tự áp dụng. |
| Đề nghị | PENDING → NOT_APPLICABLE | Buổi bị hủy, lớp kết thúc, phiên bản cũ hoặc lịch mới không còn khả dụng. |
| Buổi | SCHEDULED → CANCELLED | Một bên hủy tương lai có lý do; giải phóng cả hai booking. |
| Lớp | WAITING hoặc ACTIVE → ENDED | Một bên kết thúc có lý do; giữ lịch sử; không mở lại tự động. |

Đổi lịch chỉ áp dụng buổi chưa bắt đầu; thời lượng vẫn theo thỏa thuận 60, 90 hoặc 120 phút. Khoảng lịch là [bắt đầu, kết thúc), đệm học trực tuyến bằng 0. Xét xung đột theo cả học sinh và gia sư, bao gồm các lớp khác trong cùng tài nguyên.

Hạn đổi lịch bằng thời điểm sớm hơn giữa lúc tạo cộng 48 giờ và giờ bắt đầu buổi gốc. Chi tiết đề xuất cho học bù là thời điểm sớm hơn giữa lúc tạo cộng 48 giờ và giờ bắt đầu buổi mới; cần xác nhận D35–01. Không đặt giới hạn số lần học bù ngoài PRD/SRS.

Buổi đã bắt đầu hoặc đã qua không được viết lại lịch kế hoạch. Mọi hiệu chỉnh đi qua lịch sử kiểm toán theo F06. Học bù không sửa buổi gốc thành hoàn thành. Thay đổi lịch tăng revision và buộc công việc soạn bài đang chạy kiểm lại ngữ cảnh.

### Giao dịch lịch và kết thúc lớp

| Mã | Giao dịch | Đọc khóa và kết quả nguyên tử |
|---|---|---|
| TX17 | Tạo đề nghị đổi hoặc bù | Kiểm bên tham gia, trạng thái, revision và hạn; ghi request, receipt và outbox. Chưa đặt booking mới. |
| TX18 | Phản hồi đề nghị | Khóa lớp, buổi và request; khóa tài nguyên theo thứ tự cố định; kiểm thời hạn và revision; đổi cả hai booking, session, lịch sử, request, receipt và outbox trong một commit. |
| TX19 | Hủy buổi tương lai | Khóa lớp và buổi; kiểm start > now; ghi lý do; vô hiệu cả hai booking và request liên quan; receipt cùng outbox. |
| TX20 | Kết thúc lớp | Khóa lớp; ghi endedAt và revision; hủy buổi start > now, hết hiệu lực request, đóng bài mở, chặn công việc AI theo lớp; receipt và outbox cùng commit. |

TX18 tạo buổi mới khi học bù, có makeupForId cùng lớp; không thay đổi thời gian hoặc kết quả buổi gốc. Đổi lịch cập nhật cùng session và tăng revision. Mọi xung đột, lỗi ghi booking hoặc kiểm tra cuối giao dịch phải rollback toàn bộ thay đổi lịch; booking cũ còn hiệu lực.

### Thứ tự khóa và gửi lại

Thứ tự chung là lớp, thực thể nghiệp vụ, tài nguyên theo khóa ổn định rồi dữ liệu phụ. Worker dùng cùng quy ước; thử lại deadlock hoặc serialization có giới hạn và đúng khóa idempotency. Không coi khóa mô phỏng trong JavaScript là bằng chứng cho khóa nhiều phiên PostgreSQL.

Receipt định danh theo actor, loại lệnh và Idempotency-Key, có hash payload chuẩn hóa. Gửi lại cùng nội dung trả kết quả đã commit; khác nội dung báo xung đột. Khi mất phản hồi, operation lookup kiểm quyền hiện hành và trả biên nhận tối thiểu, không làm lộ nội dung đã bị thu hồi.
