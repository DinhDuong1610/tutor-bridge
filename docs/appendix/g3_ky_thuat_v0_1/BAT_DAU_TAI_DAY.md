# Thực thi kiểm chứng G3 trên máy nhóm

TB G3 08 v0.1, 29/10/2026. Gói này dựa trên TB G3 07 và bổ sung bộ chạy đã sửa, kiểm tra bằng chứng và hướng dẫn thực hiện. **Chưa có kết quả PostgreSQL máy chủ trong gói.**

## Đính thực hiện

1. Giải nén toàn bộ ZIP vào một thư mục riêng có quyền ghi, ví dụ `D:\TutorBridge_G3_Check`. Không chạy trực tiếp bên trong cửa sổ ZIP.
2. Cài Python 3.11 trở lên và Docker Desktop; mở Docker Desktop tới khi Engine chạy. Chọn Docker local, không dùng context máy chủ từ xa. Lần đầu cần Internet để tải thư viện và image.
3. Mở `RUN_NATIVE.cmd`. Công cụ tạo môi trường Python riêng, sinh bí mật test tại máy, tạo PostgreSQL tạm ở cổng 55434, chạy kiểm chứng, đánh giá JSON rồi dừng đúng dự án test nó vừa tạo.
4. Mở thư mục `evidence` bằng VS Code. Kiểm `execution_status.json`; nếu có native result thì đọc thêm `native_results.json` và `native_evaluation.json`.
5. Gửi lại ba tệp JSON đó để đối chiếu. Nếu chưa tạo native result, gửi `execution_status.json` trước. Không gửi thư mục `secrets`, mật khẩu hoặc cấu hình chứa khóa.

Gói chỉ tự tạo tài nguyên test local; không xóa DB có sẵn. Nếu dự án test đã tồn tại, công cụ dừng để bạn kiểm tra. Dữ liệu test là tmpfs và mất khi container dừng. Không đổi tên DB/DSN hoặc trỏ runner vào môi trường thật.

## Huy thực hiện cùng Đính

- Rà `review/reconciliation.csv` và các quyết định D35/D16 từ hồ sơ trước; điền ý kiến thật theo từng mã, không chỉ đánh dấu tất cả đã xong.
- Dùng `review/user_test_protocol.md` và `review/user_testing.csv` để tổ chức phiên thử tối thiểu 5 người, mục tiêu ít nhất 4 người hoàn thành tác vụ chính mà không được hướng dẫn từng bước.
- F01/F06 hiện vẫn có wireframe tĩnh; chỉ ghi khả năng hiểu màn hình, không gọi là hoàn thành luồng tương tác khi chưa có bản mẫu thao tác tương ứng.
- Điền số giờ/tuần, ngân sách và mốc trong `review/nguon_luc_va_xac_nhan.csv` trước khóa kế hoạch G4.

## Đọc trạng thái

| Trạng thái | Ý nghĩa |
|---|---|
| BLOCKED_OR_FAILED | Thiếu môi trường hoặc bước chạy lỗi; xem reason và log được chỉ định |
| NOT_ACCEPTED | Đã chạy nhưng kết quả hoặc bằng chứng chưa đạt |
| DB_COMPONENT_PASS | Đủ ca thành phần DB của bộ chạy; vẫn chưa đóng G3 |

`DB_COMPONENT_PASS` yêu cầu đủ 64 ca SQL và 5 ca máy chủ, gồm 4 ca quan sát chờ khóa và 1 ca ranh giới tuần tự. Không thay thế HTTP ACL, AI, Android, tải, restore hoặc kết quả người dùng.

## Bằng chứng hiện có

- `evidence/embedded_results.json` kế thừa TB G3 07: 64/64 ca trên PostgreSQL 17.5 nhúng; không phải kết quả native mới.
- `evidence/harness_results.json`: 12 kiểm tra logic bộ chạy với dữ liệu giả/mock; không kết nối PostgreSQL.
- `evidence/execution_status.json`: lần thử môi trường soạn bị chặn do không có Docker; không đánh dấu native đã chạy.
- `evidence/static_validation.json`: kiểm cấu trúc API/SQL kế thừa bản hợp nhất, không chạy lại cho thay đổi Python này.

Migration 001–005 và OpenAPI giữ nguyên byte so với TB G3 07; thay đổi lần này nằm ở native runner, launcher, bộ đánh giá và phiếu thực hiện. README kỹ thuật cũ nằm ở `README_BASELINE.md`; hướng dẫn chạy mới ưu tiên tài liệu này.
