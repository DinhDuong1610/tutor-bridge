# Tài liệu TutorBridge

Điểm bắt đầu cho Đính, Huy và các agent. Cập nhật lần cuối: 06/11/2026.

## Trạng thái

Đã hoàn thành 7 nhóm tài liệu. Repo: https://github.com/DinhDuong1610/tutor-bridge, làm việc trên nhánh `dev`. Đã xong M0 của Lát 1, xem [bàn giao](plan/m0-ban-giao.md). Bước tiếp theo là TB-06 và M2. Cổng phỏng vấn đã qua bằng chấp nhận rủi ro; chia việc theo CR-012. Nền backend, mobile, web (module N0, S1, B1–B4, M1–M5, W1–W3) làm xong ngày 06/11/2026 ở #43–#56, chờ review.

| Nhóm | Nội dung | Vị trí | Trạng thái |
|---|---|---|---|
| 1 | Baseline và quyết định | `decisions/`, file này | Đã xác nhận; còn các mục OP |
| 2 | Yêu cầu: PRD/SRS, quy tắc nghiệp vụ, UC | `baseline/` | Đã xác nhận |
| 3 | Thiết kế: kiến trúc, F01–F06, giao dịch, ADR | `design/`, `decisions/adr/` | Đã xác nhận; hợp đồng `/internal/v1` làm khi tới F04 |
| 4 | Context cho agent: `AGENTS.md`, bất biến, sơ đồ mô-đun, thuật ngữ, phạm vi Lát 1 | `context/`, `../AGENTS.md` | Đã xác nhận |
| 5 | Playbook và skills | `playbooks/`, `handoff/`, `.claude/skills/`, `.agents/skills/` | Đã xác nhận |
| 6 | Quy trình nhóm: Git, PR, review, CI, GitHub | `process/`, `../.github/`, `../.githooks/` | Đã xác nhận |
| 7 | Kế hoạch Lát 1: mốc, Issue tách backend và giao diện, ước lượng | `plan/` | Đã xác nhận; chia lại theo CR-012 ngày 06/11/2026 |

## Thứ tự ưu tiên khi tài liệu mâu thuẫn

1. Yêu cầu đã xác nhận và CR có hiệu lực
2. Quyết định thiết kế đã xác nhận
3. Phụ lục hợp đồng cùng phiên bản
4. Tài liệu lịch sử

Tài liệu mới hơn không tự động thắng. Khi các nguồn đã xác nhận mâu thuẫn, dừng phần bị ảnh hưởng và lập CR.

## Bản đồ tài liệu

| Vị trí | Nội dung |
|---|---|
| [decisions/decision-register.md](decisions/decision-register.md) | 42 quyết định và các việc còn mở (OP) |
| [decisions/change-requests.md](decisions/change-requests.md) | Thay đổi sau xác nhận (CR) |
| [baseline/](baseline/README.md) | PRD/SRS dạng Markdown: BR, quyền, trạng thái, UC, NFR, AT, truy vết |
| [context/](context/) | Bất biến, bản đồ mô-đun, phạm vi Lát 1, thuật ngữ, quy ước code |
| [playbooks/](playbooks/README.md) | 6 quy trình dùng chung, gọi được từ Claude và Codex |
| [process/](process/git-workflow.md) | Quy trình Git, review, CI, thiết lập GitHub |
| [plan/](plan/lat-1-ke-hoach.md) | Kế hoạch Lát 1: mốc, Issue, ước lượng; [bàn giao sau M0](plan/m0-ban-giao.md) |
| [design/](design/README.md) | Kiến trúc, đường mở rộng, F01–F06, API, dữ liệu |
| [decisions/adr/](decisions/adr/) | Quyết định kiến trúc ADR-0001 đến ADR-0010 (ADR-0007, một phần ADR-0009 và ADR-0010 đang đề xuất; ADR-0008 ở PR #39) |
| [appendix/g3_ky_thuat_v0_1/](appendix/g3_ky_thuat_v0_1/) | OpenAPI, từ điển dữ liệu, ERD, SQL, truy vết FR. Giữ nguyên bản, không sửa tay |
| [evidence/](evidence/) | Kết quả kiểm chứng thực tế |
| [source-docx/](source-docx/) | 8 tài liệu Word gốc, chỉ để đối chiếu |

## Tài liệu Word gốc

| File | Nội dung |
|---|---|
| `01_De_cuong_Y_tuong.docx` | Tầm nhìn sản phẩm, rộng hơn R0 |
| `02_Ke_hoach_Tong_the_v2_0.docx` | Kế hoạch G0–G8 |
| `03_G0_G1_Tong_ket_Hop_nhat_v1_1.docx` | Tổng hợp nội bộ, chưa có phỏng vấn |
| `04_G2_PRD_SRS_Hop_nhat_v1_1.docx` | Yêu cầu chính |
| `05_G2_Ra_soat_Nghiem_thu_v1_2.docx` | Nghiệm thu hồ sơ G2 |
| `06_G3_Thiet_ke_Hop_nhat_de_Ra_soat_v0_1.docx` | Thiết kế chính |
| `07_Ra_soat_Tong_the_G0_G3_v0_1.docx` | Rà soát tổng thể |
| `08_So_Xac_nhan_Dinh_Huy_v0_1.docx` | Sổ xác nhận gốc, chưa điền |

Gói đầy đủ cả tài liệu G3 01–08 nằm tại `D:\DOWNLOAD\TutorBridge_Toan_bo_Ho_so_G0_G3_Gui_Huy_v0_1`, chỉ có trên máy của Đính.
