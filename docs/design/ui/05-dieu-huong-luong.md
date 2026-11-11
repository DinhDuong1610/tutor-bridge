# Điều hướng và luồng Lát 1

Luồng theo [r0a-scope](../../context/r0a-scope.md): tài khoản → hồ sơ → nhu cầu → gợi ý → đề nghị → chốt lớp. Route theo cấu trúc trong [conventions](../../context/conventions.md). Trong Figma, mỗi luồng dưới đây là một **prototype** nối đủ các màn hình, kể cả nhánh lỗi.

## Mobile: thanh điều hướng theo vai trò
Tab lấy từ `03-man-hinh.md`, chỉ giữ tab có màn hình trong Lát 1. Tab ngoài phạm vi không hiện, không vẽ trang "sắp ra mắt" (đề xuất).

| Phụ huynh (`/parent`) | Màn hình | Gia sư (`/tutor`) | Màn hình |
|---|---|---|---|
| Tổng quan | PH01 | Tổng quan | GS01 |
| Tìm gia sư | PH03 → PH04 → PH05 → PH06 → PH07 → PH08; PH09 | Đề nghị | GS05 |
| Lớp học | CL01, CL02 | Lớp học | CL01, CL02, GS04 |
| Thông báo | COM01 | Thông báo | COM01 |
| Tài khoản | PH02, AUTH06 | Tài khoản | GS02, GS03, AUTH06 |

- Phụ huynh: `StudentSwitcher` nằm trên cùng mọi màn hình của khu vực phụ huynh, trừ Tài khoản. Chưa có con nào thì mọi tab dẫn tới PH02 với lời nhắc tạo hồ sơ con trước.
- Tab Thông báo có số chưa đọc dạng chữ số, nhãn trợ năng "3 thông báo chưa đọc".
- Tab "Học liệu" của gia sư thuộc UC10–UC11, ngoài Lát 1.

## Web quản trị
Thanh bên trái: "Xác minh gia sư" (AD03). Các mục AD khác ngoài Lát 1 không hiện. Trên cùng: tên người đang đăng nhập, "Đăng xuất". Không có liên kết đăng ký.

## Luồng 1. Đăng ký và đăng nhập (F01)
```text
AUTH01 Đăng ký (chọn Phụ huynh hoặc Gia sư)
  → "Đã gửi email xác minh tới địa chỉ bạn vừa nhập. Liên kết có hạn 30 phút." (có "Gửi lại", giới hạn theo máy chủ)
  → AUTH02 mở liên kết: Đã xác minh | Hết hạn | Đã dùng
  → AUTH03 Đăng nhập → PH01 hoặc GS01 theo vai trò máy chủ trả
AUTH03 → "Quên mật khẩu" → AUTH04 → thông báo chung → AUTH05 từ liên kết → AUTH03
Tài khoản → AUTH06: phiên đang mở, thu hồi, đổi mật khẩu (xác thực lại trước)
```
Nhánh lỗi phải có trong prototype: email đã dùng, mật khẩu ngắn, liên kết hết hạn, đăng nhập sai nhiều lần (bị trì hoãn), tài khoản bị khóa, hết phiên.

## Luồng 2. Phụ huynh tìm gia sư đến khi chốt lớp (F02)
```text
PH01 → (chưa có con) PH02 tạo hồ sơ con → chọn con
  → PH03 nhu cầu của con → "Tạo nhu cầu"
  → PH04 nhập: mục tiêu, ngân sách/buổi, thời lượng, số buổi/tuần, ngày bắt đầu, khung rảnh
       "Lưu nháp" (thiếu điều kiện vẫn lưu được) | "Xem lại"
  → PH05 xem lại toàn bộ + dải 28 ngày → "Xác nhận nhu cầu"
  → PH06 "Tìm gia sư": Đang tìm → tối đa 5 gia sư | Không có ai phù hợp | Lỗi
  → PH07 chi tiết gia sư → "Gửi đề nghị"
  → PH08 xem lại điều kiện cố định, đủ buổi, hạn trả lời → ConfirmSheet → "Gửi đề nghị"
  → PH09 đề nghị: Đang chờ → (gia sư chấp nhận) Đã chốt lớp → CL01
                         → Từ chối | Hết hạn | Phụ huynh hủy | Kết thúc vì đề nghị khác đã chốt
```
- Phụ huynh có thể gửi đề nghị cho nhiều gia sư từ cùng một nhu cầu. PH09 nhóm đề nghị theo nhu cầu.
- Sửa nhu cầu sau khi xác nhận tạo bản nháp mới. Kết quả gợi ý và đề nghị từ bản cũ gắn `VersionNotice`.
- Thông báo (COM01) khi có phản hồi dẫn thẳng tới PH09 hoặc CL01, máy chủ kiểm quyền lại khi mở.

## Luồng 3. Gia sư từ hồ sơ đến nhận lớp (F01, F02)
```text
AUTH01 (Gia sư) → AUTH02 → AUTH03 → GS01 "Việc cần làm": hoàn thiện hồ sơ
  → GS02 hồ sơ chuyên môn, học phí → "Lưu nháp"
  → GS03 tải minh chứng → "Gửi duyệt" → Đang chờ xác minh
       → (AD04 quyết định) Đã duyệt | Cần bổ sung (có lý do) | Chưa được duyệt (có lý do)
  → GS04 lịch rảnh tuần + ngoại lệ → xem lại → "Xác nhận lịch rảnh"
  → GS05 có đề nghị mới (thông báo) → xem điều kiện, đủ buổi, hạn trả lời
       → "Chấp nhận đề nghị" → Đã chốt lớp → CL01 | Xung đột lịch: không nhận được, không buổi nào được đặt
       → "Từ chối"
```
- GS01 nói rõ vì sao hồ sơ chưa xuất hiện khi phụ huynh tìm: chưa gửi duyệt, đang chờ, cần bổ sung, chưa có lịch rảnh hiệu lực.
- Sửa phần chuyên môn sau khi đã duyệt đưa phần đó về chờ duyệt. GS02 hiện song song "Bản đang công khai" và "Bản đang sửa".

## Luồng 4. Người xác minh (web)
```text
AD01 đăng nhập → bước xác thực bổ sung → AD03 hàng chờ được giao (lọc trạng thái)
  → AD04 mở hồ sơ: phiên bản, nội dung tự khai, minh chứng
  → quyết định: Xác minh (ghi phạm vi môn/khối) | Yêu cầu bổ sung (lý do) | Từ chối (lý do)
  → quay lại AD03, hồ sơ rời hàng chờ
```
Hết phiên quản trị (30 phút không hoạt động) đưa về AD01 với thông báo, không mất lý do đang gõ nếu nền giữ được (TBD khi làm AD04: nền web hiện không giữ dữ liệu form qua lần đăng nhập lại).

## Luồng 5. Xem lớp và lịch (chỉ xem ở Lát 1)
```text
Thông báo "Đã chốt lớp" hoặc tab Lớp học → CL01 lớp: hai bên, mục tiêu, giá, trạng thái
  → CL02 lịch lớp theo tuần, SessionCard từng buổi → chi tiết buổi
```
Đổi, hủy, kết thúc lớp, nhật ký buổi thuộc UC08–UC09, ngoài Lát 1. Không vẽ nút cho các việc này.
