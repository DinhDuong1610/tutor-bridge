# UX-UI cho mobile và web

Bộ tài liệu giao diện của TutorBridge. Dùng khi thiết kế trên Figma và khi agent làm Issue giao diện. Quyết định nền ở [ADR-0011](../../decisions/adr/0011-figma-nguon-thiet-ke.md); người làm và duyệt là Đính ([CR-013](../../decisions/change-requests.md)).

**Trạng thái (08/11/2026):** đã có [file Figma](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl) với Foundations, 17 component, đủ 26 màn hình Lát 1 (23 mobile, 3 web quản trị), đã duyệt v1 ngày 10/11/2026 ([07](07-figma.md#đã-có-trong-file-08112026)). 

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
Cập nhật khi đổi trạng thái. **Chưa vẽ** → **Nháp** → **Đã duyệt vN**. Issue giao diện chỉ bắt đầu khi màn hình là "Đã duyệt"; phần TBD của màn hình chưa được làm cho tới khi OP tương ứng có kết luận.

**Duyệt v1 (Đính, 10/11/2026):** Foundations, 17 component và cả 26 màn hình, phần đã vẽ; khung TBD còn mở. Duyệt v1 gồm cả các giá trị ghi "(đề xuất)" đã vẽ trong Figma ở [02](02-nen-thiet-ke.md), [03](03-thanh-phan.md), [04](04-trang-thai-cau-chu.md), và việc không hiện điểm trên thẻ gia sư. Named version trong Figma: `Lát 1 v1 2026-11-10`.

| Mã | Màn hình | Kênh | Luồng | Issue giao diện | Thiết kế | Version Figma (khi duyệt) hoặc node nháp |
|---|---|---|---|---|---|---|
| AUTH01 | Đăng ký và chọn vai trò | Android | F01 | TB-10 | Đã duyệt v1 | [9:330](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=9-330) (section), [đặc tả](screens/AUTH01.md) |
| AUTH02 | Xác minh email | Android | F01 | TB-10 | Đã duyệt v1 | [11:316](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=11-316) (section), [đặc tả](screens/AUTH02.md) |
| AUTH03 | Đăng nhập | Android | F01 | TB-10 | Đã duyệt v1 | [12:691](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=12-691) (section), [đặc tả](screens/AUTH03.md) |
| AUTH04 | Yêu cầu khôi phục mật khẩu | Android | F01 | TB-10 | Đã duyệt v1 | [11:484](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=11-484) (section), [đặc tả](screens/AUTH04.md) |
| AUTH05 | Đặt lại mật khẩu | Android | F01 | TB-10 | Đã duyệt v2 (bỏ ô nhập lại, Q9 #91) | [11:673](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=11-673) (section), [đặc tả](screens/AUTH05.md) |
| AUTH06 | Bảo mật và quản lý phiên | Android | F01 | TB-10 | Đã duyệt v1 | [12:1287](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=12-1287) (section), [đặc tả](screens/AUTH06.md) |
| COM01 | Hộp thông báo | Android | Chung | #74 | Đã duyệt v1, TBD còn mở | [38:475](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=38-475) (section), [đặc tả](screens/COM01.md) |
| PH01 | Trang chủ phụ huynh | Android | F02 | TB-14 | Đã duyệt v1 | [18:871](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=18-871) (section), [đặc tả](screens/PH01.md) |
| PH02 | Hồ sơ các con | Android | F01 | #67 | Đã duyệt v1 | [15:1418](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=15-1418) (section), [đặc tả](screens/PH02.md) |
| PH03 | Danh sách và phiên bản nhu cầu | Android | F02 | #70 | Đã duyệt v1 | [29:2132](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=29-2132) (section), [đặc tả](screens/PH03.md) |
| PH04 | Nhập nhu cầu học kèm | Android | F02 | #70 | Đã duyệt v1 | [28:2600](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=28-2600) (section), [đặc tả](screens/PH04.md) |
| PH05 | Xem lại và xác nhận nhu cầu | Android | F02 | #70 | Đã duyệt v1 | [29:2652](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=29-2652) (section), [đặc tả](screens/PH05.md) |
| PH06 | Kết quả và so sánh gia sư | Android | F02 | #71 | Đã duyệt v1 | [30:3029](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=30-3029) (section), [đặc tả](screens/PH06.md) |
| PH07 | Chi tiết gia sư công khai | Android | F02 | #71 | Đã duyệt v1 | [31:2731](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=31-2731) (section), [đặc tả](screens/PH07.md) |
| PH08 | Xem lại và gửi đề nghị | Android | F02 | #72 | Đã duyệt v1 | [31:3916](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=31-3916) (section), [đặc tả](screens/PH08.md) |
| PH09 | Danh sách và chi tiết đề nghị | Android | F02 | #72 | Đã duyệt v1, TBD còn mở | [34:4229](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=34-4229) (section), [đặc tả](screens/PH09.md) |
| GS01 | Trang chủ gia sư | Android | F01, F02 | TB-14 | Đã duyệt v2 (thêm Cần bổ sung, Chưa được duyệt, OP-23) | [23:1043](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=23-1043) (section), [đặc tả](screens/GS01.md) |
| GS02 | Hồ sơ chuyên môn và học phí | Android | F01 | TB-14 | Đã duyệt v1, TBD còn mở | [16:1925](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=16-1925) (section), [đặc tả](screens/GS02.md) |
| GS03 | Minh chứng và kết quả xác minh | Android | F01 | TB-14 | Đã duyệt v1, TBD còn mở | [17:2447](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=17-2447) (section), [đặc tả](screens/GS03.md) |
| GS04 | Lịch rảnh và ngoại lệ | Android | F02 | #69 | Đã duyệt v1, TBD còn mở | [27:2228](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=27-2228) (section), [đặc tả](screens/GS04.md) |
| GS05 | Đề nghị nhận lớp | Android | F02 | #72 | Đã duyệt v1, TBD còn mở | [35:5145](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=35-5145) (section), [đặc tả](screens/GS05.md) |
| CL01 | Lớp học và thỏa thuận (chỉ xem) | Android | F02 | #73 | Đã duyệt v1, TBD còn mở | [36:5492](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=36-5492) (section), [đặc tả](screens/CL01.md) |
| CL02 | Lịch lớp và chi tiết buổi (chỉ xem) | Android | F02 | #73 | Đã duyệt v1 | [37:5838](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=37-5838) (section), [đặc tả](screens/CL02.md) |
| AD01 | Đăng nhập và xác thực quản trị | Web | F01 | Chưa có | Đã duyệt v1 | [39:212](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=39-212) (section), [đặc tả](screens/AD01.md) |
| AD03 | Hàng chờ xác minh gia sư | Web | F01 | Chưa có | Đã duyệt v1, TBD còn mở | [40:435](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=40-435) (section), [đặc tả](screens/AD03.md) |
| AD04 | Thẩm định hồ sơ và minh chứng | Web | F01 | Chưa có | Đã duyệt v1, TBD còn mở | [41:675](https://www.figma.com/design/uuEo9SH3LxWcFbbHHFFBgl?node-id=41-675) (section), [đặc tả](screens/AD04.md) |

Số Issue lấy từ [kế hoạch Lát 1](../../plan/lat-1-ke-hoach.md). WEB01, WEB02 không thuộc 26 màn hình của Lát 1 ([r0a-scope](../../context/r0a-scope.md)), hiện là trang chờ; thiết kế khi đưa vào phạm vi.

## Câu hỏi còn mở
Theo dõi ở [sổ quyết định](../../decisions/decision-register.md), mục OP-16 đến OP-21. OP-17 đến OP-22 đã đóng; còn OP-16 (file logo).
