# Nhật ký hợp nhất

Tất cả quyết định cần kết luận của nhóm trước khóa baseline.

## D07-01 ReviewInput trùng hai ý nghĩa

Giữ ReviewInput cho duyệt bài soạn; đổi nhánh nhận xét bài nộp thành SubmissionReviewInput; viết lại toàn bộ ref.

Trạng thái: Đã sửa ứng viên; chờ nhóm rà

## D07-02 Problem và prefix khác nhau

Dùng Problem từ F02/F04 và traceId thống nhất; máy chủ placeholder /api/v1. correlationId chỉ là tên gọi trong văn xuôi.

Trạng thái: Đã sửa ứng viên; chờ nhóm rà

## D07-03 Quyền cấp tạm còn rộng

005 bảo thủ: chủ sở hữu nguồn cấp VIEW_ORIGINAL cho người hỗ trợ được giao; không tải/AI/đáp án. Mở rộng cần quyết định và ca quyền mới.

Trạng thái: Đề xuất cần Đính/Huy kết luận

## D07-04 Minh chứng chưa có hợp đồng upload

Thêm ticket/complete/read theo người xác minh được giao; đề xuất tối đa 10 tệp, mỗi tệp 5MiB, PDF/JPEG/PNG. Chưa có storage service.

Trạng thái: Đề xuất cần Đính/Huy kết luận

## D07-05 Trạng thái yêu cầu bổ sung không khớp DB

NEEDS_INFO ở lệnh duyệt giữ tutor_verifications=PENDING với reason; không thêm enum vào bảng cũ hoặc dùng nhãn VERIFIED cho bản mới.

Trạng thái: Ánh xạ ứng viên; chưa chạy service

## D07-06 PUT đầu tiên không có ETag

GET hồ sơ/lịch lấy ETag; tạo lần đầu dùng If-None-Match:*; sửa dùng If-Match. Hai trường hợp cần kiểm tra server riêng.

Trạng thái: Hợp đồng đã bổ sung

## D07-07 Lời mời chưa có đường tạo tài khoản học sinh

Chỉ token lời mời hợp lệ mới khởi tạo STUDENT; phải xác minh email riêng rồi accept; tạo account chưa liên kết hoặc tiêu thụ lời mời.

Trạng thái: Thiết kế ứng viên; backend chưa có

## D07-08 Hoàn tất xóa dễ bị hiểu quá mức

ACTIVE_STORES_PURGED cần 6 kho; CLOSED cần backup hết hạn và bằng chứng export ledger. DB không tự xác minh bằng chứng bên ngoài.

Trạng thái: Ràng buộc DB đã thử; kho ngoài chưa thử