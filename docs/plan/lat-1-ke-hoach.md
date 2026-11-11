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

Tổng trên chưa gồm các module nền thêm ngày 06/11/2026, vì chưa ước lượng.

## Mốc
| Mốc | Kết quả chạy được | Tương ứng |
|---|---|---|
| M0 Nền móng | Repo, CI, schema, khung API và app; đã diễn tập đổi agent | G4 |
| M1 Rủi ro lớn nhất | TX05 đúng khi đồng thời qua hai bản API | G4 |
| **Cổng phỏng vấn** | Đã qua bằng chấp nhận rủi ro: OP-05, OP-06 đóng ngày 06/11/2026, chưa có phỏng vấn và chưa thử bản mẫu | Điều kiện G5 |
| M2 Tài khoản | Đăng ký, đăng nhập, quản lý phiên trên điện thoại thật | G5 |
| M3 Hồ sơ | Có hồ sơ con và gia sư đã được duyệt | G5 |
| M4 Lịch và nhu cầu | Phụ huynh xác nhận được nhu cầu | G5 |
| M5 Gợi ý | Danh sách B từ dữ liệu thật của hệ thống | G5 |
| M6 Chốt lớp | Luồng trọn vẹn từ nhu cầu tới lớp | G5 |
| M7 Nghiệm thu | AT01–AT04 và kiểm quyền toàn bộ | Xong Lát 1 |

M1 đặt trước tài khoản có chủ đích: nếu TX05 có vấn đề, biết sớm khi chi phí sửa còn thấp. Test gọi service bằng dữ liệu seed, chưa cần màn hình.

Mốc vẫn kết thúc bằng thứ chạy được trên app. Issue giao diện của một mốc phải xong trong mốc đó, không dồn về cuối.

Thứ tự việc của Đính sau TB-9, quy tắc tránh xung đột và Demo 1 (M2 cùng phần hồ sơ con của M3): xem [lat-1-demo-1.md](lat-1-demo-1.md).

## Cách chia Issue
Theo [CR-012](../decisions/change-requests.md):

- **Chủ trì kiến trúc:** Đính cho backend Spring, Next.js và React Native; Huy cho AI Python. Người chủ trì duyệt quyết định kiến trúc của phần mình (XC01). Cả hai vẫn cùng code backend và React Native (CR-001).
- **Tách backend và giao diện.** Chức năng có cả API và màn hình thành hai Issue. Issue giao diện phụ thuộc Issue backend và dùng API client sinh từ hợp đồng, nên có thể giao cho người khác.
- **Số Issue:** Issue giao diện đã tạo ngày 06/11/2026: GD-11 = #67, GD-13 = #68, GD-15 = #69, GD-16 = #70, GD-17 = #71, GD-19 = #72, GD-20 = #73, GD-21 = #74, GD-22 = #75. Mã cũ: Issue backend giữ mã `TB-` cũ, trùng số Issue đã có trên GitHub. Issue giao diện mới có mã tạm `GD-<số TB>`, Issue nền có mã `NEN-MB`, `NEN-WEB`. Số thật do GitHub cấp khi tạo.

## Module nền
Đính quyết định ngày 06/11/2026: dựng xong nền cho backend, mobile, web trước khi chia lại Issue chức năng, để Issue sau chỉ thêm chức năng. Mỗi module là một Issue và một PR nhỏ, làm lần lượt theo bảng. Quyết định thư viện ở [ADR-0009](../decisions/adr/0009-nen-giao-dien.md) và [ADR-0010](../decisions/adr/0010-nen-backend.md).

Trạng thái: cả 14 module đã merge vào `dev` ngày 06/11/2026 qua #63–#66 theo ngoại lệ OP-15. Bước tiếp theo là chia lại Issue chức năng theo bảng dưới.

