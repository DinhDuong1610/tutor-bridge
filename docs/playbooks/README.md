# Playbook

Quy trình dùng chung cho Đính, Huy, Claude và Codex. Nội dung thật nằm ở đây. Skill chỉ là lớp vỏ trỏ tới file này, nên đổi quy trình chỉ cần sửa một chỗ.

| Playbook | Dùng khi | Gọi trong Claude | Gọi trong Codex |
|---|---|---|---|
| [start-task](start-task.md) | Bắt đầu hoặc tiếp nhận task | `/start-task` | `$start-task` |
| [implement-slice](implement-slice.md) | Làm một chức năng: Issue backend hoặc Issue giao diện | `/implement-slice` | `$implement-slice` |
| [transaction-change](transaction-change.md) | Đụng lịch, giao dịch, khóa, thu hồi | `/transaction-change` | `$transaction-change` |
| [contract-change](contract-change.md) | Đổi OpenAPI hoặc migration | `/contract-change` | `$contract-change` |
| [review-pr](review-pr.md) | Rà PR | `/review-pr` | `$review-pr` |
| [handoff](handoff.md) | Dừng, đổi agent, đổi người | `/handoff` | `$handoff` |

## Skill
- Claude Code: `.claude/skills/<tên>/SKILL.md`
- Codex: `.agents/skills/<tên>/SKILL.md`

Hai bản giống hệt nhau, chỉ trỏ tới playbook. CI sẽ kiểm tra chúng không lệch. Không viết quy trình trực tiếp trong skill.

Agent không có skill vẫn làm việc được bằng cách đọc thẳng playbook.
