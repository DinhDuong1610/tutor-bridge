# UX-UI cho mobile và web

Bộ tài liệu giao diện của TutorBridge. Dùng khi thiết kế trên Figma và khi agent làm Issue giao diện. Quyết định nền ở [ADR-0011](../../decisions/adr/0011-figma-nguon-thiet-ke.md); người làm và duyệt là Đính ([CR-013](../../decisions/change-requests.md)).

**Trạng thái (08/11/2026):** đã có [file Figma](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl) với Foundations, 17 component, AUTH01–06, PH01–PH09, GS01–GS05 ở dạng nháp ([07](07-figma.md#đã-có-trong-file-08112026)). Chưa màn hình nào được duyệt.

## Đọc gì
| Bạn đang làm | Đọc |
|---|---|
| Thiết kế trên Figma | [01](01-dinh-huong.md), [02](02-nen-thiet-ke.md), [07](07-figma.md), [08](08-chong-ai-slop.md), [10](10-ra-soat-nen-08-11.md), rồi đặc tả màn hình ở [06](06-man-hinh-lat-1.md) và dữ liệu ở [11](11-du-lieu-xau-nhat.md) |
| Issue giao diện (agent hay người) | `screens/<mã>.md` của màn hình, [04](04-trang-thai-cau-chu.md), [09](09-quy-trinh.md) mục "Agent triển khai", [03](03-thanh-phan.md) |
| Rà PR giao diện | [09](09-quy-trinh.md) mục "Rà PR", [08](08-chong-ai-slop.md) mục "Checklist" |

## Các file
| File | Nội dung |
|---|---|
| [01-dinh-huong.md](01-dinh-huong.md) | Người dùng, tính cách, nguyên tắc, điểm nhấn duy nhất |
| [02-nen-thiet-ke.md](02-nen-thiet-ke.md) | Token: màu, chữ, khoảng cách, bo góc, icon, chuyển động, trợ năng |
| [03-thanh-phan.md](03-thanh-phan.md) | Component đã có và cần có, variant, ánh xạ sang code |
| [04-trang-thai-cau-chu.md](04-trang-thai-cau-chu.md) | Mẫu trạng thái bắt buộc và câu chữ chuẩn |
| [05-dieu-huong-luong.md](05-dieu-huong-luong.md) | Điều hướng theo vai trò, luồng Lát 1 từ đầu đến cuối |
| [06-man-hinh-lat-1.md](06-man-hinh-lat-1.md) | Đặc tả chức năng của 26 màn hình Lát 1 |
| [07-figma.md](07-figma.md) | Cấu trúc file Figma, đặt tên, variable, phiên bản, giới hạn gói |
| [08-chong-ai-slop.md](08-chong-ai-slop.md) | Mẫu cần tránh, nguồn tham khảo, checklist |
| [09-quy-trinh.md](09-quy-trinh.md) | Vẽ → rà → duyệt → bàn giao, agent triển khai, rà PR, đổi sau duyệt |
| [10-ra-soat-nen-08-11.md](10-ra-soat-nen-08-11.md) | Kết quả rà nền giao diện hiện tại bằng các skill chống AI slop, việc phải sửa trong Foundations |
| [11-du-lieu-xau-nhat.md](11-du-lieu-xau-nhat.md) | Giới hạn trường theo hợp đồng và bộ dữ liệu xấu nhất cho mỗi màn hình |
| [screens/](screens/TEMPLATE.md) | Mỗi màn hình đã duyệt một file, kèm ảnh chụp từng trạng thái |

## Thứ tự ưu tiên
Thiết kế đã duyệt quyết định **phần nhìn và bố cục**. Hành vi, trạng thái, quyền, phạm vi theo `docs/baseline/`, `docs/decisions/`, `contracts/`. Figma vẽ khác các nguồn đó thì nguồn đó thắng: agent dừng phần bị ảnh hưởng và báo Đính, không làm theo Figma.

## Trạng thái màn hình Lát 1
Cập nhật khi đổi trạng thái. **Chưa vẽ** → **Nháp** → **Đã duyệt vN**. Issue giao diện chỉ bắt đầu khi màn hình là "Đã duyệt".

| Mã | Màn hình | Kênh | Luồng | Issue giao diện | Thiết kế | Version Figma (khi duyệt) hoặc node nháp |
|---|---|---|---|---|---|---|
| AUTH01 | Đăng ký và chọn vai trò | Android | F01 | TB-10 | Nháp | [9:330](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=9-330) (section) |
| AUTH02 | Xác minh email | Android | F01 | TB-10 | Nháp | [11:316](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=11-316) (section) |
| AUTH03 | Đăng nhập | Android | F01 | TB-10 | Nháp | [12:691](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=12-691) (section) |
| AUTH04 | Yêu cầu khôi phục mật khẩu | Android | F01 | TB-10 | Nháp | [11:484](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=11-484) (section) |
| AUTH05 | Đặt lại mật khẩu | Android | F01 | TB-10 | Nháp | [11:673](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=11-673) (section) |
| AUTH06 | Bảo mật và quản lý phiên | Android | F01 | TB-10 | Nháp | [12:1287](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=12-1287) (section) |
| COM01 | Hộp thông báo | Android | Chung | #74 | Chưa vẽ | |
| PH01 | Trang chủ phụ huynh | Android | F02 | TB-14 | Nháp | [18:871](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=18-871) (section) |
| PH02 | Hồ sơ các con | Android | F01 | #67 | Nháp | [15:1418](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=15-1418) (section) |
| PH03 | Danh sách và phiên bản nhu cầu | Android | F02 | #70 | Nháp | [29:2132](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=29-2132) (section) |
| PH04 | Nhập nhu cầu học kèm | Android | F02 | #70 | Nháp | [28:2600](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=28-2600) (section) |
| PH05 | Xem lại và xác nhận nhu cầu | Android | F02 | #70 | Nháp | [29:2652](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=29-2652) (section) |
| PH06 | Kết quả và so sánh gia sư | Android | F02 | #71 | Nháp | [30:3029](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=30-3029) (section) |
| PH07 | Chi tiết gia sư công khai | Android | F02 | #71 | Nháp | [31:2731](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=31-2731) (section) |
| PH08 | Xem lại và gửi đề nghị | Android | F02 | #72 | Nháp | [31:3916](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=31-3916) (section) |
| PH09 | Danh sách và chi tiết đề nghị | Android | F02 | #72 | Nháp, có TBD | [34:4229](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=34-4229) (section) |
| GS01 | Trang chủ gia sư | Android | F01, F02 | TB-14 | Nháp | [23:1043](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=23-1043) (section) |
| GS02 | Hồ sơ chuyên môn và học phí | Android | F01 | TB-14 | Nháp, có TBD | [16:1925](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=16-1925) (section) |
| GS03 | Minh chứng và kết quả xác minh | Android | F01 | TB-14 | Nháp, có TBD | [17:2447](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=17-2447) (section) |
| GS04 | Lịch rảnh và ngoại lệ | Android | F02 | #69 | Nháp, có TBD | [27:2228](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=27-2228) (section) |
| GS05 | Đề nghị nhận lớp | Android | F02 | #72 | Nháp, có TBD | [35:5145](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=35-5145) (section) |
| CL01 | Lớp học và thỏa thuận (chỉ xem) | Android | F02 | #73 | Chưa vẽ | |
| CL02 | Lịch lớp và chi tiết buổi (chỉ xem) | Android | F02 | #73 | Chưa vẽ | |
| AD01 | Đăng nhập và xác thực quản trị | Web | F01 | Chưa có | Chưa vẽ | |
| AD03 | Hàng chờ xác minh gia sư | Web | F01 | Chưa có | Chưa vẽ | |
| AD04 | Thẩm định hồ sơ và minh chứng | Web | F01 | Chưa có | Chưa vẽ | |

Số Issue lấy từ [kế hoạch Lát 1](../../plan/lat-1-ke-hoach.md). WEB01, WEB02 không thuộc 26 màn hình của Lát 1 ([r0a-scope](../../context/r0a-scope.md)), hiện là trang chờ; thiết kế khi đưa vào phạm vi.

## Câu hỏi còn mở
Theo dõi ở [sổ quyết định](../../decisions/decision-register.md), mục OP-16 đến OP-21: file logo, link file Figma, giới hạn MCP của gói Education, rà tự động trên CI, giá không có trần trong hợp đồng, có tạo `PRODUCT.md` và `DESIGN.md` cho Impeccable không.
