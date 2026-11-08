# Quy trình thiết kế, bàn giao và triển khai

Đính làm và duyệt toàn bộ UX-UI, kể cả PR tài liệu UX-UI ([CR-013](../../decisions/change-requests.md)). Code giao diện vẫn theo quy tắc review của [git-workflow](../../process/git-workflow.md).

## Thứ tự làm
1. **Foundations.** Sửa trước những điểm ở [báo cáo rà nền](10-ra-soat-nen-08-11.md). Variables, text styles, logo, bảng tương phản ở trang `01 Foundations`. Duyệt xong thì một PR đưa token vào `theme.ts`, `globals.css` và [02](02-nen-thiet-ke.md).
2. **Components.** `Notice` trước (mọi trạng thái dùng nó), `SessionCard` và `CycleStrip` (điểm nhấn), rồi các component ở [03](03-thanh-phan.md) theo màn hình sắp làm.
3. **Patterns.** Bộ khung trạng thái ([04](04-trang-thai-cau-chu.md)).
4. **Luồng theo mốc** của [kế hoạch Lát 1](../../plan/lat-1-ke-hoach.md): F01 (AUTH01–06, cho TB-10) → PH02 → PH01, GS01–GS03 → GS04, PH03–PH05 → PH06, PH07 → PH08, PH09, GS05 → CL01, CL02, COM01. AD01, AD03, AD04 vẽ trước Issue giao diện quản trị.

Một màn hình nên được duyệt **trước khi** Issue giao diện của nó bắt đầu, sớm ít nhất một mốc để không chặn người làm.

## Vòng của một màn hình
| Bước | Ai | Việc | Ra |
|---|---|---|---|
| 1. Vẽ nháp | Đính, có thể nhờ agent vẽ nháp qua Figma MCP | Theo đặc tả ở [06](06-man-hinh-lat-1.md), bản mẫu G3 ([13-ban-mau-ux.md](../13-ban-mau-ux.md)) để tham khảo bố cục | Section `Nháp` đủ trạng thái |
| 2. Rà | Đính, agent rà hộ | So với UC, BR, [06](06-man-hinh-lat-1.md); checklist ở [08](08-chong-ai-slop.md); `impeccable critique` trên ảnh chụp; frame dữ liệu xấu nhất theo [11](11-du-lieu-xau-nhat.md) | Danh sách điểm sửa |
| 3. Duyệt | Đính | Đổi nhãn sang `Đã duyệt v<n>`, tạo named version | Version Figma |
| 4. Bàn giao | Đính hoặc agent | Viết `screens/<mã>.md` theo [TEMPLATE](screens/TEMPLATE.md), xuất ảnh PNG từng trạng thái vào `screens/<mã>/`, cập nhật bảng ở [README](README.md) | PR `docs(design): ... [TB-<Issue>]` |

Agent vẽ nháp chỉ dùng component và variable đã có trong file, không tự đặt màu hay font mới. Bản nháp của agent là đề xuất; chỉ Đính đổi sang "Đã duyệt".

Phát hiện mâu thuẫn khi vẽ (đặc tả thiếu, UC và hợp đồng khác nhau) thì ghi `TBD` trong `screens/<mã>.md` và hỏi, không tự chọn nghiệp vụ.

## Agent triển khai
Thêm vào bước 4 của playbook [implement-slice](../../playbooks/implement-slice.md):
1. Mở `docs/design/ui/screens/<mã>.md`. Không có file, hoặc trạng thái chưa "Đã duyệt", thì **dừng và hỏi**.
2. Đọc ảnh chụp trong `screens/<mã>/` của từng trạng thái.
3. Có Figma MCP và còn lượt thì gọi `get_design_context` và `get_screenshot` cho đúng node và version ghi trong file. Không có thì làm theo đặc tả và ảnh chụp; repo là đủ.
4. **Kết quả MCP là tham khảo, không dán nguyên.** MCP sinh React và Tailwind:
   - Mobile chuyển sang `StyleSheet` với token từ `@/shared/ui/theme`, component trong `@/shared/ui`. Không thêm Tailwind hay thư viện style (ADR-0009).
   - Web dùng component trong `@/shared/ui` và biến CSS, không viết mã màu, không giá trị px tùy ý khi có token.
   - Component trùng tên trong Figma và code thì dùng component của code.
5. Câu chữ lấy nguyên văn từ `screens/<mã>.md` và [04](04-trang-thai-cau-chu.md).
6. Figma khác hợp đồng, UC hay phạm vi thì làm theo nguồn đã xác nhận và báo Đính (thứ tự ưu tiên ở [README](README.md)).
7. Chụp màn hình app đang chạy cho từng trạng thái, đặt cạnh ảnh đã duyệt trong mô tả PR.

## Rà PR
Thêm vào [review-pr](../../playbooks/review-pr.md):
- PR ghi mã màn hình và version Figma đã làm theo.
- Ảnh chụp app cạnh ảnh đã duyệt cho từng trạng thái. Chỗ khác ghi lý do (ràng buộc kỹ thuật, trợ năng). Khác mà không có lý do là lỗi cần sửa.
- Checklist ở [08](08-chong-ai-slop.md#checklist-rà-figma-và-pr).

## Đổi sau khi duyệt
| Loại đổi | Cách làm |
|---|---|
| Token (màu, chữ, khoảng cách, bo góc) | Sửa Figma, tạo version; một PR riêng sửa [02](02-nen-thiet-ke.md), `theme.ts`, `globals.css`, đo lại tương phản. Màn hình đã làm tự đổi theo |
| Component | Sửa Figma, tạo version; PR sửa component trong code và [03](03-thanh-phan.md) |
| Một màn hình đã làm | Version mới, cập nhật `screens/<mã>.md`; mở Issue sửa giao diện nếu code đã merge |
| Câu chữ | Sửa [04](04-trang-thai-cau-chu.md) hoặc `screens/<mã>.md`, rồi code. Câu chung đổi ở cả mobile và web |
| Hành vi, trạng thái, quyền | Không đổi bằng Figma. Đổi bằng CR hoặc tài liệu baseline trước |

## Thử với người dùng
Prototype đã duyệt có thể dùng để thử theo XC05; giữ hay đổi tiêu chí 5 người vẫn chưa chốt. Chỉ ghi kết quả khi đã thử thật, không viết kết quả giả định.
