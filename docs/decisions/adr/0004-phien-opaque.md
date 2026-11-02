# ADR-0004 Phiên opaque tra trạng thái

**Trạng thái:** Đã chấp nhận (G3-D16-02). **Chủ trì:** Đính.

## Vấn đề
Thu hồi phiên, khóa tài khoản và đổi mật khẩu phải có hiệu lực ở yêu cầu kế tiếp. JWT dài hạn không đáp ứng được nếu không có danh sách thu hồi.

## Quyết định
Token opaque, chỉ lưu digest ở máy chủ, tra trạng thái và `auth_epoch` mỗi yêu cầu. Mobile dùng kho bảo mật của hệ điều hành; web quản trị dùng cookie Secure, HttpOnly kèm CSRF. Quản trị có thêm yếu tố xác thực.

## Hệ quả
- Mỗi yêu cầu có một lần đọc phiên. Đây là truy vấn chạy thường xuyên nhất, nên phải theo khóa chính.
- Khi lớn, thêm cache ngắn hạn cho phiên chỉ khi có số đo và phải giữ cách thu hồi tức thời. Không đổi sang JWT dài hạn để né tải.
