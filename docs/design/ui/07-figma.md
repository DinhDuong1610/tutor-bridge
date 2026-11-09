# Quy ước file Figma

Một file Figma cho cả mobile và web, Đính là chủ file. Link file ghi ở bảng dưới khi tạo (OP-17). Quyền xem cấp cho Huy để tham khảo; agent đọc qua Figma MCP bằng tài khoản của người đang chạy agent.

| Mục | Giá trị |
|---|---|
| File | [TutorBridge UX-UI](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl), tạo 08/11/2026 |
| File key | `uuEo9SH3LxWcFbbHHFFBgl` |
| Team | Verijob (gói student, tức Education; ghế Full; Đính là admin). Đính chọn ngày 08/11/2026. Có thể dời file sang team riêng sau |
| Gói | Figma Education (Đính, 07/11/2026) |

## Giới hạn của gói và cách bù
Gói Education tương đương Professional về tính năng. Theo tài liệu và diễn đàn Figma tháng 11/2026 (cần kiểm lại trên trang chính thức, OP-18):
- **Code Connect không có** (cần Organization hoặc Enterprise). Bù bằng **cùng tên component và variant** giữa Figma và code ([03](03-thanh-phan.md)), và bảng ánh xạ trong `screens/<mã>.md`.
- **Figma MCP có giới hạn số lần đọc** mỗi ngày và mỗi phút; seat View/Collab gần như không dùng được. Bù bằng: repo tự đủ (đặc tả và ảnh chụp trong `screens/`), agent chỉ gọi MCP cho node của màn hình đang làm, không quét cả file.
- **Không có branching.** Dùng **named version** và trạng thái trên section thay cho nhánh.

## Trang trong file
| Trang | Nội dung |
|---|---|
| `00 Đọc trước` | Link repo, link tài liệu này, bảng trạng thái màn hình, quy ước tóm tắt |
| `01 Foundations` | Variables (màu, khoảng cách, bo góc), text styles, logo, icon, bảng đo tương phản |
| `02 Components / Mobile` | Component mobile theo [03](03-thanh-phan.md), mỗi component một section kèm mọi variant |
| `03 Components / Web` | Component web quản trị, theo shadcn/ui đã dùng |
| `04 Patterns` | Bộ khung trạng thái ([04](04-trang-thai-cau-chu.md)), biểu mẫu mẫu, danh sách mẫu |
| `F01 Tài khoản` | AUTH01–06, PH02, GS02, GS03, AD01, AD03, AD04 |
| `F02 Nhu cầu đến chốt lớp` | PH01, PH03–PH09, GS01, GS04, GS05, CL01, CL02 |
| `Chung` | COM01, điều hướng tab, hết phiên |
| `Lưu trữ` | Phương án cũ, không xóa để còn đối chiếu |

## Đặt tên
- **Frame màn hình:** `<mã> / <tên> / <trạng thái>`. Ví dụ `PH04 / Nhập nhu cầu / Lỗi trường`, `PH06 / Kết quả gia sư / Không có ai phù hợp`. Trạng thái dùng đúng tên ở [04](04-trang-thai-cau-chu.md).
- **Section màn hình:** `<mã> <tên>`, chứa mọi frame trạng thái của màn hình đó, xếp theo thứ tự: mặc định, tải, rỗng, lỗi, rồi trạng thái riêng.
- **Component:** tên tiếng Anh như code (`SessionCard`, `StatusLabel`). Thuộc tính variant như prop: `variant=proposed`, `tone=warning`, `state=busy`.
- **Layer:** đặt tên theo vai trò (`price`, `deadline`, `primary-action`), không để `Frame 123`, `Rectangle 4`.

## Variables
- Collection `color`, `space`, `radius` với tên đúng như cột Figma ở [02](02-nen-thiet-ke.md). Một mode `Light`; mode `Dark` để trống tới khi có quyết định.
- Text styles theo bảng chữ ở [02](02-nen-thiet-ke.md), font Be Vietnam Pro.
- **Không giá trị rời** trong frame màn hình: mọi màu, khoảng cách, bo góc là variable; mọi chữ là text style.

