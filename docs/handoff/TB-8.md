# TB-8 Đăng ký, xác minh, đăng nhập, phiên; ADR băm mật khẩu

- **Chủ task:** Huy
- **Nhánh:** `feature/TB-8-account-sessions`
- **Agent đang làm:** Claude (Claude Code), chưa viết code nghiệp vụ
- **Cập nhật lần cuối:** 06/11/2026, rebase lên `dev` tại `8c045d9` (sau nền backend #64)
- **Mã liên quan:** FR-01, FR-02; UC01; AC-UC01-01, AC-UC01-02, AC-UC01-03, AC-UC01-04; INV-01, INV-03, INV-06, INV-18, INV-20, INV-21, INV-63, INV-80, INV-83; ADR-0004

## Mục tiêu
Phụ huynh và gia sư tự đăng ký, xác minh email, đăng nhập bằng phiên opaque, xem và thu hồi phiên của mình, đăng xuất. Có ADR chọn cách băm mật khẩu. Bảy thao tác: `registerAccount`, `verifyEmail`, `resendVerification`, `loginAccount`, `logoutAccount`, `listOwnSessions`, `revokeOwnSession`.

## Kết quả start-task
- Issue #8 đủ chủ task, mã UC/FR, tiêu chí xong.
- Trong phạm vi Lát 1: FR-01, FR-02 và cả bảy thao tác có trong `docs/context/r0a-scope.md`.
- Ngày 06/11/2026 nền backend (#64, ADR-0010) đã có Spring Security, bộ lọc bearer và cookie, `Actor`, giao diện `SessionResolver`, `PublicEndpoints`, tầng web dùng chung và `HttpTest`. TB-8 không dựng bộ lọc riêng.
- Ước tính vượt xa 400 dòng logic, nên đề xuất chia nhỏ (xem Kế hoạch).

## Chỗ hợp đồng, schema và tài liệu chưa khớp
1. **Idempotency cho lệnh công khai.** INV-20 và `docs/design/10-hop-dong-api.md` yêu cầu mọi lệnh ghi có `Idempotency-Key`. Nhưng trong `contracts/openapi.yaml`, `registerAccount`, `verifyEmail`, `resendVerification`, `loginAccount`, `logoutAccount`, `revokeOwnSession` đều có `parameters: []`. `command_receipts.actor_id` là `NOT NULL REFERENCES accounts` (V1), nên lệnh chưa có actor (đăng ký, xác minh, gửi lại, đăng nhập) không ghi receipt được.
2. **Thiếu cột hoặc bảng cho:**
   - `SessionMetadata.deviceLabel` là trường bắt buộc, nhưng `auth_sessions` không có cột tương ứng.
   - `RegistrationInput.termsVersion` là trường bắt buộc, F01 yêu cầu "chấp thuận điều khoản có phiên bản", nhưng không có chỗ lưu.
   - Đếm 5 lần sai trong 15 phút theo tài khoản và theo nguồn gửi: không có bảng. INV-80 cấm giữ trong RAM.
   - Giới hạn gửi lại xác minh và đăng ký theo tài khoản và nguồn: không có bảng.
3. **Tài khoản chưa xác minh đăng nhập được không.** OpenAPI `loginAccount` ghi "reject locked/unverified". F01 ghi "Tài khoản chờ xác minh chưa được tạo lớp hoặc tải học liệu", câu này ngụ ý tài khoản đó vẫn có phiên.
4. **Ngưỡng giới hạn.** UC01 chỉ có số cho đăng nhập (5 lần/15 phút, trì hoãn 15 phút). Đăng ký và gửi lại xác minh không có số.

## Kế hoạch
Đề xuất chia Issue #8 thành 3 PR. Đính chốt việc chia, Đính cấp số migration.
- [ ] **PR 1, theo `contract-change`:** ADR-0008 băm mật khẩu; migration V9 (nếu được cấp) cho `deviceLabel`, chấp thuận điều khoản, bộ đếm giới hạn; sửa OpenAPI theo câu trả lời Q1. PR riêng, merge trước code.
- [ ] **PR 2, theo `implement-slice`:** `registerAccount`, `verifyEmail`, `resendVerification`. Gửi email qua outbox, không gửi trong giao dịch (INV-18).
- [ ] **PR 3, theo `implement-slice`:** `SessionResolver` trong `identity` tra `auth_sessions` và `auth_epoch` mỗi yêu cầu (ADR-0004, ADR-0010; điều kiện theo hàm `valid_session` của V5), bean `PublicEndpoints` cho các thao tác công khai, `loginAccount`, `logoutAccount`, `listOwnSessions`, `revokeOwnSession`.

### File dự kiến đụng
- `docs/decisions/adr/0008-bam-mat-khau.md` (mới)
- `backend/src/main/resources/db/migration/V9__*.sql` (mới, chờ cấp số)
- `contracts/openapi.yaml` và `packages/` sinh lại nếu Q1 đổi hợp đồng
- `backend/src/main/java/com/tutorbridge/identity/{api,internal,web}/`
- Không đụng `platform/web`: bộ lọc đã có ở nền (ADR-0010)
- `backend/build.gradle*` nếu thêm thư viện băm mật khẩu
- Test trong `backend/src/test/java/com/tutorbridge/identity/`

### Ca kiểm thử dự kiến
- `AC_UC01_01`: email trùng (khác hoa thường, khoảng trắng) không tạo tài khoản thứ hai và phản hồi giống email mới; `role=ADMIN` hoặc `STUDENT` trong payload bị từ chối.
- `AC_UC01_02`: mã xác minh dùng lần hai bị từ chối; mã quá 30 phút bị từ chối; mã khác mục đích bị từ chối; hai yêu cầu xác minh đồng thời chỉ một lần thành công.
- `AC_UC01_03`: gọi bằng phiên đã đăng xuất, đã thu hồi, tài khoản `LOCKED`, `auth_epoch` đã tăng đều trả 401; hết hạn 24 giờ không hoạt động và 7 ngày tuyệt đối.
- `AC_UC01_04`: log không chứa mật khẩu, mã xác minh, token (bắt log trong test).
- Đăng nhập sai 5 lần trong 15 phút thì bị trì hoãn, đếm riêng theo tài khoản và theo nguồn; trạng thái giữ trong DB nên chạy qua hai bản ứng dụng vẫn đúng.
- Quyền (INV-01, INV-02, INV-06): `revokeOwnSession` với `sessionId` của người khác không thu hồi được và không lộ phiên tồn tại; `listOwnSessions` không trả `token_digest`.
- Phản hồi có `Cache-Control: private, no-store`.

## Trạng thái hiện tại
Đã chạy `start-task`, viết bản nháp ADR-0008 (thuật toán đã chọn, tham số TBD). Ngày 06/11/2026 rebase lên `dev`, cập nhật theo nền backend. Chưa viết code: PR 1 chờ Q1, Q2; PR 2 chờ Q4–Q8; PR 3 chờ Q5, Q7, Q11.

## Lệnh kiểm tra
```bash
git switch feature/TB-8-account-sessions
git log --oneline dev..HEAD   # mong đợi: chỉ có commit của TB-8
cd backend && ./gradlew test  # mong đợi: toàn bộ test đạt
```

## Kết quả chạy gần nhất
Chưa chạy test trên nhánh này vì chưa có thay đổi code.

## Quyết định đã chọn
- Huy, 04/11/2026, Q3: băm mật khẩu bằng Argon2id. Ghi trong ADR-0008. Tham số chưa đo.
- Huy, 04/11/2026, Q9: `oasdiff` trên CI chặn đổi hợp đồng phá vỡ (đã merge ở #40). Phần kiểm phản hồi khớp hợp đồng trong test: nền dùng `Contract.matches()` trên `com.networknt:json-schema-validator` thay cho `swagger-request-validator` vì hợp đồng là OpenAPI 3.1 (agent chọn ở B3, Đính duyệt 06/11/2026). Test HTTP của TB-8 dùng `Contract.matches()`.
- Huy, 04/11/2026: push nhánh và mở Draft PR vào `dev` để Đính xem câu hỏi.
- Huy, 06/11/2026, Q10: đồng ý ADR-0010 mục 1. TB-8 không dựng bộ lọc riêng, chỉ viết `SessionResolver` trong `identity`; giữ kế hoạch 3 PR.
- Huy, 04/11/2026: bỏ qua OP-05 (cổng phỏng vấn) để làm TB-8. Đính đóng OP-05, OP-06 bằng chấp nhận rủi ro ngày 06/11/2026 (sổ quyết định ghi "Huy chưa xác nhận").

## Câu hỏi còn mở
| # | Câu hỏi | Ai trả lời |
|---|---|---|
| Q1 | Lệnh xác thực công khai có cần `Idempotency-Key` không? Nếu có thì receipt lưu thế nào khi chưa có actor: đổi `actor_id` cho phép null, hay tách bảng? `logoutAccount`, `revokeOwnSession` có actor: có thêm header vào OpenAPI không? Thiếu khóa trả 422 (code hiện tại) hay 428 (`10-hop-dong-api.md` ghi "thiếu điều kiện trả 428")? | Đính, Huy |
| Q2 | Có cấp V9 cho TB-8 không? Nội dung đề xuất: `auth_sessions.device_label`, bảng chấp thuận điều khoản (tài khoản, phiên bản, thời điểm), bảng đếm lần thử theo tài khoản và theo nguồn | Đính |
| Q3 | Đã chọn Argon2id. Còn lại: tham số `m`, `t`, `p` đo trên máy chạy; có dùng pepper không; có chuẩn hóa Unicode mật khẩu không (xem ADR-0008) | Tham số: Huy. Pepper, Unicode: Đính, Huy |
| Q4 | Email: chuẩn hóa thế nào (cắt khoảng trắng, chữ thường)? `email_digest` dùng HMAC với khóa bí mật hay hash thường? Khóa mã hóa `email_ciphertext` lấy từ đâu? Lát 1 gửi email qua đâu (đề xuất: hộp thư local như Mailpit trong `infra/compose.yaml`, không dùng nhà cung cấp thật)? | Đính, Huy |
| Q5 | Tài khoản `UNVERIFIED` có đăng nhập được không? (OpenAPI nói không, F01 ngụ ý có) | Đính, Huy |
| Q6 | Ngưỡng giới hạn cho `registerAccount` và `resendVerification` | Đính, Huy |
| Q7 | Đăng nhập của ADMIN (trả `AdminMfaChallenge`) thuộc TB-8 hay TB-13? Đề xuất: TB-8 chặn ADMIN đăng nhập, TB-13 mở | Đính |
| Q8 | Danh sách `termsVersion` hợp lệ lấy từ đâu (cấu hình)? Hiện chưa có văn bản điều khoản nào | Đính, Huy |
| Q11 | `SessionResolver`: digest token tính thế nào (SHA-256 hay HMAC có khóa, mã hóa hex hay base64url)? Cập nhật `last_seen_at` cho hạn 24 giờ không hoạt động mỗi yêu cầu hay theo chu kỳ (mỗi yêu cầu thêm một lần ghi)? Liên quan Q4 | Đính, Huy |

## Bước kế tiếp
Khi có câu trả lời cho Q1, Q2: chạy playbook `contract-change` cho PR 1 (ADR-0008, V9, OpenAPI). Q4 đến Q8 cần trước PR 2 và PR 3.
