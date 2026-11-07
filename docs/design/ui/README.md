# UX-UI cho mobile và web

Bộ tài liệu giao diện của TutorBridge. Dùng khi thiết kế trên Figma và khi agent làm Issue giao diện. Quyết định nền ở [ADR-0011](../../decisions/adr/0011-figma-nguon-thiet-ke.md); người làm và duyệt là Đính ([CR-013](../../decisions/change-requests.md)).

**Trạng thái (07/11/2026):** đã chốt định hướng, chưa có file Figma. Chưa màn hình nào được duyệt.

## Đọc gì
| Bạn đang làm | Đọc |
|---|---|
| Thiết kế trên Figma | [01](01-dinh-huong.md), [02](02-nen-thiet-ke.md), [07](07-figma.md), [08](08-chong-ai-slop.md), rồi đặc tả màn hình ở [06](06-man-hinh-lat-1.md) |
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
| [screens/](screens/TEMPLATE.md) | Mỗi màn hình đã duyệt một file, kèm ảnh chụp từng trạng thái |

## Thứ tự ưu tiên
Thiết kế đã duyệt quyết định **phần nhìn và bố cục**. Hành vi, trạng thái, quyền, phạm vi theo `docs/baseline/`, `docs/decisions/`, `contracts/`. Figma vẽ khác các nguồn đó thì nguồn đó thắng: agent dừng phần bị ảnh hưởng và báo Đính, không làm theo Figma.

## Trạng thái màn hình Lát 1
Cập nhật khi đổi trạng thái. **Chưa vẽ** → **Nháp** → **Đã duyệt vN**. Issue giao diện chỉ bắt đầu khi màn hình là "Đã duyệt".

| Mã | Màn hình | Kênh | Luồng | Issue giao diện | Thiết kế | Version Figma |
|---|---|---|---|---|---|---|
| AUTH01 | Đăng ký và chọn vai trò | Android | F01 | TB-10 | Chưa vẽ | |
| AUTH02 | Xác minh email | Android | F01 | TB-10 | Chưa vẽ | |
| AUTH03 | Đăng nhập | Android | F01 | TB-10 | Chưa vẽ | |
| AUTH04 | Yêu cầu khôi phục mật khẩu | Android | F01 | TB-10 | Chưa vẽ | |
| AUTH05 | Đặt lại mật khẩu | Android | F01 | TB-10 | Chưa vẽ | |
| AUTH06 | Bảo mật và quản lý phiên | Android | F01 | TB-10 | Chưa vẽ | |
| COM01 | Hộp thông báo | Android | Chung | #74 | Chưa vẽ | |
| PH01 | Trang chủ phụ huynh | Android | F02 | TB-14 | Chưa vẽ | |
| PH02 | Hồ sơ các con | Android | F01 | #67 | Chưa vẽ | |
| PH03 | Danh sách và phiên bản nhu cầu | Android | F02 | #70 | Chưa vẽ | |
| PH04 | Nhập nhu cầu học kèm | Android | F02 | #70 | Chưa vẽ | |
| PH05 | Xem lại và xác nhận nhu cầu | Android | F02 | #70 | Chưa vẽ | |
| PH06 | Kết quả và so sánh gia sư | Android | F02 | #71 | Chưa vẽ | |
| PH07 | Chi tiết gia sư công khai | Android | F02 | #71 | Chưa vẽ | |
| PH08 | Xem lại và gửi đề nghị | Android | F02 | #72 | Chưa vẽ | |
| PH09 | Danh sách và chi tiết đề nghị | Android | F02 | #72 | Chưa vẽ | |
| GS01 | Trang chủ gia sư | Android | F01, F02 | TB-14 | Chưa vẽ | |
| GS02 | Hồ sơ chuyên môn và học phí | Android | F01 | TB-14 | Chưa vẽ | |
| GS03 | Minh chứng và kết quả xác minh | Android | F01 | TB-14 | Chưa vẽ | |
| GS04 | Lịch rảnh và ngoại lệ | Android | F02 | #69 | Chưa vẽ | |
| GS05 | Đề nghị nhận lớp | Android | F02 | #72 | Chưa vẽ | |
| CL01 | Lớp học và thỏa thuận (chỉ xem) | Android | F02 | #73 | Chưa vẽ | |
| CL02 | Lịch lớp và chi tiết buổi (chỉ xem) | Android | F02 | #73 | Chưa vẽ | |
| AD01 | Đăng nhập và xác thực quản trị | Web | F01 | Chưa có | Chưa vẽ | |
| AD03 | Hàng chờ xác minh gia sư | Web | F01 | Chưa có | Chưa vẽ | |
| AD04 | Thẩm định hồ sơ và minh chứng | Web | F01 | Chưa có | Chưa vẽ | |

Số Issue lấy từ [kế hoạch Lát 1](../../plan/lat-1-ke-hoach.md). WEB01, WEB02 không thuộc 26 màn hình của Lát 1 ([r0a-scope](../../context/r0a-scope.md)), hiện là trang chờ; thiết kế khi đưa vào phạm vi.

## Câu hỏi còn mở
Theo dõi ở [sổ quyết định](../../decisions/decision-register.md), mục OP-16 đến OP-18: file logo, link file Figma, giới hạn MCP của gói Education.