| Module | Nội dung | Phụ thuộc | Thuộc |
|---|---|---|---|
| N0 | ADR, quy ước, cấu trúc thư mục, cách thêm một chức năng (#43) | #42 | Chung |
| S1 | Gói `@tutorbridge/shared`: kiểu Problem, `Idempotency-Key`, định dạng ngày giờ và tiền | N0 | Chung |
| B1 | Tầng web dùng chung: `Idempotency-Key`, `If-Match`/ETag, `Cache-Control`, JSON chặt, lỗi validate | N0 | `NEN-BE` |
| B2 | Khung xác thực: `Actor`, `SessionResolver`, bộ lọc bearer, helper test. TB-08 nối phần tra phiên | B1 | `NEN-BE` |
| B3 | Bộ test: lớp cơ sở test HTTP, fixture, kiểm phản hồi theo OpenAPI | B2 | `NEN-BE` |
| B4 | Vận hành local: health, log JSON che dữ liệu nhạy cảm, `Clock` và sinh ID, seed giả | B1 | `NEN-BE` |
| M1 | Expo Router, cấu trúc thư mục, nhóm route theo vai trò, tên gói Android | N0 | `NEN-MB` |
| M2 | Tầng API: TanStack Query, client gắn phiên, đọc Problem, lệnh ghi, tra trạng thái lệnh | M1, S1 | `NEN-MB` |
| M3 | Phiên: `expo-secure-store`, chặn route, hết phiên | M2 | `NEN-MB` |
| M4 | Nền giao diện: theme, component nền, khung màn hình đủ trạng thái | M1 | `NEN-MB` |
| M5 | Form, định dạng, test mẫu, giả lập API | M2, M4 | `NEN-MB` |
| W1 | Cấu trúc, Tailwind, shadcn/ui, nhóm route công khai và quản trị | N0 | `NEN-WEB` |
| W2 | Phiên quản trị: cookie, CSRF, `proxy.ts`, client phía máy chủ | W1, S1 | `NEN-WEB` |
| W3 | Form, khung trang đủ trạng thái, Vitest | W2 | `NEN-WEB` |

## Danh sách Issue
Người phụ trách là đề xuất, Đính chốt khi chia việc. `NEN-BE`, `NEN-MB`, `NEN-WEB` là nhóm module nền ở trên.

Giờ của Issue backend có dấu `*` là ước lượng cũ cho cả API lẫn màn hình. Chia lại giờ khi tạo Issue giao diện.

| Mã | Mốc | Việc | Phụ thuộc | Giờ | Người | API | Màn hình |
|---|---|---|---|---|---|---|---|
| TB-01 | M0 | Monorepo, `contracts/`, Docker Compose, chốt phiên bản, cập nhật lệnh trong AGENTS.md | | 6–10 | Đính | | |
| TB-02 | M0 | CI ngày đầu, hook, kiểm `skills-sync` | TB-01 | 6–10 | Đính | | |
| TB-03 | M0 | Khung backend, Flyway V1–V6, Testcontainers, test kiến trúc, tài khoản DB quyền tối thiểu | TB-01 | 12–20 | Đính | | |
| TB-04 | M0 | Platform: Problem, traceId, receipt, outbox, phân trang con trỏ | TB-03 | 16–24 | Đính | | |
| TB-05 | M0 | Khung mobile, web, API client sinh từ OpenAPI | TB-01 | 12–20 | Đính | | |
| TB-06 | M0 | Diễn tập: Claude làm dở, Codex tiếp, người kia review; sửa playbook theo chỗ vướng | TB-02 | 3–5 | Cả hai | | |
| TB-07 | M1 | TX05 ở tầng service, test đồng thời qua hai bản API | TB-04 | 24–36 | Đính | | |
| NEN-BE | M2 | Nền backend: module B1–B4 | TB-04 | Xong (#64) | Đính | | |
| NEN-MB | M2 | Nền mobile: module M1–M5; viết lại `mobile/AGENTS.md` | TB-05 | Xong (#65) | Đính | | |
| TB-08 | M2 | Đăng ký, xác minh, đăng nhập, phiên; ADR băm mật khẩu; viết `SessionResolver` cho khung của B2 | TB-04; B2 cho phần phiên | 20–30 | Huy | `registerAccount`, `verifyEmail`, `resendVerification`, `loginAccount`, `logoutAccount`, `listOwnSessions`, `revokeOwnSession` | |
| TB-09 | M2 | Khôi phục, đổi mật khẩu, tái xác thực | TB-08 | 12–18 | Huy | `requestRecovery`, `resetPassword`, `changePassword`, `reauthenticate` | |
| TB-10 | M2 | Màn hình tài khoản | NEN-MB, TB-09 | 16–24 | Huy | | AUTH01, AUTH02, AUTH03, AUTH04, AUTH05, AUTH06 |
| TB-11 | M3 | API hồ sơ con | TB-08 | 10–16 * | Đính | `listStudents`, `createStudent`, `getStudentProfile`, `patchStudent` | |
| #67 (GD-11) | M3 | Màn hình hồ sơ con | TB-11, NEN-MB | Trong TB-11 | Huy | | PH02 |
| TB-12 | M3 | Hồ sơ gia sư, gửi duyệt, tải minh chứng, kho tệp local | TB-08 | 20–30 | Huy | `saveTutorProfile`, `getOwnTutorProfile`, `submitTutorProfile`, `createEvidenceUpload`, `completeEvidenceUpload` | |
| NEN-WEB | M3 | Nền web quản trị: module W1–W3; viết lại `web/AGENTS.md` | TB-05 | Xong (#66) | Đính | | |
| TB-13 | M3 | API quản trị xác minh, MFA | TB-12 | 24–36 * | Đính | `completeAdminMfa`, `listAssignedVerifications`, `decideVerification`, `readAssignedEvidence` | |
| #68 (GD-13) | M3 | Web quản trị xác minh | TB-13, NEN-WEB | Trong TB-13 | Đính | | AD01, AD03, AD04 |
| TB-14 | M3 | Màn hình hồ sơ gia sư, trang chủ hai vai trò | TB-10, TB-12 | 12–18 | Huy | | PH01, GS01, GS02, GS03 |
| TB-15 | M4 | API lịch rảnh, ngoại lệ, sinh cửa sổ UTC | TB-11 | 24–36 * | Đính | `getTutorCalendar`, `replaceTutorCalendar`, `getStudentCalendar`, `replaceStudentCalendar` | |
| #69 (GD-15) | M4 | Màn hình lịch rảnh | TB-15, NEN-MB | Trong TB-15 | Huy | | GS04 |
| TB-16 | M4 | API nhu cầu có phiên bản | TB-15 | 20–30 * | Đính | `listNeeds`, `createNeedDraft`, `getNeed`, `updateNeedDraft`, `confirmNeed` | |
| #70 (GD-16) | M4 | Màn hình nhu cầu | TB-16, #67 | Trong TB-16 | Huy | | PH03, PH04, PH05 |
| TB-17 | M5 | API lọc cứng, giao lịch, xếp hạng B, lưu lượt gợi ý | TB-12, TB-16 | 28–40 * | Huy | `createMatchingRun`, `getMatchingRun`, `getTutorPublic` | |
| #71 (GD-17) | M5 | Màn hình gợi ý gia sư | TB-17, #70 | Trong TB-17 | Đính | | PH06, PH07 |
| TB-18 | M6 | Gửi, từ chối, hủy, hết hạn đề nghị | TB-17 | 16–24 | Đính | `sendOffer`, `listOffers`, `getOffer`, `rejectOffer`, `cancelOffer` | |
| TB-19 | M6 | Nối `acceptOffer` vào HTTP | TB-07, TB-18 | 16–24 * | Huy | `acceptOffer` | |
| #72 (GD-19) | M6 | Màn hình đề nghị | TB-19, #71 | Trong TB-19 | Đính | | PH08, PH09, GS05 |
| TB-20 | M6 | API xem lớp và buổi | TB-19 | 10–16 * | Huy | `getClass`, `listSessions` | |
| #73 (GD-20) | M6 | Màn hình lớp và buổi | TB-20, #72 | Trong TB-20 | Huy | | CL01, CL02 |
| TB-21 | M6 | Bộ phát thông báo, API hộp thông báo | TB-18 | 12–18 * | Đính | `listOwnNotifications`, `markNotificationRead` | |
| #74 (GD-21) | M6 | Màn hình hộp thông báo | TB-21, NEN-MB | Trong TB-21 | Huy | | COM01 |
| TB-22 | M6 | API tra trạng thái lệnh | TB-19 | 8–12 * | Huy | `getCommandStatus`, `findCommandStatus` | |
| #75 (GD-22) | M6 | Xử lý chưa rõ kết quả trên app bằng tra trạng thái lệnh | TB-22, #72 | Trong TB-22 | Đính | | |
| TB-23 | M7 | Ca quyền trên mọi API, AT01–AT04, hai bản API | TB-22, các Issue GD của M6 | 16–24 | Cả hai | | |
| TB-24 | M7 | Job dọn receipt và outbox theo lô | TB-04 | 6–10 | Đính | | |

Cách gán người ở trên: Issue backend giữ người cũ, Issue giao diện thường giao cho người không làm backend của chức năng đó, để cả hai hiểu cả hai phía. Theo ước lượng cũ, phần Huy vẫn nhiều hơn phần Đính. Đính cân lại khi chia lại giờ.

## Việc không phải code, chạy song song
| Việc | Người | Hạn |
|---|---|---|
| ~~Phỏng vấn tối thiểu 10 phụ huynh, 10 gia sư~~: đã thay bằng chấp nhận rủi ro (OP-05, 06/11/2026) | Đính | Xong |
| ~~Thử bản mẫu theo tiêu chí đã chốt~~: đã thay bằng chấp nhận rủi ro (OP-06, 06/11/2026) | Đính | Xong |
| Ngân sách và mốc (OP-01) | Đính, Huy | Trước khi cam kết ngày |
| Thêm Huy vào repo (OP-13) | Đính | Xong |
| Quyết định môi trường thử chung, ngoài máy local | Đính | Trước M6 |

## Rủi ro kế hoạch
- Ước lượng chưa có cơ sở thực tế. Sau M1, tính lại theo giờ thực tế ghi trên Issue.
- Tăng tốc nhờ agent có thể đổi lại nợ hiểu biết. Nếu review bị dồn, giảm tốc độ tạo code, không giảm review.
- Chi tiết như mẫu email, kho tệp, thiết bị thử có thể làm phát sinh giờ chưa tính.
- Thiếu nguồn gia sư thật không chặn Lát 1 vì dùng dữ liệu giả, nhưng chặn R0.
- Làm Lát 1 khi chưa phỏng vấn và chưa thử bản mẫu (OP-05, OP-06). Có thể làm đúng kỹ thuật nhưng sai nhu cầu. Lát 1 không chứng minh được nhu cầu thị trường.
- Tách backend và giao diện thêm điểm bàn giao giữa hai người. Issue giao diện chờ Issue backend merge; hợp đồng phải chốt trước để hai bên làm song song được.
