# CI

Dặn agent bằng tài liệu là chưa đủ. CI chặn những gì bắt buộc đúng. File workflow viết khi dựng repo, vì phụ thuộc phiên bản công cụ.

## Kiểm tra bắt buộc trên mỗi PR
| Kiểm tra | Chặn điều gì | Khi nào có |
|---|---|---|
| `skills-sync` | `.claude/skills` và `.agents/skills` lệch nhau | Ngày đầu |
| `immutable-history` | Sửa `docs/appendix/`, `docs/source-docx/` hoặc migration đã merge | Ngày đầu |
| `no-handoff` | Còn file `docs/handoff/TB-*.md` khi PR đã Ready | Ngày đầu |
| `secrets-scan` | Khóa, mật khẩu, token trong code | Ngày đầu |
| `pr-format` | Tiêu đề thiếu mã TB hoặc thiếu phần tác giả giải thích | Ngày đầu |
| `backend-build` | Build, lint, unit test | Khi có backend |
| `backend-integration` | Test trên PostgreSQL thật, image `pgvector/pgvector:pg17` | Khi có backend |
| `migration-check` | Áp được từ DB trống và từ phiên bản trước | Khi có backend |
| `architecture` | Mô-đun đọc bảng của nhau, gọi ngược chiều (INV-82) | Khi có backend |
| `contract-check` | Code lệch OpenAPI; đổi phá vỡ không có phiên bản | Khi có backend |
| `client-sync` | API client sinh ra lệch hợp đồng | Khi có client |
| `mobile-build`, `web-build` | Build, lint, typecheck, test | Khi có từng phần |
| `worker-build` | Lint, test worker Python | Khi có worker |
| `dependency-scan` | Phụ thuộc có lỗ hổng nghiêm trọng | Khi có backend |

## Quy tắc
- Chỉ chạy kiểm tra của phần có thay đổi, để tiết kiệm phút CI. Test đồng thời luôn chạy khi đụng `classes`.
- Không bỏ qua, không tắt kiểm tra để merge. Test chập chờn phải sửa, không chạy lại cho qua.
- CI chạy trên PR vào `dev` và `main`. CI đỏ trên `dev` là việc ưu tiên số một.

## Định kỳ
| Việc | Tần suất |
|---|---|
| Test tải k6 trên môi trường thử | Trước mỗi lần lên nấc tải |
| Diễn tập khôi phục | Hằng tháng từ khi có dữ liệu cần giữ |
| Cập nhật phụ thuộc | Hằng tuần qua Dependabot |
