# Thành phần (component)

Mỗi component trong Figma có **cùng tên và cùng variant** với component trong code, để agent tra được ngay. Component mới chỉ thêm khi có màn hình Lát 1 cần. Thêm vào Figma trước, thêm vào code ở Issue giao diện đầu tiên dùng nó, đặt trong `shared/ui` nếu từ hai tính năng trở lên dùng.

## Đã có trong code
| Figma | Mobile `shared/ui/` | Web `shared/ui/` | Variant và thuộc tính |
|---|---|---|---|
| `Button` | `Button.tsx` | `button.tsx` (shadcn) | `variant`: primary, secondary (web thêm destructive, outline, ghost); `busy`; `disabled`. Nhãn là động từ nói đúng kết quả: "Gửi đề nghị", không "Xác nhận" |
| `TextField` | `TextField.tsx`, `FormTextField` | `input.tsx`, `form-field.tsx` | Nhãn luôn hiện phía trên (không dùng placeholder làm nhãn); gợi ý; lỗi cạnh trường; bộ đếm ký tự khi có giới hạn |
| `Notice` | `Notice.tsx` | `notice.tsx` | `tone`: info, success, warning, danger. Có chữ nhãn tone ("Thông tin", "Đã xong", "Lưu ý", "Lỗi"); `action` tùy chọn |
| `QueryScreen` | `QueryScreen.tsx` | `StateMessage`, `EmptyState` trong `admin-page.tsx` | Khung đọc dữ liệu: tải, rỗng, lỗi, mất quyền, không tìm thấy, dữ liệu cũ |
| `CommandStatus` | `CommandStatus.tsx` | `command-status.tsx` | `state`: sending, confirmed, failed, unknown. Đặt ngay cạnh nút gửi |
| `AdminPage` | | `admin-page.tsx` | Khung trang quản trị: tiêu đề, mô tả, nội dung |
| `ComingSoon` | `ComingSoon.tsx` | `coming-soon.tsx` | Chỉ dùng tạm cho route nền, không vẽ thêm trong Figma |

Trong Figma, `QueryScreen` và `CommandStatus` không là một khối hình mà là **bộ khung trạng thái**: mỗi trạng thái một variant, dùng lại cho mọi màn hình ([04](04-trang-thai-cau-chu.md)).

