# Kế hoạch Lát 1

Chia [phạm vi Lát 1](../context/r0a-scope.md) thành các mốc nhỏ. Mỗi mốc kết thúc bằng thứ chạy được.

**Giờ ở đây là ước lượng thô của tài liệu, chưa có số liệu thực tế.** Agent giúp viết code nhanh hơn, nhưng review, hiểu code, gỡ lỗi và thử trên thiết bị vẫn tốn giờ người. Ước lượng lại sau M1 bằng giờ thực tế.

## Năng lực
40 giờ/tuần cho hai người. Giữ 20% dự phòng theo Kế hoạch mục 4.1, còn 32 giờ/tuần cho việc đã biết.

| Tổng ước lượng | Số tuần |
|---|---|
| Thấp: 349 giờ | Khoảng 11 tuần |
| Cao: 531 giờ | Khoảng 17 tuần |

Tức Lát 1 đã xấp xỉ hoặc vượt khung 10 tuần của G4 và G5 trong kế hoạch gốc. R0 đầy đủ sẽ cần phạm vi hoặc thời gian lớn hơn, phải ước lượng lại. Không dùng các số này làm cam kết ngày.

## Mốc
| Mốc | Kết quả chạy được | Tương ứng |
|---|---|---|
| M0 Nền móng | Repo, CI, schema, khung API và app; đã diễn tập đổi agent | G4 |
| M1 Rủi ro lớn nhất | TX05 đúng khi đồng thời qua hai bản API | G4 |
| **Cổng phỏng vấn** | Có kết quả OP-05, OP-06 trước khi mở rộng tính năng | Điều kiện G5 |
| M2 Tài khoản | Đăng ký, đăng nhập, quản lý phiên trên điện thoại thật | G5 |
| M3 Hồ sơ | Có hồ sơ con và gia sư đã được duyệt | G5 |
| M4 Lịch và nhu cầu | Phụ huynh xác nhận được nhu cầu | G5 |
| M5 Gợi ý | Danh sách B từ dữ liệu thật của hệ thống | G5 |
| M6 Chốt lớp | Luồng trọn vẹn từ nhu cầu tới lớp | G5 |
| M7 Nghiệm thu | AT01–AT04 và kiểm quyền toàn bộ | Xong Lát 1 |

M1 đặt trước tài khoản có chủ đích: nếu TX05 có vấn đề, biết sớm khi chi phí sửa còn thấp. Test gọi service bằng dữ liệu seed, chưa cần màn hình.

## Danh sách Issue
Người phụ trách là đề xuất, Đính chốt khi chia việc. Mã `TB-` chỉ là tạm; số thật do GitHub cấp.

