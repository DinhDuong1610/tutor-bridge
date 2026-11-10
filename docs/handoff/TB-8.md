# TB-8 Đăng ký, xác minh, đăng nhập, phiên; ADR băm mật khẩu

- **Chủ task:** Huy
- **Nhánh:** PR 1 `feature/TB-8-auth-contract` (#92, đã merge vào `dev` ngày 11/11/2026, commit `c16d51f`); PR 2 và PR 3 trên `feature/TB-8-account-sessions` (Draft PR #39), đã merge `dev` vào sau khi #92 merge; PR 2b `feature/TB-8-verification-mail` (Draft PR #108), tách từ #39 tại `a697fff`, nhánh đích `feature/TB-8-account-sessions`
- **Agent đang làm:** Claude (Claude Code). PR 1 đã merge; PR 2 đã push lên #39; PR 2b xong (`913e510`, `80b90a1`)
- **Cập nhật lần cuối:** 11/11/2026, PR 2b (worker gửi email xác minh qua Mailpit) xong, chạy trọn luồng trên máy
- **Mã liên quan:** FR-01, FR-02; UC01; AC-UC01-01, AC-UC01-02, AC-UC01-03, AC-UC01-04; INV-01, INV-03, INV-06, INV-18, INV-20, INV-21, INV-63, INV-80, INV-83; ADR-0004

## Mục tiêu
Phụ huynh và gia sư tự đăng ký, xác minh email, đăng nhập bằng phiên opaque, xem và thu hồi phiên của mình, đăng xuất. Có ADR chọn cách băm mật khẩu. Bảy thao tác: `registerAccount`, `verifyEmail`, `resendVerification`, `loginAccount`, `logoutAccount`, `listOwnSessions`, `revokeOwnSession`.

## Kết quả start-task
- Issue #8 đủ chủ task, mã UC/FR, tiêu chí xong.
- Trong phạm vi Lát 1: FR-01, FR-02 và cả bảy thao tác có trong `docs/context/r0a-scope.md`.
- Ngày 06/11/2026 nền backend (#64, ADR-0010) đã có Spring Security, bộ lọc bearer và cookie, `Actor`, giao diện `SessionResolver`, `PublicEndpoints`, tầng web dùng chung và `HttpTest`. TB-8 không dựng bộ lọc riêng.
- Ước tính vượt xa 400 dòng logic, nên đề xuất chia nhỏ (xem Kế hoạch).

## Chỗ hợp đồng, schema và tài liệu chưa khớp
Đã giải quyết cả bốn mục bằng #92 (CR-014, V9, mô tả OpenAPI) và câu trả lời của Đính ngày 08/11/2026 (Q1, Q5, Q6). Giữ lại để biết lý do.
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
- [x] **PR 1, theo `contract-change`** (#92): CR-014; ADR-0008 hoàn chỉnh và `bcprov-jdk18on` 1.86; V9 (`auth_sessions.device_label`, `terms_acceptances`, `auth_login_failures` cửa sổ trượt cho `LOGIN`, `auth_rate_counters` cửa sổ cố định cho ba lệnh còn lại); OpenAPI ghi mã lỗi và "No Idempotency-Key (CR-014)" trong mô tả, sinh lại client; `IdempotencyKey` trả 400 khi thiếu khóa; INV-20, `module-map`, `m0-ban-giao`. PR riêng, merge trước code.
- [x] **PR 2, theo `implement-slice`:** `registerAccount`, `verifyEmail`, `resendVerification`. Sự kiện email vào outbox, không gửi trong giao dịch (INV-18). Commit `dda25c7` (`TooManyRequests` trong `platform`), `01f57bf` (`identity`).
- [x] **PR 2b:** gửi email thật: Mailpit trong `infra/compose.yaml`, worker đọc `identity.EmailVerificationRequested` qua `OutboxQueue`, giải mã mã bằng `AccountSecrets.decryptLinkToken`, giải mã email từ `accounts.email_ciphertext`, gửi SMTP (Huy, 11/11/2026: TB-8 làm, PR riêng).
- [ ] **PR 3, theo `implement-slice`:** `SessionResolver` trong `identity` tra `auth_sessions` và `auth_epoch` mỗi yêu cầu (ADR-0004, ADR-0010; điều kiện theo hàm `valid_session` của V5), bean `PublicEndpoints` cho các thao tác công khai, `loginAccount`, `logoutAccount`, `listOwnSessions`, `revokeOwnSession`.

### File dự kiến đụng
- Đã xong ở #92: ADR-0008, `V9__account_sessions.sql`, `contracts/openapi.yaml`, client sinh lại
- `backend/src/main/java/com/tutorbridge/identity/{api,internal,web}/`
- Không đụng `platform/web`: bộ lọc đã có ở nền (ADR-0010)
- Test trong `backend/src/test/java/com/tutorbridge/identity/`

### Ca kiểm thử dự kiến
- `AC_UC01_01`: email trùng (khác hoa thường, khoảng trắng) không tạo tài khoản thứ hai và phản hồi giống email mới; `role=ADMIN` hoặc `STUDENT` trong payload bị từ chối.
- `AC_UC01_02`: mã xác minh dùng lần hai bị từ chối; mã quá 30 phút bị từ chối; mã khác mục đích bị từ chối; hai yêu cầu xác minh đồng thời chỉ một lần thành công.
- `AC_UC01_03`: gọi bằng phiên đã đăng xuất, đã thu hồi, tài khoản `LOCKED`, `auth_epoch` đã tăng đều trả 401; hết hạn 24 giờ không hoạt động và 7 ngày tuyệt đối.
- `AC_UC01_04`: log không chứa mật khẩu, mã xác minh, token (bắt log trong test).
- Đăng nhập sai 5 lần trong 15 phút thì bị trì hoãn, đếm riêng theo tài khoản và theo nguồn; trạng thái giữ trong DB nên chạy qua hai bản ứng dụng vẫn đúng.
- Quyền (INV-01, INV-02, INV-06): `revokeOwnSession` với `sessionId` của người khác không thu hồi được và không lộ phiên tồn tại; `listOwnSessions` không trả `token_digest`.
- Phản hồi có `Cache-Control: private, no-store`.
- Huy, 10/11/2026 (Q13, Q14): email không tồn tại và sai mật khẩu nhận phản hồi tương đương (401 `AUTHENTICATION_FAILED`, cùng cấu trúc và thông báo); vượt giới hạn nhận 429 `RATE_LIMITED` kèm `Retry-After`; ADMIN bị từ chối qua luồng mobile không được cấp phiên hay token. Ca "đăng nhập quản trị trên web vẫn hoạt động với MFA" thuộc TB-13.

## Trạng thái hiện tại
#92 (PR 1) đã merge vào `dev` ngày 11/11/2026 (`c16d51f`) theo ngoại lệ OP-16. CR-013 vẫn chưa có trên `dev` (Q15). `dev` đã được merge vào nhánh này, không force-push (lệnh `reset --hard` kèm force-push bị chặn trên máy Huy).

PR 2 xong trên máy, chưa push:
- `identity/internal`: `AccountRegistration` (service), `AccountRepository`, `AccountSecrets` (chuẩn hóa email, HMAC, AES-GCM `v1:`, mã liên kết 256 bit), `PasswordHasher` (`argon2id-v1`, NFKC), `AuthRateLimiter` (cửa sổ cố định trên `auth_rate_counters`, giao dịch riêng), `IdentityProperties`, `IdentityConfig`. `identity/web/RegistrationController` kèm bean `PublicEndpoints`.
- `platform/api/TooManyRequests` và `ProblemHandler` ghi `Retry-After` (giây, làm tròn lên, ≥ 1). Phần này thuộc nền của Đính; nhờ Đính xem.
- Cấu hình `tutorbridge.identity.*` trong `application.yaml`: ba khóa từ `TB_EMAIL_DIGEST_KEY`, `TB_EMAIL_ENCRYPTION_KEY`, `TB_LINK_TOKEN_KEY` (base64 32 byte); `terms-versions` từ `TB_TERMS_VERSIONS`, profile local mặc định `ban-thu-chua-hieu-luc`; ngưỡng Q6 (đăng ký 5/giờ theo nguồn; gửi lại 3/giờ theo email, 10/giờ theo nguồn). `.env.example` thêm tên biến.
- **Thay đổi cho máy local:** backend không khởi động khi thiếu ba khóa. Ai chạy `bootRun` phải thêm ba dòng vào `.env` (`openssl rand -base64 32` cho mỗi khóa).
- Test: `IntegrationTest` cấp khóa ngẫu nhiên mỗi lần chạy; `AcceptOfferConcurrencyTest` truyền cùng khóa cho bản ứng dụng thứ hai.

PR 2b (`feature/TB-8-verification-mail`):
- `platform/api/OutboxHandler` (một handler cho một `event_type`, chạy ngoài giao dịch), `platform/internal/OutboxDispatcher` (lấy lô bằng `OutboxQueue.claim`, thành công thì `markDelivered`, lỗi thì thử lại sau 30 s, 60 s, … tối đa 1 giờ; không có handler thì hoãn 10 phút), `platform/internal/WorkerRole` (bật bằng `tutorbridge.worker.enabled`, quét mỗi `poll-interval`, mặc định 2 s). Huy chọn đặt chung ở `platform` (11/11/2026); nhờ Đính rà vì là nền backend.
- `identity/internal/VerificationMailSender`: chỉ có trên vai trò worker; giải mã mã theo `challengeId`, giải mã email từ `accounts.email_ciphertext`, gửi `tutorbridge://verify-email?token=…`. Không gửi khi mã đã dùng, đã hết hạn hoặc tài khoản không còn `UNVERIFIED`. Worker thiếu SMTP, `tutorbridge.identity.mail.from` hoặc `link-base` thì không khởi động.
- `spring-boot-starter-mail` (bản theo BOM Spring Boot 4.1.2) và `axllent/mailpit:v1.32.0` (phát hành 22/10/2026) trong `infra/compose.yaml`: Huy duyệt cho riêng TB-8 ngày 11/11/2026, Đính rà khi review (CR-012).
- Profile local: một tiến trình chạy cả hai vai trò, SMTP `127.0.0.1:1025`, xem thư ở http://localhost:8025. Bản triển khai: `TB_WORKER_ENABLED`, `TB_MAIL_FROM`, `TB_LINK_BASE`, `SPRING_MAIL_*` (ghi trong `.env.example`).

## Lệnh kiểm tra
```bash
git switch feature/TB-8-account-sessions
git diff --stat origin/dev...HEAD    # mong đợi: chỉ có thay đổi của TB-8 PR 2, PR 3
cd backend && ./gradlew test
cd .. && sh scripts/ci/client-sync.sh && npm run lint && npm run typecheck && npm test
```
Máy Windows của Huy: Gradle báo lỗi chứng chỉ TLS (PKIX) khi tải thư viện mới, `curl` thì tải được. Thêm `-Djavax.net.ssl.trustStoreType=Windows-ROOT` vào lệnh `./gradlew`. CI Linux không cần.

## Kết quả chạy gần nhất
11/11/2026, PR 2b, máy Windows của Huy, Docker 29.6.2:
- `./gradlew test`: 146/146 đạt (thêm 4 `OutboxDispatcherTest`, 4 `VerificationMailTest` chạy Mailpit thật qua Testcontainers).
- Thử làm hỏng: gửi cả mã đã hết hạn thì 1 ca đỏ; sai đường dẫn liên kết thì 2 ca đỏ; handler lỗi mà vẫn đánh dấu đã gửi thì 1 ca đỏ; khôi phục thì đạt.
- Chạy thật: `docker compose ... up -d --wait` (db, mail healthy), `bootRun` profile local với ba khóa sinh ngẫu nhiên chỉ cho tiến trình đó. `POST /api/v1/auth/register` trả 202; Mailpit nhận "Xác minh email TutorBridge" có liên kết `tutorbridge://verify-email?token=…` (43 ký tự); `POST /api/v1/auth/verify` trả 204, lần hai 409 `LINK_USED`. Log backend không có mã, không có email.
- Chưa mở liên kết trên điện thoại thật.

11/11/2026, PR 2, máy Windows của Huy, Docker 29.6.2:
- `./gradlew test`: 138/138 đạt (115 cũ + 18 `AccountRegistrationHttpTest` + 5 `AccountSecretsTest`). Lần đầu đỏ vì Docker Desktop chưa bật; lần hai đỏ 1 ca vì email có khoảng trắng bị `@Email` trả 422 (đúng hợp đồng `format: email`), sửa ca test thành khác hoa thường, phần cắt khoảng trắng và NFC kiểm ở `AccountSecretsTest`; lần ba `AcceptOfferConcurrencyTest` không khởi động được bản thứ hai vì thiếu khóa, đã truyền khóa.
- Thử làm hỏng: cho tiêu thụ mã hai lần thì 3 ca đỏ; bỏ header `Retry-After` thì 2 ca đỏ (`Contract.matches()` không bắt vì header không bắt buộc, ca riêng bắt); gửi lại cho mọi trạng thái tài khoản thì 1 ca đỏ; khôi phục thì đạt.
- `skills-sync`, `immutable-history`, `client-sync`: ok. `no-handoff` báo file này (đúng, xóa trước khi Ready).
- Chưa chạy `bootRun` với `.env` thật, chưa gửi email thật (PR 2b).

11/11/2026, #92 trước khi merge: CI trên commit `13f43c1` đạt cả ba job. Commit đó chỉ đổi tài liệu so với `ad7ea13`.

10/11/2026, #92 sau khi sửa theo review: `./gradlew test` 115/115 đạt; `oasdiff` không báo thay đổi phá vỡ; `client-sync`, lint, typecheck, `npm test` đạt.

## Quyết định đã chọn
- Huy, 04/11/2026, Q3: băm mật khẩu bằng Argon2id. Ghi trong ADR-0008. Tham số chưa đo.
- Huy, 04/11/2026, Q9: `oasdiff` trên CI chặn đổi hợp đồng phá vỡ (đã merge ở #40). Phần kiểm phản hồi khớp hợp đồng trong test: nền dùng `Contract.matches()` trên `com.networknt:json-schema-validator` thay cho `swagger-request-validator` vì hợp đồng là OpenAPI 3.1 (agent chọn ở B3, Đính duyệt 06/11/2026). Test HTTP của TB-8 dùng `Contract.matches()`.
- Huy, 04/11/2026: push nhánh và mở Draft PR vào `dev` để Đính xem câu hỏi.
- Huy, 06/11/2026, Q10: đồng ý ADR-0010 mục 1. TB-8 không dựng bộ lọc riêng, chỉ viết `SessionResolver` trong `identity`; giữ kế hoạch 3 PR.
- Huy, 04/11/2026: bỏ qua OP-05 (cổng phỏng vấn) để làm TB-8. Đính đóng OP-05, OP-06 bằng chấp nhận rủi ro ngày 06/11/2026 (sổ quyết định ghi "Huy chưa xác nhận").

- Huy, 07/11/2026: Huy chủ trì ADR-0008, kể cả thêm `bcprov`; Q12 không cần Đính duyệt. Ghi chú: CR-012 vẫn ghi Đính chủ trì kiến trúc backend Spring và `conventions.md` ghi thư viện mới qua Đính; Đính chưa có ý kiến về ngoại lệ này.

- Huy, 07/11/2026, Q3 phần tham số: chính sách `argon2id-v1` = mức tối thiểu OWASP (`m=19456` KiB, `t=2`, `p=1`), đo lại trên máy chạy trước thí điểm. Ghi trong ADR-0008.
- Huy, 07/11/2026, yêu cầu cho TB-09 từ TB-10 (Q10 của TB-10): `changePassword` dùng `currentPassword` làm bước tái xác thực, không dùng `reauthToken`; sai mật khẩu hiện tại trả 422 kèm lỗi trường `currentPassword`, không trả 401 (401 làm app kết thúc phiên).

- Đính, 08/11/2026, trên #39, đồng ý ý kiến của Huy (07/11/2026). Các câu Q1, Q3–Q6, Q8, Q11 đóng:
  - Q1: lệnh xác thực công khai không dùng `Idempotency-Key`; `logoutAccount`, `revokeOwnSession` không thêm header; thiếu khóa ở lệnh có yêu cầu trả 400 `IDEMPOTENCY_KEY_REQUIRED`, 428 chỉ cho `If-Match`. Ghi là **CR-014** (CR-013 đã dùng cho UX-UI, nhánh TB-87).
  - Q4: email cắt khoảng trắng, chữ thường, NFC. `email_digest` = HMAC-SHA256, `email_ciphertext` = AES-GCM. **Hai khóa riêng**, cùng lấy từ biến môi trường; bản mã có tiền tố phiên bản khóa (ví dụ `v1:`) để thay khóa không cần migration. `.env.example` chỉ có tên biến. Lát 1 gửi qua Mailpit trong `infra/compose.yaml`.
  - Q5: `UNVERIFIED` không đăng nhập được; chỉ trả 403 `ACCOUNT_UNVERIFIED` khi mật khẩu đúng.
  - Q6: số đề xuất là giá trị tạm, đặt trong cấu hình, ghi rõ "chưa có số đo": gửi lại xác minh và khôi phục 3 lần/giờ theo tài khoản, 10 lần/giờ theo nguồn; đăng ký 5 lần/giờ theo nguồn.
  - Q8: `termsVersion` hợp lệ trong cấu hình backend; app lấy từ `EXPO_PUBLIC_TERMS_VERSION`. Đính lo văn bản điều khoản; trước khi có thì dùng bản ghi rõ "bản thử, chưa có hiệu lực".
  - Q11: token phiên 256 bit base64url, digest SHA-256; `last_seen_at` chỉ ghi khi giá trị cũ quá 5 phút (chấp nhận hạn 24 giờ không hoạt động lệch tối đa 5 phút).
  - Mã lỗi: `ACCOUNT_UNVERIFIED` (403), `ACCOUNT_LOCKED` (403), `LOGIN_DELAYED` (429, kèm `Retry-After`), `LINK_EXPIRED` (400), `LINK_USED` (409), `LINK_INVALID` (400), `CURRENT_PASSWORD_INVALID` (422). Thêm vào hợp đồng trong PR 1.
  - ADR-0008: không dùng pepper ở Lát 1, chuẩn hóa NFKC trước khi băm.
- Đính, 08/11/2026, quyết:
  - Q2: cấp **V9** cho TB-8, một migration trong PR 1, merge trước code.
  - Q7: TB-8 chặn ADMIN đăng nhập; TB-13 mở luồng `AdminMfaChallenge`.
  - Huy chủ trì ADR-0008 và thêm `bcprov` là ngoại lệ cho riêng việc này; CR-012 giữ nguyên. Đính duyệt nội dung ADR và phiên bản `bcprov` khi rà PR 1.
  - Đồng ý chia #8 thành 3 PR như Kế hoạch.
- Huy, 10/11/2026, Q13 (cần Đính đồng ý vì là câu hai người cùng chốt):
  - 401 `AUTHENTICATION_FAILED` khi email không tồn tại hoặc sai mật khẩu. Hai trường hợp cùng cấu trúc phản hồi và thông báo "Email hoặc mật khẩu không đúng.", không trả chi tiết giúp đoán tài khoản có tồn tại.
  - 429 `RATE_LIMITED` khi vượt giới hạn, kèm `Retry-After`; thông báo "Bạn đã thử quá nhiều lần. Vui lòng thử lại sau."
  - Backend thực thi giới hạn; client hiển thị thông báo và thời gian chờ theo phản hồi, không tự đặt thời gian khóa. Thêm hai mã vào danh mục lỗi và hợp đồng trước phần phụ thuộc: đã thêm vào #92 (mô tả của `loginAccount`, `registerAccount`, `resendVerification`, `requestRecovery`) và `m0-ban-giao.md`.
  - Agent ghi chú: `loginAccount` vẫn dùng 429 `LOGIN_DELAYED` (đã chốt 08/11) cho trì hoãn sau 5 lần sai; `RATE_LIMITED` dùng cho ba lệnh còn lại.
- Huy, 10/11/2026, Q14 (Q7 là quyết định của Đính, nên cần Đính đồng ý):
  - ADMIN đăng nhập qua luồng mobile nhận 401 `AUTHENTICATION_FAILED`, cùng cấu trúc và thông báo như sai mật khẩu; không có thông báo riêng lộ vai trò quản trị; không cấp phiên hay token.
  - Chỉ áp dụng cho mobile; đăng nhập quản trị trên web vẫn xác thực và MFA theo thiết kế.
  - Điều kiện: nếu mobile và web dùng chung endpoint thì phải chốt cách backend phân biệt hai luồng trước khi áp dụng; không chặn ADMIN trên toàn bộ endpoint, không dựa vào việc client tự khai báo vai trò. Agent kiểm hợp đồng: hai bên **dùng chung** `/auth/login` (xem Q17).
- Huy, 10/11/2026, Q17: chọn phương án (b), tách endpoint đăng nhập quản trị riêng cho web. Cần Đính đồng ý (câu hai người cùng chốt). Hệ quả agent ghi lại:
  - `/auth/login` luôn từ chối ADMIN bằng 401 `AUTHENTICATION_FAILED`, cùng phản hồi như sai mật khẩu, không cấp phiên hay token. Khớp cả Q7 của Đính ("TB-8 chặn ADMIN") lẫn điều kiện của Huy ở Q14 (không chặn ADMIN trên toàn bộ đăng nhập, vì ADMIN có endpoint riêng). Đã ghi vào mô tả `loginAccount` trong #92.
  - Endpoint quản trị mới và việc bỏ `AdminMfaChallenge` khỏi `LoginResult` thuộc TB-13 (Đính), làm bằng PR `contract-change` riêng. #92 không thêm endpoint, không đổi schema.
  - Ca kiểm thử của PR 3: ADMIN với mật khẩu đúng nhận phản hồi tương đương sai mật khẩu, không có dòng mới trong `auth_sessions`.
- Đính, 10/11/2026, trên #39 (đóng Q10, Q13, Q14, Q16, Q17):
  - Q13: đồng ý 401 `AUTHENTICATION_FAILED` và 429 `RATE_LIMITED` kèm `Retry-After` cho `registerAccount`, `resendVerification`, `requestRecovery`; `loginAccount` giữ 429 `LOGIN_DELAYED`.
  - Q14, Q17: đồng ý phương án (b). `/auth/login` từ chối ADMIN bằng đúng 401 `AUTHENTICATION_FAILED`, không cấp phiên. Đính thêm endpoint đăng nhập quản trị và bỏ `AdminMfaChallenge` khỏi `LoginResult` trong TB-13 bằng PR `contract-change` riêng.
  - Q10: `currentPassword` là bước tái xác thực của `changePassword`, không dùng `reauthToken`; sai mật khẩu hiện tại trả 422 `CURRENT_PASSWORD_INVALID`. Ghi rõ trong `description` (đã làm).
  - Q16: `changePassword` vào ngoại lệ của CR-014, không thêm `Idempotency-Key`; gửi lại sau khi đổi thành công thì mọi phiên đã bị thu hồi, máy chủ trả 401 (đã cập nhật CR-014, INV-20).
  - Bộ đếm: `LOGIN` dùng cửa sổ trượt (năm lần sai trong 15 phút gần nhất, đúng UC01); `REGISTER`, `RESEND_VERIFICATION`, `RECOVERY` giữ cửa sổ cố định vì ngưỡng còn tạm (đã làm: bảng `auth_login_failures`).
  - Q15: Đính đưa CR-013 lên GitHub trước khi #92 merge.
- Đính, review #92 ngày 09/11/2026, góp ý không chặn:
  - Khai báo `Retry-After` cho phản hồi 429. Agent kiểm: `components.responses.Problem` đã khai báo header `Retry-After` (số nguyên ≥ 1), và mọi thao tác trả 429 qua `$ref` này, nên hợp đồng đã có; không sửa thêm.
  - `device_label` rỗng: màn hình phiên hiện "Thiết bị không rõ" (làm ở TB-10, PR sửa theo review).
  - PR 2: ghi `terms_acceptances.accepted_at` từ `Clock`, không dựa vào `DEFAULT now()`.
- Agent chọn trong PR 1, cần người rà xem:
  - Mã lỗi ghi trong `description` của từng thao tác vì `Problem.code` là chuỗi tự do; thêm `enum` sẽ là thay đổi phá vỡ. Cách ghi theo mẫu `Missing -> 428` sẵn có của `If-Match`.
  - Khóa sai độ dài vẫn trả 422 `IDEMPOTENCY_KEY_INVALID`; CR-014 chỉ đổi trường hợp thiếu khóa.
  - `device_label` là `NOT NULL DEFAULT ''` để code chạy trên V8 vẫn ghi phiên được; phiên cũ có nhãn rỗng.
  - `auth_login_failures`: một dòng cho mỗi lần sai (phạm vi, digest, thời điểm), chỉ mục (phạm vi, digest, thời điểm) để đếm 15 phút gần nhất và chỉ mục thời điểm để dọn theo lô. `auth_rate_counters` (ba lệnh còn lại) đếm theo cửa sổ cố định, một dòng cho mỗi (thao tác, phạm vi, digest). `subject_digest` là HMAC hex của email hoặc địa chỉ nguồn, không lưu giá trị gốc.
  - `bcprov-jdk18on` 1.86 khai báo `runtimeOnly`.

- Huy, 11/11/2026, PR 2: mã liên kết chỉ vào outbox dưới dạng mã hóa AES-GCM bằng khóa riêng, gắn với `challengeId`; bên gửi giải mã lúc gửi. Payload không có email (bên gửi đọc `email_ciphertext`).
- Huy, 11/11/2026: gửi email thật (Mailpit, worker, SMTP) là PR riêng của TB-8 (PR 2b).
- Agent chọn trong PR 2, cần người rà xem:
  - Nguồn gửi là `request.getRemoteAddr()`. Sau proxy cần cấu hình forwarded headers khi triển khai (môi trường chung, Đính).
  - Email đã có tài khoản: không gửi gì cho chủ tài khoản, chỉ trả cùng 202. Vẫn băm mật khẩu để thời gian phản hồi gần nhau.
  - Gửi lại không vô hiệu mã cũ còn hạn: mỗi mã vẫn chỉ dùng một lần, hạn 30 phút.
  - Yêu cầu sai định dạng (422) không được đếm vào giới hạn; mọi yêu cầu hợp lệ đều được đếm, kể cả khi bị từ chối 429.
  - Tài khoản `LOCKED`/`DELETED` mở mã xác minh: trả `LINK_INVALID`, không kích hoạt lại, mã không bị tiêu thụ.
  - Email có khoảng trắng ở đầu, cuối: 422 theo `format: email`; máy chủ vẫn cắt khoảng trắng khi chuẩn hóa.
  - Câu báo: đăng ký "Nếu email hợp lệ, chúng tôi đã gửi liên kết xác minh. Vui lòng kiểm tra hộp thư."; gửi lại "Nếu email cần xác minh, chúng tôi đã gửi liên kết mới. Vui lòng kiểm tra hộp thư."; `LINK_INVALID` "Liên kết không hợp lệ."; `LINK_EXPIRED` "Liên kết đã hết hạn. Vui lòng yêu cầu liên kết mới."; `LINK_USED` "Liên kết này đã được sử dụng.". Đối chiếu Figma khi Đính duyệt F01 (#94).

## Câu hỏi còn mở
Q1–Q17 đã đóng, trừ:

| # | Câu hỏi | Ai trả lời | Cần trước |
|---|---|---|---|
| Q15 | CR-013 (UX-UI) chưa có trên `dev`. #92 đã merge với số CR-014 theo OP-16; Đính đưa CR-013 lên sau | Đính | Không chặn TB-8 |

## Bước kế tiếp
1. PR 2b (Draft #108, nhánh đích `feature/TB-8-account-sessions`): Huy viết phần tác giả; Đính rà `platform` (worker, `OutboxHandler`), thư viện mail và Mailpit.
2. PR 3: `SessionResolver`, `PublicEndpoints`, `loginAccount` (ADMIN trả 401 `AUTHENTICATION_FAILED`), `logoutAccount`, `listOwnSessions`, `revokeOwnSession`. Đếm lần sai đăng nhập trên `auth_login_failures` theo 15 phút gần nhất; `LOGIN_DELAYED` dùng `TooManyRequests`.
3. Huy viết phần tác giả cho PR 2 trong mô tả #39.
4. Sau khi #39 merge: chạy chuỗi TB-10 (#78 → #99) với API thật, mở liên kết từ Mailpit trên điện thoại.