## Cách vẽ
- **Auto layout** cho mọi khung. Khoảng cách bằng variable `space`.
- Khung theo [02](02-nen-thiet-ke.md#kích-thước-và-lưới). Mỗi màn hình mobile có thêm một frame cỡ chữ 200% cho trạng thái nhiều chữ nhất.
- Mỗi màn hình có frame `Dữ liệu xấu nhất`, và `Rỗng`, `Một mục`, `Nhiều mục` khi là danh sách, theo [11](11-du-lieu-xau-nhat.md).
- Chữ thật tiếng Việt theo [04](04-trang-thai-cau-chu.md), dữ liệu giả rõ ràng. Không "Lorem ipsum".
- **Ghi chú (annotation)** trên frame cho điều không nhìn thấy: hành vi khi bấm, thứ tự focus trên web, nhãn trợ năng của icon, điều kiện ẩn hiện, API dùng.
- Prototype nối theo các luồng ở [05](05-dieu-huong-luong.md), kể cả nhánh lỗi.

## Trạng thái và phiên bản
- Section mỗi màn hình có nhãn trạng thái: `Nháp`, `Đang rà`, `Đã duyệt v1`. Chỉ Đính đổi sang "Đã duyệt".
- Khi duyệt: tạo **named version** trong lịch sử phiên bản, tên `<mã> v<n> <ngày>`, ví dụ `PH04 v1 2026-11-20`. Ghi tên version vào bảng trạng thái ở [README](README.md) và `screens/<mã>.md`.
- Sửa sau khi duyệt: làm trên bản sao của section ở trang đó với nhãn `Nháp v2`, duyệt xong thì thay bản cũ, chuyển bản cũ sang trang `Lưu trữ`.

## Đã có trong file (08/11/2026)
| Thứ | Node | Trạng thái |
|---|---|---|
| Trang `00 Đọc trước` | [0:1](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=0-1) | Có |
| Foundations: 26 biến (`color` 17, `space` 6, `radius` 3) có scope và tên biến cho web (`var(--…)`) và mobile (`colors.…`); 7 text style; logo demo; bảng tương phản | [5:2](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=5-2) | Nháp |
| Icon (Material Icons, Apache 2.0): info, check-circle, warning, error, event-available, schedule, event-busy | trang `02 Components / Mobile` | Nháp |
| `Button` (Primary, Secondary) | [3:27](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=3-27) | Nháp tối thiểu |
| `Notice` (Info, Success, Warning, Danger; thuộc tính Nội dung, Có nút) | [3:56](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=3-56) | Nháp |
| `SessionCard` (Scheduled, Proposed, Cancelled, Past × Default, Compact) | [4:101](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=4-101) | Nháp |
| Khung trình bày Notice và SessionCard với câu thật và dữ liệu xấu nhất | [5:201](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=5-201) | Có |
| `Button` thêm trục State (Default, Busy, Disabled) | [3:27](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=3-27) | Nháp |
| `TextField` (Default, Focused, Error, Disabled; nút hiện mật khẩu) | [6:182](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=6-182) | Nháp |
| `ChoiceOption` (thẻ chọn một), `Checkbox` (Checked, Unchecked, Error) | [6:195](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=6-195), [6:214](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=6-214) | Nháp |
| `TopAppBar` (có, không có nút quay lại), `StickyActionBar`, `ConfirmSheet` | [6:222](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=6-222), [7:288](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=7-288), [7:289](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=7-289) | Nháp |
| `NavigationBar` (Phụ huynh, Gia sư × 5 tab; số chưa đọc) | [8:428](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=8-428) | Nháp |
| `PriceBlock`, `StudentSwitcher`, `CycleStrip` (Scheduled, Proposed × 1–3 buổi/tuần) | [7:124](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=7-124), [7:127](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=7-127), [7:279](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=7-279) | Nháp, chưa dùng trong màn hình |
| `StatusLabel` (Neutral, Info, Success, Warning, Danger), `VerificationLabel` (Tự khai, Đã xác minh), `FileRow` (4 trạng thái tệp) | [14:212](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=14-212), [14:221](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=14-221), [14:263](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=14-263) | Nháp |
| Trang `F02 Nhu cầu đến chốt lớp`: PH01 (10 trạng thái, có bảng chọn con), GS01 (7 trạng thái) | PH01 [18:871](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=18-871), GS01 [23:1043](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=23-1043) | Nháp |
| `TimeChip` (Draft, Effective, Booked) | [25:217](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=25-217) | Nháp |
| Trang `F02`: GS04 (10 trạng thái, có TBD), PH03 (5), PH04 (8), PH05 (7) | GS04 [27:2228](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=27-2228), PH03 [29:2132](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=29-2132), PH04 [28:2600](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=28-2600), PH05 [29:2652](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=29-2652) | Nháp |
| Trang `F02`: PH06 (9), PH07 (4), PH08 (8), PH09 (10, có TBD), GS05 (13, có TBD) | PH06 [30:3029](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=30-3029), PH07 [31:2731](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=31-2731), PH08 [31:3916](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=31-3916), PH09 [34:4229](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=34-4229), GS05 [35:5145](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=35-5145) | Nháp |
| Trang `F02`: CL01 (9, có màn gốc tab Lớp học), CL02 (8); trang `Chung`: COM01 (7) | CL01 [36:5492](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=36-5492), CL02 [37:5838](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=37-5838), COM01 [38:475](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=38-475) | Nháp |
| Trang `F01 Tài khoản`: PH02, GS02, GS03 (GS02, GS03 có khung TBD đỏ nét đứt) | PH02 [15:1418](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=15-1418), GS02 [16:1925](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=16-1925), GS03 [17:2447](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=17-2447) | Nháp |
| Trang `F01 Tài khoản`: AUTH01–AUTH06, mỗi màn hình một section đủ trạng thái, frame chữ 200%, frame dữ liệu xấu nhất | AUTH01 [9:330](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=9-330), AUTH02 [11:316](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=11-316), AUTH03 [12:691](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=12-691), AUTH04 [11:484](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=11-484), AUTH05 [11:673](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=11-673), AUTH06 [12:1287](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=12-1287) | Nháp |

Ghi chú khi dựng:
- Bộ Material 3 Design Kit (cộng đồng) không có icon lịch, đồng hồ, hủy lịch, nên icon được dựng từ path của Material Icons.
- `Notice` phân biệt tone bằng icon khác hình dạng; nhãn trợ năng của icon là chữ tone. Không còn viền màu một bên và dòng nhãn phía trên ([10](10-ra-soat-nen-08-11.md)).
- `SessionCard` Proposed: viền nét đứt, khối ngày rỗng; Cancelled: giờ gạch ngang. Bản Compact xếp giờ trên trạng thái để nhãn "Trong đề nghị, chưa chốt" không đè giờ.
- Thuộc tính chữ của component dùng một giá trị mặc định chung cho mọi variant; đặt câu thật ở instance.
- Frame màn hình: phần trên (thanh trạng thái, thanh tiêu đề, nội dung) và phần dưới (thông báo trạng thái lệnh, thanh nút, thanh điều hướng) đẩy về hai đầu. Màn hình ngắn: thanh nút sát đáy 800; màn hình dài: frame dài theo nội dung, nghĩa là trang cuộn, thanh nút vẫn cố định khi làm.
- Kiểu chữ (gạch ngang, đậm) của một lớp chữ gắn thuộc tính TEXT được dùng chung cho mọi variant. Variant cần kiểu riêng (SessionCard Cancelled gạch ngang giờ) phải bỏ liên kết thuộc tính ở lớp đó. Lỗi này từng làm mọi thẻ buổi học bị gạch ngang (08/11/2026); kiểm mọi variant sau khi đổi kiểu chữ.
- Ngày đi kèm thứ phải đúng lịch thật (18/11/2026 là Thứ 4). Lịch buổi phải đúng mẫu: N buổi trong mỗi khối 7 ngày tính từ ngày bắt đầu (BR04). Ngày 10/11/2026 đã quét và sửa 23 chỗ sai thứ, 8 frame PH08 sai mẫu buổi; quét lại khi thêm dữ liệu mẫu có ngày.
- Chữ có giới hạn số dòng: bật cắt chữ (`ENDING`) trước rồi mới đặt số dòng tối đa, ngược lại Figma giữ 1 dòng.
- Hàng "Trạng thái" + `StatusLabel` cho phép xuống dòng, để nhãn không tràn ở chữ 200%.
- Frame chữ 200% là bản tách instance (detach) rồi nhân đôi cỡ chữ, vì Figma không cho đổi cỡ chữ có style bên trong instance. Không sửa component từ frame này; icon giữ cỡ vì Android tính icon theo dp.

## Agent đọc Figma
Lấy link node bằng "Copy link to selection" trên section màn hình, ghi vào `screens/<mã>.md`. Agent chạy theo thứ tự ở [09](09-quy-trinh.md#agent-triển-khai): đọc `screens/<mã>.md`, rồi mới gọi MCP (`get_design_context`, `get_screenshot`, `get_variable_defs`) cho đúng node đó nếu còn lượt.