| Mã | Mốc | Việc | Phụ thuộc | Giờ | Người | API | Màn hình |
|---|---|---|---|---|---|---|---|
| TB-01 | M0 | Monorepo, `contracts/`, Docker Compose, chốt phiên bản, cập nhật lệnh trong AGENTS.md | | 6–10 | Đính | | |
| TB-02 | M0 | CI ngày đầu, hook, kiểm `skills-sync` | TB-01 | 6–10 | Huy | | |
| TB-03 | M0 | Khung backend, Flyway V1–V6, Testcontainers, test kiến trúc, tài khoản DB quyền tối thiểu | TB-01 | 12–20 | Đính | | |
| TB-04 | M0 | Platform: Problem, traceId, receipt, outbox, phân trang con trỏ | TB-03 | 16–24 | Đính | | |
| TB-05 | M0 | Khung mobile, web, API client sinh từ OpenAPI | TB-01 | 12–20 | Huy | | |
| TB-06 | M0 | Diễn tập: Claude làm dở, Codex tiếp, người kia review; sửa playbook theo chỗ vướng | TB-02 | 3–5 | Cả hai | | |
| TB-07 | M1 | TX05 ở tầng service, test đồng thời qua hai bản API | TB-04 | 24–36 | Đính | | |
| TB-08 | M2 | Đăng ký, xác minh, đăng nhập, phiên; ADR băm mật khẩu | TB-04 | 20–30 | Huy | `registerAccount`, `verifyEmail`, `resendVerification`, `loginAccount`, `logoutAccount`, `listOwnSessions`, `revokeOwnSession` | |
| TB-09 | M2 | Khôi phục, đổi mật khẩu, tái xác thực | TB-08 | 12–18 | Huy | `requestRecovery`, `resetPassword`, `changePassword`, `reauthenticate` | |
| TB-10 | M2 | Màn hình tài khoản | TB-05, TB-09 | 16–24 | Huy | | AUTH01, AUTH02, AUTH03, AUTH04, AUTH05, AUTH06 |
| TB-11 | M3 | Hồ sơ con | TB-08 | 10–16 | Đính | `listStudents`, `createStudent`, `getStudentProfile`, `patchStudent` | PH02 |
| TB-12 | M3 | Hồ sơ gia sư, gửi duyệt, tải minh chứng, kho tệp local | TB-08 | 20–30 | Huy | `saveTutorProfile`, `getOwnTutorProfile`, `submitTutorProfile`, `createEvidenceUpload`, `completeEvidenceUpload` | |
| TB-13 | M3 | Quản trị xác minh, MFA, web | TB-12 | 24–36 | Đính | `completeAdminMfa`, `listAssignedVerifications`, `decideVerification`, `readAssignedEvidence` | AD01, AD03, AD04 |
| TB-14 | M3 | Màn hình hồ sơ gia sư, trang chủ hai vai trò | TB-10, TB-12 | 12–18 | Huy | | PH01, GS01, GS02, GS03 |
| TB-15 | M4 | Lịch rảnh, ngoại lệ, sinh cửa sổ UTC | TB-11 | 24–36 | Đính | `getTutorCalendar`, `replaceTutorCalendar`, `getStudentCalendar`, `replaceStudentCalendar` | GS04 |
| TB-16 | M4 | Nhu cầu có phiên bản | TB-15 | 20–30 | Đính | `listNeeds`, `createNeedDraft`, `getNeed`, `updateNeedDraft`, `confirmNeed` | PH03, PH04, PH05 |
| TB-17 | M5 | Lọc cứng, giao lịch, xếp hạng B, lưu lượt gợi ý | TB-12, TB-16 | 28–40 | Huy | `createMatchingRun`, `getMatchingRun`, `getTutorPublic` | PH06, PH07 |
| TB-18 | M6 | Gửi, từ chối, hủy, hết hạn đề nghị | TB-17 | 16–24 | Đính | `sendOffer`, `listOffers`, `getOffer`, `rejectOffer`, `cancelOffer` | |
| TB-19 | M6 | Nối `acceptOffer` vào HTTP, màn hình đề nghị | TB-07, TB-18 | 16–24 | Huy | `acceptOffer` | PH08, PH09, GS05 |
| TB-20 | M6 | Xem lớp và buổi | TB-19 | 10–16 | Huy | `getClass`, `listSessions` | CL01, CL02 |
| TB-21 | M6 | Bộ phát thông báo, hộp thông báo | TB-18 | 12–18 | Đính | `listOwnNotifications`, `markNotificationRead` | COM01 |
| TB-22 | M6 | Tra trạng thái lệnh, xử lý chưa rõ kết quả trên app | TB-19 | 8–12 | Huy | `getCommandStatus`, `findCommandStatus` | |
| TB-23 | M7 | Ca quyền trên mọi API, AT01–AT04, hai bản API | TB-22 | 16–24 | Cả hai | | |
| TB-24 | M7 | Job dọn receipt và outbox theo lô | TB-04 | 6–10 | Đính | | |

## Việc không phải code, chạy song song
| Việc | Người | Hạn |
|---|---|---|
| Phỏng vấn tối thiểu 10 phụ huynh, 10 gia sư, hoặc ghi nhận chấp nhận rủi ro (OP-05) | Đính, Huy | Trước M2 |
| Thử bản mẫu theo tiêu chí đã chốt (OP-06) | Cả hai | Trước M2 |
| Ngân sách và mốc (OP-01) | Đính, Huy | Trước khi cam kết ngày |
| Thêm Huy vào repo (OP-13) | Đính | Trước TB-02 |
| Quyết định môi trường thử chung, ngoài máy local | Đính | Trước M6 |

## Rủi ro kế hoạch
- Ước lượng chưa có cơ sở thực tế. Sau M1, tính lại theo giờ thực tế ghi trên Issue.
- Tăng tốc nhờ agent có thể đổi lại nợ hiểu biết. Nếu review bị dồn, giảm tốc độ tạo code, không giảm review.
- Chi tiết như mẫu email, kho tệp, thiết bị thử có thể làm phát sinh giờ chưa tính.
- Thiếu nguồn gia sư thật không chặn Lát 1 vì dùng dữ liệu giả, nhưng chặn R0.
