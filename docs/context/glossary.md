# Thuật ngữ

Dùng đúng một tên cho một khái niệm. Giao diện dùng tiếng Việt, code dùng tiếng Anh.

## Khái niệm nghiệp vụ
| Tiếng Việt | Trong code | Nghĩa |
|---|---|---|
| Phụ huynh | Parent, `PARENT` | Quản lý hồ sơ con, gửi đề nghị, xác nhận tham gia |
| Gia sư | Tutor, `TUTOR` | Dạy, duyệt nội dung chuyên môn |
| Học sinh | Student, `STUDENT` | Hồ sơ do phụ huynh quản lý; đăng nhập là P1 |
| Quản trị | Admin, `ADMIN` | Tài khoản cấp riêng, có scope từng việc |
| Nhu cầu | Need, `learning_needs` | Yêu cầu tìm gia sư, có phiên bản (`need_versions`) |
| Gợi ý | Matching run | Một lần lọc và xếp hạng |
| Đề nghị | Offer, `class_offers` | Phụ huynh gửi gia sư, điều kiện cố định |
| Lớp | Class, `classes` | Thỏa thuận 1–1 sau khi chốt |
| Buổi | Session, `lesson_sessions` | Một buổi học cụ thể. **Khác** phiên đăng nhập |
| Phiên | Auth session, `auth_sessions` | Phiên đăng nhập |
| Booking | `resource_bookings` | Chiếm giờ của một tài nguyên; mỗi buổi có hai dòng |
| Tài nguyên | Resource | Gia sư hoặc học sinh trong kiểm tra trùng lịch |
| Chu kỳ | Cycle | 28 ngày, gồm 4 khối 7 ngày |
| Giá | `rate_vnd_60` | VND cho 60 phút |
| Giá buổi | Session price | `ceil(rate × D / 60)` |
| Ngân sách | `budget_vnd_session` | Trần VND mỗi buổi |
| Nguồn, học liệu | Material | Tài liệu của lớp, có phiên bản |
| Đoạn nguồn | Source segment | Đoạn trích có vị trí |
| Dẫn xuất | Derivative | Nội dung tạo ra từ nguồn |
| Bài soạn | Plan, `lesson_plans` | Kế hoạch buổi, có `plan_versions` |
| Công bố | Publication | Con trỏ tới phiên bản đã duyệt |
| Nhật ký | Lesson log | Gia sư ghi sau buổi |
| Xác nhận tham gia | Attendance decision | Ý kiến phụ huynh trên đúng một phiên bản nhật ký |
| Vụ việc | Support case | Yêu cầu hỗ trợ |
| Quyền tạm | Support grant | Tối đa 24 giờ, xem INV-04 |

## Kỹ thuật
| Thuật ngữ | Nghĩa |
|---|---|
| Receipt | Bản ghi kết quả lệnh, theo actor + loại lệnh + `Idempotency-Key` |
| Outbox | Sự kiện ghi cùng commit, phát sau |
| Revision | Số phiên bản đối tượng, dùng cho ETag |
| Version | Bản nghiệp vụ bất biến. **Khác** revision |
| Epoch | Số tăng khi thu hồi: `auth_epoch` cho phiên, `access_epoch` cho nguồn |
| Lease | Quyền xử lý tác vụ có thời hạn |
| Fencing token | Số chặn kết quả từ worker đã mất lease |
| Tombstone | Dấu vết tối thiểu sau khi xóa |
| S | Điểm xếp hạng có AI, BR03 |
| B | Điểm đối chứng không dùng AI, BR03 |

## Trạng thái theo schema
| Đối tượng | Giá trị |
|---|---|
| Tài khoản | `UNVERIFIED`, `ACTIVE`, `LOCKED`, `DELETED` |
| Hồ sơ gia sư | `DRAFT`, `PENDING`, `APPROVED`, `REJECTED`, `RETIRED` |
| Xác minh | `PENDING`, `VERIFIED`, `REJECTED`. NEEDS_INFO là `PENDING` kèm lý do (G3-D07-05) |
| Nhu cầu | `DRAFT`, `CONFIRMED`, `CLOSED` |
| Lượt gợi ý | `RUNNING`, `COMPLETED`, `FAILED`; chế độ `S`, `B` |
| Đề nghị | `PENDING`, `ACCEPTED`, `REJECTED`, `CANCELLED`, `EXPIRED` |
| Lớp | `WAITING`, `ACTIVE`, `ENDED` |
| Buổi | `SCHEDULED`, `COMPLETED`, `CANCELLED`, `ABSENT` |
| Mục tiêu lớp | `PROPOSED`, `EFFECTIVE`, `SUPERSEDED`, `REJECTED` |
| Phiên bản tài liệu | `UPLOADING`, `PROCESSING`, `REVIEW`, `READY`, `FAILED`, `REVOKED`, `DELETED` |
| Tác vụ AI | `QUEUED`, `RUNNING`, `COMPLETED`, `FAILED`, `CANCELLED`, `TIMED_OUT` |
| Receipt | `PROCESSING`, `SUCCEEDED`, `FAILED` |

Khi lệch, schema trong migration là chuẩn.

## Mã tài liệu
| Tiền tố | Nghĩa |
|---|---|
| FR, NFR | Yêu cầu chức năng, phi chức năng |
| UC, AC, AT | Use case, tiêu chí nghiệm thu, kịch bản kiểm thử |
| BR | Quy tắc nghiệp vụ |
| TX | Giao dịch |
| F01–F06 | Sáu luồng thiết kế |
| G0–G8 | Giai đoạn dự án |
| R0a, R0, R1, R2 | Lần phát hành |
| P0, P1, P2 | Mức ưu tiên |
| D, DP, XC, OP | Quyết định, việc còn mở trong sổ quyết định |
| CR, ADR | Thay đổi, quyết định kiến trúc |
| INV | Bất biến |
