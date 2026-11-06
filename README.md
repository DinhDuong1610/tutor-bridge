# TutorBridge

Nền tảng kết nối phụ huynh với gia sư và quản lý việc học kèm. Tài liệu bắt đầu từ [docs/README.md](docs/README.md); quy tắc cho người và agent ở [AGENTS.md](AGENTS.md).

## Cấu trúc
| Thư mục | Nội dung | Trạng thái |
|---|---|---|
| `contracts/` | OpenAPI đang dùng | Có |
| `infra/` | Docker Compose cho máy phát triển | Có |
| `docs/` | Yêu cầu, thiết kế, quy trình | Có |
| `backend/` | Spring Boot, migration Flyway | Có |
| `mobile/`, `web/`, `packages/` | React Native (Expo), Next.js, API client và gói dùng chung | Có, đã có nền |
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

Cần thêm Node.js 26 (đúng `.node-version`) cho mobile và web. JDK 25 do Gradle tự tải.

```bash
# Backend: http://localhost:8080, health ở /actuator/health
cd backend && set -a && . ../.env && set +a && SPRING_PROFILES_ACTIVE=local ./gradlew bootRun

# Mobile và web: cài một lần từ thư mục gốc
npm ci
cp mobile/.env.example mobile/.env.local && cp web/.env.example web/.env.local
npm run start --workspace @tutorbridge/mobile   # Expo
npm run dev --workspace @tutorbridge/web        # http://localhost:3000

# Kiểm tra như CI
cd backend && ./gradlew test                    # cần Docker
npm run lint && npm run typecheck && npm test
```

Cách viết code từng phần: [docs/context/conventions.md](docs/context/conventions.md), cùng `mobile/AGENTS.md` và `web/AGENTS.md`.

## Làm việc
Nhánh chính là `dev`. Quy trình ở [docs/process/git-workflow.md](docs/process/git-workflow.md).
