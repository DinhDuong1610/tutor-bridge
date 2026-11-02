# Giao dịch service cần triển khai

Các trigger/hàm mới hỗ trợ kiểm tính đúng; không nhận principal từ session tự động và không thay ACL service. Tất cả endpoint riêng kiểm account/session, role/capability, quan hệ đối tượng và trạng thái; fail closed nếu không kiểm được.

## Reset mật khẩu
BEGIN, khóa account, consume token đúng purpose/TTL một lần, thay hash, tăng auth_epoch, revoke mọi session, ghi audit tối thiểu và outbox xác thực; COMMIT. Không tự mở khóa account. Nếu consume trả NULL thì rollback, không đổi credential. Hash mật khẩu thực hiện bằng thư viện đã chọn; không dùng chuỗi hash minh họa ở fixture làm mật khẩu thật.

## Nhận hoặc thu hồi liên kết học sinh
Khóa parent account, student và invite/link theo quy ước ổn định. Kiểm email đã xác minh và role STUDENT; consume invite, cập nhật link, tăng revision, thêm link_event/audit/outbox cùng commit. Thu hồi theo cùng thứ tự khóa. Bài nộp đã lưu giữ actor/revision; lần đọc/nộp mới kiểm link hiện hành.

## Quyền hỗ trợ
Khóa lớp nếu có, case, request rồi material_version theo thứ tự dùng chung. Chỉ assignee có capability SUPPORT được xin. Owner xác nhận không tự cấp cho support, nguồn còn hiệu lực. Cấp grant VIEW_ORIGINAL <=24giờ, receipt/audit cùng commit. Đọc proxy gọi kiểm phiên và valid_support_grant, sau đó kiểm nguồn trước stream. Khóa dùng trong trigger chỉ hỗ trợ thành phần, cần kiểm tranh chấp/deadlock native trước khóa thứ tự giao dịch.

## Quyền và xóa dữ liệu
Kiểm chủ thể và tái xác thực, khóa quan hệ gốc, ghi revocation/deletion ledger, chặn read/job/publication, tạo privacy request cùng sáu store task và outbox trong giao dịch. Account có lớp hoạt động điều phối kết thúc lớp/booking; khi lỗi giữ quyền chặn và tác vụ cần xử lý. Worker không chạy trong transaction dài bao trùm I/O. Từng task có checkpoint/retry an toàn. Không báo CLOSED khi backup chưa hết hạn hoặc chưa có bằng chứng ledger ngoài DB. Giữ minimal tombstone khi phải bảo toàn FK; xóa/ẩn danh nội dung, định danh và object theo phạm vi được duyệt.

## Hồ sơ lịch và mục tiêu
Thay chuyên môn tạo version mới, không mang nhãn duyệt cũ sang dữ liệu mới. Giá/lịch mới không đổi thỏa thuận lớp. Calendar version immutable; materialize lịch thành cửa sổ UTC hữu hạn, kiểm BOOKING tại commit nhận lớp. GoalVersion chỉ EFFECTIVE khi đúng hai bên duyệt cùng bản; giữ bản cũ trong lúc đề xuất. Retry receipt phải kiểm quyền trước trả, tránh lộ nội dung đã thu hồi.
