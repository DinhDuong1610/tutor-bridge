# TB-1 Monorepo và nền chung

- **Chủ task:** Đính
- **Nhánh:** `feature/TB-1-monorepo-foundation`
- **Agent đang làm:** Claude
- **Cập nhật lần cuối:** 03/11/2026
- **Mã liên quan:** CR-005a, CR-011, TB-01 trong kế hoạch Lát 1

## Mục tiêu
Đưa repo về nền chung: dòng kết thúc LF, hợp đồng đang dùng, database local, phiên bản công cụ, lệnh trong AGENTS.md.

## Trạng thái hiện tại
Đã xong phần việc. PR Draft đã mở vào `dev`, chờ Đính viết phần tác giả tự giải thích, Codex rà và Huy duyệt.

## Lệnh kiểm tra
```bash
cp .env.example .env
docker compose --env-file .env -f infra/compose.yaml up -d --wait
docker compose --env-file .env -f infra/compose.yaml exec db pg_isready
docker compose --env-file .env -f infra/compose.yaml down -v
git ls-files --eol .githooks/pre-push   # phải là i/lf w/lf
```

## Quyết định đã chọn
- Java 25 LTS: LTS mới nhất, hỗ trợ tới 2031.
- Node.js 26: vừa thành LTS; Node 24 đã hết giai đoạn hỗ trợ chủ động.
- Python 3.14: không chọn 3.15 vì mới phát hành.
- Expo SDK 57: SDK 58 vừa ra, Expo Go chưa sẵn sàng khi kiểm tra.

## Câu hỏi còn mở
Huy cần rà các phiên bản này trong PR.

## Bước kế tiếp
Đính viết phần tự giải thích trong PR, cho Codex rà theo `review-pr`, xóa file này rồi chuyển Ready.
