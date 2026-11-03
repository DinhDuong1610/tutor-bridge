# Thiết lập GitHub

Làm một lần khi tạo repo. Đính thực hiện vì là admin (`DinhDuong1610`). Gói GitHub Free theo CR-010, Gitflow theo CR-011. Repo: https://github.com/DinhDuong1610/tutor-bridge

## Repo
- [x] Repo private, tên `tutor-bridge`
- [x] **Đặt nhánh mặc định là `dev`**, để `Closes #<số>` tự đóng Issue khi merge vào `dev`
- [x] Bật Squash merge và Merge commit, tắt Rebase merge. Squash cho `feature/`, `bugfix/`; merge commit cho `release/`, `hotfix/`
- [x] Bật tự xóa nhánh sau khi merge. Không dùng `main` hoặc `dev` làm nhánh nguồn của PR
- [x] Bật Issues, Milestone M0–M7
- [ ] GitHub Project: cần `gh auth refresh -s project`
- [ ] Bật 2FA cho tài khoản
- [ ] Bật Dependabot alerts. Cập nhật phiên bản đã cấu hình ở `.github/dependabot.yml`
- [ ] Bí mật CI lưu ở GitHub Secrets, không có khóa production

## Commit đầu tiên
Commit đầu chứa tài liệu là lần duy nhất push thẳng lên `main` và tạo `dev`, làm trước khi bật hook. Từ commit sau mọi thay đổi qua PR vào `dev`.

```bash
git config core.hooksPath .githooks
git update-index --chmod=+x .githooks/pre-push
```

## Thêm Huy
Cần làm **trước PR code đầu tiên**, vì nếu không sẽ không có người duyệt chéo.

- [ ] Thêm Huy làm collaborator, quyền ghi
- [ ] Huy chạy lệnh `git config core.hooksPath .githooks`
- [ ] Bật 2FA cho tài khoản của Huy

## Bảng công việc
Cột: Chưa làm, Sẵn sàng, Đang làm, Chờ review, Xong. Issue chỉ vào "Sẵn sàng" khi có đủ chủ task, mã FR/UC/AC và tiêu chí xong.

## Người phải xem theo vùng
GitHub Free không ép CODEOWNERS trên repo private, nên đây là quy ước.

| Đường dẫn | Người duyệt |
|---|---|
| `contracts/`, migration, `docs/decisions/`, `docs/context/` | Cả hai đọc, người không phải tác giả duyệt |
| `AGENTS.md`, `.claude/`, `.agents/`, `docs/playbooks/` | Cả hai đọc, người không phải tác giả duyệt |
| Giao dịch trong mô-đun `classes` | Đọc từng dòng |
| `ai-worker/` | Huy chủ trì |
| `web/` | Đính chủ trì |

## Khi nâng lên GitHub Pro
Bật bảo vệ `main` và `dev`: bắt buộc PR, 1 người duyệt, bỏ duyệt cũ khi có commit mới, CI đạt, cấm force push, áp dụng cả với admin. Quy trình còn lại không đổi.
