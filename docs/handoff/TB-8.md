# TB-8 Đăng ký, xác minh, đăng nhập, phiên; ADR băm mật khẩu

- **Chủ task:** Huy
- **Nhánh:** PR 1 `feature/TB-8-auth-contract` (Draft PR #92 vào `dev`); PR 2 và PR 3 trên `feature/TB-8-account-sessions` (Draft PR #39), rebase lên `dev` sau khi PR 1 merge
- **Agent đang làm:** Claude (Claude Code). PR 1 xong trên máy, chưa có code nghiệp vụ
- **Cập nhật lần cuối:** 09/11/2026, PR 1 theo `contract-change` (CR-014, V9, ADR-0008, OpenAPI)
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
- [x] **PR 1, theo `contract-change`** (#92): CR-014; ADR-0008 hoàn chỉnh và `bcprov-jdk18on` 1.86; V9 (`auth_sessions.device_label`, `terms_acceptances`, `auth_rate_counters`); OpenAPI ghi mã lỗi và "No Idempotency-Key (CR-014)" trong mô tả, sinh lại client; `IdempotencyKey` trả 400 khi thiếu khóa; INV-20, `module-map`, `m0-ban-giao`. PR riêng, merge trước code.
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
Đính trả lời toàn bộ câu hỏi ngày 08/11/2026 trên #39. PR 1 mở Draft PR #92 ngày 10/11/2026, chờ Huy viết phần tác giả và cả hai người duyệt. Q13–Q16 đã gửi Đính trên #39. Chưa có code nghiệp vụ; PR 2 bắt đầu sau khi PR 1 merge (cả hai người duyệt).

## Lệnh kiểm tra
```bash
git switch feature/TB-8-auth-contract
git log --oneline origin/dev..HEAD   # mong đợi: chỉ có commit của TB-8
cd backend && ./gradlew test         # mong đợi: 113 test đạt
cd .. && sh scripts/ci/client-sync.sh && npm run lint && npm run typecheck && npm test
```
Máy Windows của Huy: Gradle báo lỗi chứng chỉ TLS (PKIX) khi tải thư viện mới, `curl` thì tải được. Thêm `-Djavax.net.ssl.trustStoreType=Windows-ROOT` vào lệnh `./gradlew`. CI Linux không cần.

## Kết quả chạy gần nhất
09/11/2026, PR 1, máy Windows của Huy, Docker 29.6.2:
- `./gradlew test`: 113/113 đạt (106 cũ, thêm 5 test V9 trong `AccountSessionsMigrationTest`, 1 trong `CommandReceiptsTest`, 1 trong `WebFoundationTest`). Có test nâng cấp từ V8 có dữ liệu lên V9.
- Thử làm hỏng: bỏ `DEFAULT ''` của `device_label` thì test nâng cấp đỏ; bỏ `CHECK` dạng digest thì `counters_reject_raw_subjects_and_unknown_actions` đỏ; trả 422 khi thiếu khóa thì 2 test CR-014 đỏ; khôi phục thì đạt.
- `oasdiff breaking --fail-on ERR` so với `origin/dev`: "No breaking changes". Chạy bằng `docker run` với đường dẫn Windows, vì `scripts/ci/contract-check.sh` không mount được thư mục trên Git Bash (lỗi đã biết trong `m0-ban-giao.md`).
- `client-sync`: `schema.d.ts` sinh lại, chỉ đổi chú thích mô tả. `npm run lint`, `npm run typecheck`: đạt. `npm test`: api-client 3, shared 22, mobile 41, web 27 đạt. `skills-sync`, `immutable-history`: ok.

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
- Agent chọn trong PR 1, cần người rà xem:
  - Mã lỗi ghi trong `description` của từng thao tác vì `Problem.code` là chuỗi tự do; thêm `enum` sẽ là thay đổi phá vỡ. Cách ghi theo mẫu `Missing -> 428` sẵn có của `If-Match`.
  - Khóa sai độ dài vẫn trả 422 `IDEMPOTENCY_KEY_INVALID`; CR-014 chỉ đổi trường hợp thiếu khóa.
  - `device_label` là `NOT NULL DEFAULT ''` để code chạy trên V8 vẫn ghi phiên được; phiên cũ có nhãn rỗng.
  - `auth_rate_counters` đếm theo cửa sổ cố định: một dòng cho mỗi (thao tác, phạm vi, digest), có `blocked_until`. `subject_digest` là HMAC hex của email hoặc địa chỉ nguồn, không lưu giá trị gốc.
  - `bcprov-jdk18on` 1.86 khai báo `runtimeOnly`.

## Tham khảo đo Argon2id (không phải tham số)
07/11/2026, máy Windows của Huy (i7-12700H, RAM 15,6 GB), JDK 21, BouncyCastle `bcprov-jdk18on` 1.80 (`Argon2BytesGenerator`), 15 lần sau 3 lần làm nóng, mật khẩu giả:

| m (MiB) | t | p | Trung vị (ms) | p90 (ms) |
|---|---|---|---|---|
| 19 | 2 | 1 | 26 | 33 |
| 46 | 1 | 1 | 37 | 39 |
| 64 | 2 | 1 | 204 | 219 |
| 64 | 3 | 1 | 309 | 322 |
| 128 | 2 | 1 | 443 | 461 |

Không ghi vào ADR-0008: ADR yêu cầu đo trên máy sẽ chạy, chưa chọn (Đính, trước M6). Số này đo trên JDK 21 và `bcprov` 1.80; dự án chạy JDK 25 và PR 1 chọn `bcprov` 1.86.

## Câu hỏi còn mở
Q1–Q8, Q11 đã đóng ngày 08/11/2026 (xem Quyết định đã chọn). Câu mới, cần trước PR 2 hoặc PR 3:

| # | Câu hỏi | Ai trả lời | Cần trước |
|---|---|---|---|
| Q13 | Mã lỗi cho sai email hoặc mật khẩu khi đăng nhập (401) và cho vượt giới hạn ở `registerAccount`, `resendVerification`, `requestRecovery` (429) chưa có trong danh mục đã chốt. Màn hình TB-10 chỉ dựa vào mã trạng thái. Đề xuất: 401 `AUTHENTICATION_FAILED`, 429 `RATE_LIMITED` kèm `Retry-After` | Đính, Huy | PR 2 |
| Q14 | TB-8 chặn ADMIN đăng nhập (Q7) bằng phản hồi nào? Đề xuất: giống hệt sai mật khẩu (401), để không lộ email là tài khoản quản trị | Đính | PR 3 |
| Q15 | CR-013 (UX-UI) chưa có trên GitHub: `dev` mới đến CR-012 và chưa có nhánh TB-87. Cần Đính push để không trùng số | Đính | Trước khi PR 1 merge |
| Q16 | `changePassword` (TB-09) là lệnh ghi có actor nhưng hợp đồng không khai báo `Idempotency-Key`, khác INV-20. Thêm header bắt buộc là thay đổi phá vỡ | Đính, Huy | TB-09 |

## Bước kế tiếp
1. #92 (PR 1): Huy viết phần tác giả; cả hai người duyệt rồi merge.
2. Trả lời Q13 trước khi bắt đầu PR 2 (`registerAccount`, `verifyEmail`, `resendVerification`, theo `implement-slice`).
3. Sau khi PR 1 merge: rebase `feature/TB-8-account-sessions` lên `dev` (`--force-with-lease`), làm PR 2, rồi PR 3.
