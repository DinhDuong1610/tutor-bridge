# Quy trình Git

Theo Gitflow (CR-011). Áp dụng cho Đính, Huy và mọi agent. Mục tiêu: `dev` luôn chạy được, `main` luôn là bản đã phát hành, hai người ít xung đột và cả hai hiểu mọi thay đổi.

## Vai trò
| Người | Trách nhiệm |
|---|---|
| Đính | Quản lý task: tạo, chia Issue trên GitHub Project. Admin repo: thiết lập, quyền |
| Huy | Leader GitHub: giữ quy trình PR, nhịp merge, nhánh release, tag phát hành |
| Cả hai | Làm chính phần được giao, review phần của người kia |

Người làm và người duyệt luôn khác nhau.

## Nhánh
| Nhánh | Tách từ | Merge vào | Kiểu merge | Sống |
|---|---|---|---|---|
| `main` | | | | Lâu dài, chỉ chứa bản phát hành |
| `dev` | `main` | | | Lâu dài, **nhánh làm việc chính** |
| `feature/TB-<số>-<mô-tả>` | `dev` | `dev` | Squash | 2–3 ngày |
| `bugfix/TB-<số>-<mô-tả>` | `dev` | `dev` | Squash | Ngắn |
| `release/v<x.y.z>` | `dev` | `main`, rồi về `dev` | Merge commit | Vài ngày |
| `hotfix/TB-<số>-<mô-tả>` | `main` | `main`, rồi về `dev` | Merge commit | Ngắn |
| `sync/v<x.y.z>` | `main` | `dev` | Merge commit | Một PR |

- Không commit trực tiếp lên `main` hoặc `dev`.
- `<số>` là số Issue thật trên GitHub.
- Việc tài liệu, cấu hình, hợp đồng cũng đi theo `feature/` hoặc `bugfix/`; loại việc nằm ở tiêu đề commit.
- Nhánh sống quá 2–3 ngày làm việc thì chia nhỏ Issue.

## Commit
Theo Conventional Commits, kèm mã Issue:

```text
<type>(<scope>): <mô tả tiếng Anh, viết thường> [TB-<số>]
```

- `type`: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`, `ci`, `build`.
- `scope`: mô-đun hoặc thành phần: `identity`, `classes`, `materials`, `learning`, `privacy`, `platform`, `mobile`, `web`, `ai-worker`, `contracts`, `infra`, `docs`.
- Trên nhánh: commit nhỏ sau mỗi bước đạt. Việc dở dùng `wip: ...`.
- Merge vào `dev` dùng squash, nên tiêu đề PR là commit cuối cùng trên `dev` và phải đúng cú pháp trên.
- Không commit bí mật, dữ liệu thật, file build.

```text
feat(classes): atomic offer acceptance [TB-12]
```

Agent thêm dòng đồng tác giả theo cấu hình của công cụ. Tác giả chịu trách nhiệm vẫn là người mở PR.

## Một vòng công việc
1. Nhận Issue, chạy `start-task`.
2. Tách nhánh từ `dev` mới nhất. Mở **Draft PR vào `dev`** ngay sau commit đầu, thân PR ghi `Closes #<số>`.
3. Làm theo playbook, cập nhật handoff sau mỗi commit.
4. Trước khi gửi review: `git rebase origin/dev`, tự rà bằng `review-pr`, cho agent còn lại rà, xóa file handoff, viết phần "tác giả tự giải thích".
5. Chuyển PR sang Ready, yêu cầu người kia review.
6. Người rà duyệt hoặc yêu cầu sửa. Tác giả tự giải quyết comment sau khi đã sửa.
7. CI xanh và đã duyệt thì tác giả squash merge, xóa nhánh.

Đề xuất: phản hồi PR trong một ngày làm việc. Vì mỗi người chỉ có 20 giờ/tuần, cả hai nên chốt khung giờ review cố định.

## Phát hành
1. Huy tách `release/v<x.y.z>` từ `dev`. Nhánh này chỉ nhận sửa lỗi và cập nhật phiên bản, không thêm tính năng.
2. Kiểm bằng chứng, cả hai duyệt PR vào `main`, merge bằng merge commit.
3. Huy tag `v<x.y.z>` trên `main`.
4. Đưa `main` về `dev` qua nhánh trung gian `sync/v<x.y.z>` tách từ `main`, merge bằng merge commit.

Không mở PR trực tiếp từ `main`: repo bật tự xóa nhánh sau merge, nên nhánh nguồn của PR có thể bị xóa.

## Sửa khẩn
1. Tách `hotfix/TB-<số>-<mô-tả>` từ `main`, tăng số PATCH.
2. Qua PR và review như thường, merge vào `main`, tag.
3. Đưa `main` về `dev` qua nhánh `sync/v<x.y.z>`.

## Tránh xung đột khi cùng làm backend và mobile
- **Chia theo lát dọc, không theo tầng.** Một lát có một chủ từ API tới màn hình.
- Trong một tuần, hai người không làm cùng một mô-đun backend hoặc cùng một luồng mobile. Đính đảm bảo điều này khi chia Issue.
- Thay đổi hợp đồng đi trước, là PR riêng (`contract-change`).
- Số migration được ghi trong Issue. Chỉ Đính cấp số.
- File khóa phụ thuộc bị xung đột thì sinh lại, không sửa tay.
- Dữ liệu chung, cấu hình chung: báo người kia trước khi sửa.

## Hai agent cùng lúc
- Một nhánh, một agent. Hai agent chạy song song phải ở hai nhánh, hai worktree: `git worktree add ../tb-12 feature/TB-12-accept-offer`.
- Đổi agent trên cùng nhánh theo playbook `handoff`.
- Người chịu trách nhiệm cuối cùng về code là người mở PR, không phải agent.

## Trên GitHub Free
Theo CR-010, GitHub không ép được các quy tắc trên repo private. Lớp bù:

- **Hook máy:** sau mỗi lần clone chạy `git config core.hooksPath .githooks`. Hook chặn push thẳng lên `main` và `dev`.
- **Agent:** `AGENTS.md` cấm commit lên `main` và `dev`. Không dùng `--no-verify` để vượt hook.
- **CI vẫn chạy** trên PR nhưng không tự chặn merge. CI đỏ thì không merge, theo cam kết.
- **Không tự duyệt:** không merge PR của chính mình khi người kia chưa duyệt.
- **Không force push** lên `main` hoặc `dev`. Trên nhánh riêng chỉ dùng `--force-with-lease`.
- **Rà hằng tuần:** cuối tuần, Huy xem `git log dev`. Mọi commit phải có số PR. Vi phạm thì hoàn tác bằng PR và ghi lại.

Những lớp này ngăn sai sót, không ngăn được người cố tình vượt qua.
