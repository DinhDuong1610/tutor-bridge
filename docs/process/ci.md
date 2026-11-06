# CI

Dặn agent bằng tài liệu là chưa đủ. CI chặn những gì bắt buộc đúng. Workflow ở `.github/workflows/`, script kiểm tra ở `scripts/ci/` chạy được cả trên máy.

## Kiểm tra bắt buộc trên mỗi PR
| Kiểm tra | Chặn điều gì | Trạng thái |
|---|---|---|
| `skills-sync` | `.claude/skills` và `.agents/skills` lệch nhau | Có, `repo.yml` |
| `immutable-history` | Sửa `docs/appendix/`, `docs/source-docx/` hoặc migration đã merge | Có, `repo.yml` |
| `no-handoff` | Còn file `docs/handoff/TB-*.md` khi PR đã Ready | Có, `repo.yml`, chỉ khi PR không phải Draft |
| `secrets-scan` | Khóa, mật khẩu, token trong code | Có, gitleaks trên các commit của PR |
| `pr-format` | Tiêu đề thiếu mã TB hoặc thiếu phần tác giả giải thích | Có, `repo.yml`. Bỏ qua PR của Dependabot |
| `backend-build` | Build, lint, unit test | Có, `backend.yml`. Lint là cảnh báo trình biên dịch tính như lỗi |
| `backend-integration` | Test trên PostgreSQL thật, image `pgvector/pgvector:pg17` | Có, cùng job `backend-build` |
| `migration-check` | Áp được từ DB trống và từ phiên bản trước | Một phần: từ DB trống. Từ phiên bản trước làm khi có bản phát hành đầu |
| `architecture` | Mô-đun gọi ngược chiều, dùng `internal` của nhau (INV-82) | Có, `ArchitectureTest`. Chưa kiểm được SQL đọc bảng của mô-đun khác |
| `contract-check` | Code lệch OpenAPI; đổi phá vỡ không có phiên bản | Có: `oasdiff breaking` so `contracts/openapi.yaml` với nhánh đích trong `repo.yml` (#40), lỗi mức ERR thì chặn, cảnh báo chỉ in ra; phản hồi khớp hợp đồng kiểm bằng `Contract.matches()` trong test HTTP, chạy cùng `backend-build` (#47) |
| `client-sync` | API client sinh ra lệch hợp đồng | Có, `frontend.yml` |
| `mobile-build`, `web-build` | Build, lint, typecheck, test | Có, `frontend.yml`. Mobile bundle Android; web chạy Vitest từ #55 |
| `worker-build` | Lint, test worker Python | Khi có worker |
| `dependency-scan` | Phụ thuộc có lỗ hổng nghiêm trọng | Một phần: Dependabot cập nhật hằng tuần. Cần bật Dependabot alerts trong cài đặt repo |

## Chạy trên máy
```bash
sh scripts/ci/skills-sync.sh
sh scripts/ci/immutable-history.sh origin/dev
sh scripts/ci/hooks.sh
sh scripts/ci/no-handoff.sh   # trước khi chuyển PR sang Ready
sh scripts/ci/client-sync.sh  # sau khi đổi contracts/openapi.yaml
sh scripts/ci/contract-check.sh origin/dev  # cần oasdiff trên PATH hoặc Docker đang chạy
npm run lint && npm run typecheck && npm test
```

## Quy tắc
- Action pin theo commit SHA, image pin theo digest. Dependabot đề xuất bản mới qua PR.
- Workflow chỉ có quyền đọc. Tiêu đề và nội dung PR đi qua biến môi trường, không chèn thẳng vào lệnh.
- Chỉ chạy kiểm tra của phần có thay đổi, để tiết kiệm phút CI. `backend.yml` chạy khi đổi `backend/`, `infra/db-init/` hoặc nguồn migration. Mọi test backend, gồm test đồng thời, chạy mỗi lần như vậy.
- Không bỏ qua, không tắt kiểm tra để merge. Test chập chờn phải sửa, không chạy lại cho qua.
- CI chạy trên PR vào `dev` và `main`. CI đỏ trên `dev` là việc ưu tiên số một.

## Định kỳ
| Việc | Tần suất |
|---|---|
| Test tải k6 trên môi trường thử | Trước mỗi lần lên nấc tải |
| Diễn tập khôi phục | Hằng tháng từ khi có dữ liệu cần giữ |
| Cập nhật phụ thuộc | Hằng tuần qua Dependabot |
