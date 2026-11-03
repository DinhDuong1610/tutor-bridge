# TutorBridge

Nền tảng kết nối phụ huynh với gia sư và quản lý việc học kèm. Tài liệu bắt đầu từ [docs/README.md](docs/README.md); quy tắc cho người và agent ở [AGENTS.md](AGENTS.md).

## Cấu trúc
| Thư mục | Nội dung | Trạng thái |
|---|---|---|
| `contracts/` | OpenAPI đang dùng | Có |
| `infra/` | Docker Compose cho máy phát triển | Có |
| `docs/` | Yêu cầu, thiết kế, quy trình | Có |
| `backend/` | Spring Boot, migration Flyway | TB-03 |
| `mobile/`, `web/`, `packages/` | React Native, Next.js, API client | TB-05 |
| `ai-worker/` | Python worker | Sau Lát 1 |

## Phiên bản
| Công cụ | Phiên bản | File |
|---|---|---|
| Java | 25 LTS | `.java-version` |
| Node.js | 26 LTS | `.node-version` |
| Python | 3.14 | `.python-version` |
| PostgreSQL | 17 kèm pgvector | `infra/compose.yaml` |

## Thiết lập sau khi clone
Yêu cầu: Git, Docker.

```bash
git config core.hooksPath .githooks
cp .env.example .env
docker compose --env-file .env -f infra/compose.yaml up -d --wait
```

Đổi `POSTGRES_PASSWORD` trong `.env` trước lần chạy đầu. Dừng bằng `docker compose --env-file .env -f infra/compose.yaml down`; thêm `-v` để xóa dữ liệu.

## Làm việc
Nhánh chính là `dev`. Quy trình ở [docs/process/git-workflow.md](docs/process/git-workflow.md).