## Cần có cho Lát 1
| Figma | Dùng ở | Variant và nội dung | Quy tắc phải giữ |
|---|---|---|---|
| `SessionCard` (thẻ lịch) | PH01, PH05, PH08, PH09, GS01, GS05, CL02 | Xem mục dưới | Điểm nhấn duy nhất ([01](01-dinh-huong.md#điểm-nhấn-duy-nhất-thẻ-lịch-buổi-học)) |
| `CycleStrip` (dải 28 ngày) | PH05, PH08, GS05, CL02 | 4 khối `Tuần 1`–`Tuần 4`, mỗi buổi một dấu; buổi đã qua, đã hủy khác hình dấu | Tối đa 12 buổi, đủ số buổi mỗi khối (BR04) |
| `StudentSwitcher` (con đang chọn) | Mọi màn hình phụ huynh | Thanh trên cùng: "Đang xem: Minh An, lớp 8" và nút "Đổi"; bảng chọn danh sách con | Luôn hiện. Đổi con tải lại toàn bộ dữ liệu màn hình (AC-UC02-01) |
| `PriceBlock` | PH06, PH07, PH08, GS02, GS05, CL01 | Giá theo 60 phút, thời lượng, giá một buổi | Luôn đủ ba số P, D, giá buổi; giá buổi làm tròn lên do máy chủ trả, không tự tính ở giao diện (BR02) |
| `VerificationLabel` | PH06, PH07, GS02, GS03 | `self-declared` ("Tự khai"), `verified` ("Đã xác minh: Toán 8") kèm icon khác nhau | Nêu đúng phạm vi đã kiểm, không ghi "đảm bảo" (FR-07) |
| `StatusLabel` | PH03, PH09, GS02, GS03, GS05, CL01, AD03 | Một variant cho mỗi giá trị trạng thái của hợp đồng (bảng dưới) | Chữ và icon, nền màu nhạt; không chỉ chấm màu |
| `VersionNotice` | PH03, PH05, PH06, PH08, GS02 | `stale` ("Đây là bản cũ"), `changed` ("Giá hoặc lịch đã đổi, hãy xem lại") | Bản cũ chỉ để đối chiếu, nút gửi ẩn (UC04) |
| `Deadline` | PH08, PH09, GS05 | "Hết hạn lúc 18:00 20/11/2026" và "còn 5 giờ" | Thời điểm tuyệt đối luôn hiện, thời gian còn lại chỉ phụ trợ |
| `TutorCard` | PH06 | Tên hiển thị, kinh nghiệm, `VerificationLabel`, `PriceBlock`, lịch chung tóm tắt, lý do khớp | Không có ảnh, không có liên hệ, không có số điểm (mục dưới) |
| `WeekAvailability` (lịch rảnh tuần) | PH04, GS04 | Lưới 7 ngày theo khung giờ; ô `draft`, `effective`, `booked`; ngoại lệ theo ngày | Buổi đã đặt không sửa được ở đây (UC07); giờ 24 giờ |
| `ChoiceGroup` | PH04, AUTH01 | Lựa chọn một trong vài giá trị cố định: thời lượng 60/90/120, số buổi 1–3, vai trò | Mỗi lựa chọn có chữ, vùng chạm ≥ 48 |
| `StickyActionBar` | Mọi màn hình có nút chính trên mobile | Nút chính, nút phụ tùy chọn, `CommandStatus` phía trên | Không bị bàn phím che; chữ 200% vẫn thấy nút |
| `ConfirmSheet` | PH05, PH08, PH09 (hủy), GS05, AUTH06 | Bảng từ dưới lên: tóm tắt điều sắp xảy ra, nút chính, "Quay lại" | Dùng cho lệnh không hoàn tác hoặc cam kết. Nút nêu đúng việc: "Hủy đề nghị" |
| `NotificationItem` | COM01, PH01, GS01 | `unread` (chấm và chữ "Chưa đọc"), `read` | Tiêu đề không chứa dữ liệu riêng (UC15) |
| `DataTable` | AD03 | Tiêu đề cột, dòng, lọc theo trạng thái, phân trang bằng "Xem thêm" | Phân trang bằng `cursor` theo hợp đồng, không có số trang |
| `EvidenceViewer` | AD04 | Xem tệp minh chứng cạnh biểu mẫu quyết định | Chỉ hồ sơ được giao |

## Thẻ lịch buổi học (`SessionCard`)
| Variant | Khi nào | Hình |
|---|---|---|
| `scheduled` | Buổi của lớp đã chốt (`Session.state = SCHEDULED`) | Khối ngày đặc `primary`, chữ "Đã chốt" và icon lịch có dấu tích |
| `proposed` | Buổi trong đề nghị chưa chốt (`Offer.schedule`) | Khối ngày viền nét đứt, nền `surface`, chữ "Trong đề nghị, chưa chốt" |
| `cancelled` | `CANCELLED` (Lát 1 chỉ xem) | Chữ gạch ngang giờ, nhãn "Đã hủy", khối ngày nền `surface` |
| `past` | Đã qua giờ kết thúc | Chữ `textMuted`, nhãn "Đã qua" |
| `compact` | Danh sách dài (CL02 theo tuần) | Một dòng: khối ngày nhỏ, giờ, trạng thái |

Không variant nào chỉ khác nhau về màu. `COMPLETED`, `ABSENT` thuộc UC09, ngoài Lát 1: chưa vẽ.

## Nhãn trạng thái theo hợp đồng
Câu chữ theo giá trị trong `contracts/openapi.yaml`. Câu chữ mới phải thêm vào bảng này trước khi vẽ.

| Đối tượng | Giá trị | Nhãn | Tone |
|---|---|---|---|
| Nhu cầu `Need.status` | `DRAFT` | Nháp | trung tính |
| | `CONFIRMED` | Đã xác nhận | info |
| | `CLOSED` | Đã đóng | trung tính |
| Đề nghị `Offer.state` | `PENDING` | Đang chờ gia sư trả lời | info |
| | `ACCEPTED` | Đã chốt lớp | success |
| | `REJECTED` | Gia sư đã từ chối | trung tính |
| | `CANCELLED` | Đã hủy | trung tính |
| | `EXPIRED` | Đã hết hạn | warning |
| Hồ sơ gia sư `TutorProfileDraft.status` | `DRAFT` | Nháp, chưa gửi duyệt | trung tính |
| | `PENDING` | Đang chờ xác minh | info |
| | `APPROVED` | Đã duyệt | success |
| | `REJECTED` | Chưa được duyệt | danger |
| | `RETIRED` | Bản cũ | trung tính |
| Lượt gợi ý `MatchingRun.status` | `RUNNING` | Đang tìm gia sư phù hợp | info |
| | `COMPLETED` | (không hiện nhãn, hiện kết quả) | |
| | `FAILED` | Không tìm được lúc này | danger |
| Lớp `Class.state` | `WAITING` | Chưa bắt đầu | info |
| | `ACTIVE` | Đang học | success |
| | `ENDED` | Đã kết thúc | trung tính |
| Buổi `Session.state` | `SCHEDULED` | Đã chốt | success |
| | `CANCELLED` | Đã hủy | trung tính |

Lý do kết thúc đề nghị (`reasonCode`) hiện thành câu đầy đủ dưới nhãn. Danh sách mã lấy từ hợp đồng khi làm PH09, GS05; mã chưa có câu chữ thì dừng và hỏi Đính, không tự viết theo đoán.

## Không có điểm số trên thẻ gia sư (đề xuất, Đính chốt khi duyệt PH06)
`Candidate` có `rank` và `reasons`. Giao diện hiện **thứ tự** và **lý do khớp**, không hiện điểm S, B hay phần trăm. Lý do: điểm không phải xác suất phù hợp hay tiến bộ (BR03, PH07). Lát 1 chỉ có phương án B (`MatchingRun.mode = B`): đầu danh sách luôn ghi "Danh sách lọc theo điều kiện của bạn, không dùng xếp hạng AI" (BR03, UC05).
