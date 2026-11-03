# TB-5 Khung mobile, web, API client

- **Chủ task:** Đính
- **Nhánh:** `feature/TB-5-app-skeletons`, tách từ `feature/TB-2-ci`
- **Agent đang làm:** Claude
- **Cập nhật lần cuối:** 03/11/2026
- **Mã liên quan:** ADR-0001, Issue #5

## Mục tiêu
Khung mobile và web chạy được, dùng API client sinh từ OpenAPI, có CI.

## Trạng thái hiện tại
Đã xong phần việc, chưa push.

## Lệnh kiểm tra
```bash
npm ci
npm run lint && npm run typecheck && npm test   # mong đợi 3 test client, 1 test mobile
sh scripts/ci/client-sync.sh
(cd mobile && npm run bundle) && (cd web && npm run build)
```

## Kết quả chạy gần nhất
- 03/11/2026, Node 26.12.0 sau khi cài sạch bằng `npm ci`: lint, typecheck đạt; 3 test client, 1 test mobile đạt; bundle Android và build web đạt.
- `client-sync` thử cả ca đạt và ca hợp đồng đổi mà chưa sinh lại. actionlint không lỗi.
- Typecheck của client phát hiện backend thiếu `detail` bắt buộc trong Problem. Đã sửa ở TB-4 (`4dc512d`) và merge xuôi chuỗi nhánh.

## Quyết định đã chọn
- npm workspaces, không dùng pnpm: Expo hỗ trợ sẵn, không cần thêm công cụ. `.npmrc` khóa bản chính xác và bắt đúng Node.
- React 19.2.3 cho cả mobile và web, theo Expo SDK 57, để chỉ có một bản React trong monorepo.
- TypeScript 6.0 cho mobile theo Expo; 5.9 cho web và client vì `openapi-typescript` 7.13 chỉ hỗ trợ TypeScript 5.
- Bỏ font Google của template web: tải mạng lúc build và không chắc có dấu tiếng Việt.
- Giữ `mobile/AGENTS.md`, `web/AGENTS.md` của Expo và Next.js, thêm `CLAUDE.md` trỏ tới. Bỏ `mobile/.claude/settings.json` vì bật plugin chỉ có ở Claude.
- Chưa có tên gói Android, chưa có điều hướng: thuộc TB-10.

## Câu hỏi còn mở
- `npm audit` báo 13 lỗ hổng, 2 mức cao, đều nằm trong công cụ dòng lệnh của Expo (`zod`, `minimatch`, `glob`). `npm audit fix --force` sẽ hạ Expo về SDK 51 nên không dùng. Chờ Expo vá, Dependabot sẽ báo.
- Máy local dùng Node 26 bản portable ở `~/.tutorbridge-tools/`. Đính và Huy nên cài Node 26 chính thức.

## Bước kế tiếp
Push, mở PR sau chuỗi trước, xem `frontend.yml` chạy thật.
