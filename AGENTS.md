# TutorBridge

Nền tảng kết nối phụ huynh với gia sư và quản lý việc học kèm. Hai thành viên: Dương Đính, Đức Huy. File này áp dụng cho mọi agent và mọi người trong dự án.

**Trạng thái:** đã xong tài liệu, chưa có mã sản phẩm. Tiến độ ở [docs/README.md](docs/README.md).

## Đọc gì trước khi làm
1. [docs/context/invariants.md](docs/context/invariants.md): bắt buộc đọc mỗi task.
2. [docs/context/r0a-scope.md](docs/context/r0a-scope.md): việc này có trong phạm vi không.
3. [docs/context/module-map.md](docs/context/module-map.md): code đặt ở đâu, bảng nào của ai.
4. [docs/context/conventions.md](docs/context/conventions.md): cách viết code và test.
5. Đúng UC của task trong `docs/baseline/use-cases/`, cùng `docs/baseline/02-quy-tac-nghiep-vu.md` và file luồng `docs/design/f0X-*.md`.
6. [docs/context/glossary.md](docs/context/glossary.md) khi gặp thuật ngữ.

Không đọc cả bộ tài liệu. `docs/design/chi-tiet/` và `docs/source-docx/` chỉ tra khi nguồn trên thiếu.

## Thứ tự ưu tiên khi mâu thuẫn
1. Yêu cầu đã xác nhận và CR có hiệu lực (`docs/baseline/`, `docs/decisions/change-requests.md`)
2. Quyết định thiết kế đã xác nhận (`docs/decisions/`)
3. Hợp đồng đang dùng (`contracts/`, thư mục migration của backend). Trước khi dựng repo là `docs/appendix/` và `docs/design/migrations-proposed/`
4. Tài liệu lịch sử

Tài liệu mới hơn không tự động thắng. Mâu thuẫn giữa các nguồn đã xác nhận thì dừng phần bị ảnh hưởng và báo lại.

## Quy tắc cứng
- **Thiếu quyết định thì dừng và hỏi.** Ghi `TBD` kèm câu hỏi. Không tự chọn nghiệp vụ, không tự mở rộng phạm vi.
- **Không báo xong khi chưa chạy.** Nêu lệnh đã chạy và kết quả thật. Test hỏng thì nói hỏng.
- **Không làm yếu ràng buộc để test qua.** Không xóa ca kiểm thử, không hạ tiêu chí.
- **Không giả dữ liệu.** Không dùng dữ liệu mẫu làm kết quả AI, không viết kết quả khảo sát, số đo hay năng lực chưa có bằng chứng.
- **Không ghi xác nhận thay người.** Sổ quyết định chỉ ghi điều Đính hoặc Huy đã nói.
- **Không sửa** `docs/appendix/`, `docs/source-docx/` hoặc migration đã merge. Thay đổi đi bằng migration mới hoặc CR.
- **Không đưa dữ liệu trẻ thật, mật khẩu, token, khóa API** vào repo, log, prompt hay ví dụ.
- **Đổi OpenAPI hoặc migration** phải theo playbook `contract-change`, là PR riêng, merge trước phần code dùng nó.
- **Không commit lên `main` hoặc `dev`.** Làm trên nhánh theo [Gitflow](docs/process/git-workflow.md), PR vào `dev`. Không push hay tạo PR khi người dùng chưa yêu cầu.

## Quy trình
Dùng playbook trong [docs/playbooks/](docs/playbooks/README.md): bắt đầu bằng `start-task`, cập nhật `handoff` sau mỗi commit. Agent khác hoặc người khác phải tiếp tục được chỉ từ repo, không cần lịch sử chat.

## Lệnh
```bash
# Database local, cần file .env tạo từ .env.example
docker compose --env-file .env -f infra/compose.yaml up -d --wait
docker compose --env-file .env -f infra/compose.yaml down
```

```bash
# Backend, cần Docker cho Testcontainers. Lần đầu Gradle tự tải JDK 25
cd backend && ./gradlew test

# Chạy local: migration bằng tài khoản migrator, ứng dụng bằng tutorbridge_app
set -a; . ../.env; set +a
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

Đổi `infra/db-init/` thì cần tạo lại DB local bằng `down -v`, vì script chỉ chạy trên thư mục dữ liệu trống.

Build, test, lint của phần còn lại được bổ sung cùng task dựng phần đó.
