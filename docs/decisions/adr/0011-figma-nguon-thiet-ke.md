# ADR-0011 Figma là nguồn thiết kế giao diện

**Trạng thái:** Đề xuất (07/11/2026). Mục 1, 2, 4 Đính đã chọn; mục 3, 5, 6, 7 là đề xuất của agent, có hiệu lực khi Đính duyệt PR của #87. **Chủ trì:** Đính (CR-012, CR-013).

## Vấn đề
ADR-0009 chốt thư viện nhưng giao diện chưa có thiết kế: token đang là màu tạm, mỗi Issue giao diện tự đặt bố cục. Với hai người và hai agent, màn hình dễ lệch nhau, lệch chức năng, và mang vẻ mặc định của AI. Cần một nguồn thiết kế duyệt được, agent đọc được và repo vẫn tự đủ.

## Quyết định
| # | Chủ đề | Chọn | Ai chọn |
|---|---|---|---|
| 1 | Nguồn thiết kế | Một file Figma cho mobile và web, cùng bộ tài liệu `docs/design/ui/` | Đính |
| 2 | Người làm và duyệt | Đính làm và duyệt toàn bộ UX-UI (CR-013) | Đính |
| 3 | Phạm vi của Figma | Quyết định phần nhìn, bố cục, câu chữ. Không quyết định hành vi, trạng thái, quyền, phạm vi; các thứ đó theo baseline, quyết định, hợp đồng | Đề xuất, chờ Đính |
| 4 | Định hướng | Tin cậy, điềm tĩnh; xanh dương đậm đang dùng; thoáng, bo tròn vừa; Be Vietnam Pro; điểm nhấn duy nhất là thẻ lịch buổi học; chỉ nền sáng ở Lát 1; dùng logo đã có | Đính |
| 5 | Nối Figma với code | Cùng tên component, variant và token. Không dùng Code Connect vì gói Education không có | Đề xuất, chờ Đính |
| 6 | Bàn giao | Mỗi màn hình đã duyệt có `docs/design/ui/screens/<mã>.md` và ảnh từng trạng thái. Issue giao diện chỉ bắt đầu khi màn hình đã duyệt | Đề xuất, chờ Đính |
| 7 | Phiên bản | Named version của Figma, ghi tên version trong repo | Đề xuất, chờ Đính |

## Vì sao không chọn phương án khác
| Phương án | Lý do bỏ |
|---|---|
| Làm UI cơ bản hết Lát 1 rồi mới thiết kế | Làm lại nhiều màn hình; khó sửa vẻ mặc định sau khi đã có code |
| Agent đọc thẳng Figma, không lưu gì trong repo | Gói Education giới hạn lượt MCP; người hoặc agent không có quyền Figma không làm tiếp được (AGENTS.md: tiếp tục được chỉ từ repo) |
| Code Connect | Cần gói Organization hoặc Enterprise |
| Thư viện component mobile có sẵn để có giao diện nhanh | ADR-0009 đã bỏ; phụ thuộc thiết kế của thư viện |

## Hệ quả
- Thứ tự ưu tiên trong `AGENTS.md` thêm thiết kế đã duyệt, xếp sau hợp đồng và chỉ cho phần nhìn.
- Playbook `implement-slice` và `review-pr` thêm bước đọc `screens/<mã>.md` và so ảnh chụp.
- Font Be Vietnam Pro trên mobile cần gói tải font cho Expo; icon trên mobile có thể cần gói icon. Thêm ở Issue giao diện đầu tiên cần, Đính duyệt theo ADR-0009.
- Token trong `theme.ts` và `globals.css` đổi theo Foundations đã duyệt (line height, bo góc 8 và 12, cỡ tiêu đề) bằng một PR riêng.
- Câu hỏi mở: file logo (OP-16), link file Figma (OP-17), giới hạn MCP của gói Education (OP-18), rà tự động chống AI slop (OP-19).
