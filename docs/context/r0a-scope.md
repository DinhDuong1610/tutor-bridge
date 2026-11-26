# Phạm vi code hiện tại: Lát 1 của R0a

Theo CR-003. Việc nằm ngoài file này thì **không làm**, kể cả khi có sẵn trong OpenAPI hoặc schema. Muốn thêm phải sửa file này qua PR.

Ba mức cần phân biệt:
- **Lát 1:** đợt code đầu tiên, mô tả ở đây.
- **R0a:** bản nội bộ theo PRD, gồm Lát 1 cộng nguồn, soạn và duyệt.
- **R0:** thí điểm kín, cần toàn bộ P0 và P1.

## Mục tiêu
Trên môi trường thử, phụ huynh tạo được nhu cầu, nhận gợi ý phương án B, gửi đề nghị; gia sư chấp nhận; hệ thống chốt lớp và toàn bộ buổi đúng một lần, kể cả khi đồng thời. Dùng dữ liệu giả, AI tắt.

## Yêu cầu
| Có | Ghi chú |
|---|---|
| FR-01, 02, 03, 39 | Tài khoản, phiên, hồ sơ con |
| FR-04 | Nhu cầu có phiên bản |
| FR-05, 06, 07 | Hồ sơ gia sư, học phí, xác minh |
| FR-08, 19 | Lịch rảnh; xem lớp và buổi |
| FR-09, 10, 12, 13, 14 | Lọc, tối đa năm kết quả, lưu phiên bản, phân biệt rỗng và lỗi |
| FR-11 | Chỉ phương án B. Điểm S chờ OP-11 |
| FR-15, 16, 17 | Đề nghị và chốt lớp |
| FR-18 | Chỉ chuyển mục tiêu ban đầu vào lớp. Đề xuất đổi mục tiêu để sau |
| FR-37, 41 | Lỗi, thử lại, thông báo trong hệ thống cho đề nghị và chốt lớp |

## API: 53 trên 130 thao tác
| Nhóm | Số | operationId |
|---|---|---|
| Xác thực | 11 | `registerAccount`, `verifyEmail`, `resendVerification`, `loginAccount`, `logoutAccount`, `requestRecovery`, `resetPassword`, `listOwnSessions`, `revokeOwnSession`, `changePassword`, `reauthenticate` |
| Quản trị xác minh | 7 | `loginAdmin`, `completeAdminMfa`, `getAdminSession`, `listAssignedVerifications`, `getAssignedVerification`, `decideVerification`, `readAssignedEvidence` |
| Học sinh | 4 | `listStudents`, `createStudent`, `getStudentProfile`, `patchStudent` |
| Hồ sơ gia sư | 7 | `saveTutorProfile`, `getOwnTutorProfile`, `submitTutorProfile`, `getOwnTutorVerification`, `createEvidenceUpload`, `uploadEvidenceContent`, `completeEvidenceUpload` |
| Lịch | 4 | `getTutorCalendar`, `replaceTutorCalendar`, `getStudentCalendar`, `replaceStudentCalendar` |
| Nhu cầu | 5 | `listNeeds`, `createNeedDraft`, `getNeed`, `updateNeedDraft`, `confirmNeed` |
| Gợi ý | 3 | `createMatchingRun`, `getMatchingRun`, `getTutorPublic` |
| Đề nghị | 6 | `sendOffer`, `listOffers`, `getOffer`, `acceptOffer`, `rejectOffer`, `cancelOffer` |
| Lớp | 2 | `getClass`, `listSessions` |
| Trạng thái lệnh | 2 | `getCommandStatus`, `findCommandStatus` |
| Thông báo | 2 | `listOwnNotifications`, `markNotificationRead` |

## Màn hình: 26 trên 52
| Kênh | Mã |
|---|---|
| Android chung | AUTH01–AUTH06, COM01 |
| Android phụ huynh | PH01–PH09 |
| Android gia sư | GS01–GS05 |
| Android lớp | CL01, CL02 (chỉ xem) |
| Web quản trị | AD01, AD03, AD04 |

## Giao dịch
TX01–TX06, TX16, TX30 cùng các giao dịch tài khoản của F01. TX05 là rủi ro cao nhất, làm theo playbook `transaction-change`.

## Migration
Áp đủ V1–V6 (CR-005a, CR-009). Có bảng chưa dùng là đúng dự kiến.

## Điều kiện xong Lát 1
- Toàn bộ AC của UC01–UC07 thuộc phạm vi trên đạt, gồm AT01–AT04.
- Test đồng thời TX05 qua hai bản API trên PostgreSQL thật.
- Phân biệt được quyền giữa hai gia đình trên từng API (AC-UC02-02).
- INV liên quan có test.
- Đính và Huy ghi lại giờ thực tế để ước lượng lại R0.

## Không làm ở Lát 1
- UC08 đổi lịch, hủy, kết thúc lớp; UC09 nhật ký và xác nhận; UC10–UC12 học liệu, AI, bài tập; UC13 tiến trình; UC14 hỗ trợ; UC16 xóa dữ liệu.
- Điểm S và mọi gọi AI thật.
- Tài khoản học sinh, lời mời (P1).
- Dữ liệu người dùng thật, push, email nghiệp vụ, thanh toán, iOS, web gia sư.
