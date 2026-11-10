# TB-9 Khôi phục, đổi mật khẩu, tái xác thực

- **Chủ task:** Huy
- **Nhánh:** PR 1 `feature/TB-9-password-recovery` (Draft PR #110 vào `dev`); PR 2 `feature/TB-9-change-password`, tách từ PR 1 tại `c2cd943`, nhánh đích `feature/TB-9-password-recovery`
- **Agent đang làm:** Claude (Claude Code)
- **Cập nhật lần cuối:** 11/11/2026, PR 2 xong (`5e9c67c`, `fcfd339`), chạy thật cả TB-9
- **Mã liên quan:** UC01, FR-39; AC-UC01-02, AC-UC01-03, AC-UC01-04; INV-01, INV-06, INV-18, INV-20, INV-21, INV-63, INV-80; ADR-0004, ADR-0008; CR-014

## Mục tiêu
Bốn thao tác `requestRecovery`, `resetPassword`, `changePassword`, `reauthenticate` trên nền TB-8 (#92, #39, #109). Màn hình AUTH04, AUTH05 và đổi mật khẩu đã có trong TB-10 (#88, #90) và đang chờ các API này.

## Kết quả start-task
- Issue #9 đủ chủ task (Huy), mã UC01 và FR-39, bốn API, tiêu chí xong. Phụ thuộc #8 đã đóng.
- Phạm vi: cả bốn thao tác nằm trong `r0a-scope.md` (nhóm Xác thực, 11 thao tác).
- Hợp đồng đủ cho ba thao tác: `requestRecovery` (202 `GenericAcknowledgment`, 429 `RATE_LIMITED`), `resetPassword` (15 phút, dùng một lần, đổi hash, tăng epoch, thu hồi mọi phiên; `LINK_INVALID`, `LINK_EXPIRED`, `LINK_USED`), `changePassword` (sai mật khẩu hiện tại thì 422 `CURRENT_PASSWORD_INVALID` kèm `fields[].path` `currentPassword`). Cả ba không dùng `Idempotency-Key` (CR-014). Không đổi hợp đồng, không cần migration.
- `reauthenticate` chỉ ghi "See TB G3 07 reconciliation decisions". Thao tác duy nhất nhận `reauthToken` là `createPrivacyRequest`, nằm ngoài Lát 1. Đã hỏi Huy (Q1).

## Kế hoạch
- [x] **PR 1, `implement-slice`:** `requestRecovery`, `resetPassword`, email đặt lại mật khẩu.
  - Khôi phục: đếm `RECOVERY` trên `auth_rate_counters` (3/giờ theo email, 10/giờ theo nguồn, Q6 của TB-8); tài khoản `ACTIVE` hoặc `UNVERIFIED` nhận mã `RESET_PASSWORD` 15 phút qua outbox (mã hóa như TB-8); phản hồi giống nhau cho mọi email.
  - Đặt lại: tiêu thụ mã nguyên tử; đổi hash `argon2id-v1`; tăng `auth_epoch`; thu hồi mọi phiên; xóa lần sai theo email; cùng một commit. Không đổi trạng thái tài khoản.
  - Worker: handler gửi email đặt lại, liên kết `tutorbridge://reset-password?token=…` (Q7 của TB-10).
- [x] **PR 2, `implement-slice`:** `changePassword`, `reauthenticate`.
  - Đổi mật khẩu: kiểm trì hoãn, so mật khẩu hiện tại ngoài giao dịch; sai thì ghi lần sai (email, nguồn) và trả 422 `CURRENT_PASSWORD_INVALID`; đúng thì đổi hash, tăng epoch, thu hồi mọi phiên kể cả phiên hiện tại, xóa lần sai theo email.
  - Tái xác thực: như trên nhưng cấp `reauthToken` (purpose `REAUTH` trong `auth_challenges`, một lần, 5 phút), không đổi gì khác.

### File dự kiến đụng
- `backend/src/main/java/com/tutorbridge/identity/internal/`: service mới cho khôi phục và đổi mật khẩu, mở rộng `AccountRepository`, `SessionRepository`, `AuthRateLimiter` (thêm `RECOVERY`), handler email đặt lại.
- `backend/src/main/java/com/tutorbridge/identity/web/`: controller và `PublicEndpoints` cho `/auth/recovery`, `/auth/reset`.
- Test trong `backend/src/test/java/com/tutorbridge/identity/`.

### Ca kiểm thử dự kiến
- `AC_UC01_02`: mã đặt lại dùng lần hai bị 409 `LINK_USED`; quá 15 phút bị 400 `LINK_EXPIRED`; mã xác minh email dùng ở đặt lại bị 400 `LINK_INVALID`; hai yêu cầu đồng thời chỉ một lần thành công.
- `AC_UC01_03`: sau đặt lại hoặc đổi mật khẩu, mọi phiên cũ bị 401; mật khẩu cũ không đăng nhập được, mật khẩu mới thì được.
- `AC_UC01_04`: log không có mật khẩu, mã, email.
- Khôi phục: email không có, `LOCKED`, `DELETED` nhận cùng 202 và không có email; vượt giới hạn 429 `RATE_LIMITED` kèm `Retry-After`.
- Đổi mật khẩu: sai mật khẩu hiện tại 422 `CURRENT_PASSWORD_INVALID`, không phải 401, phiên vẫn còn; sai 5 lần thì 429 `LOGIN_DELAYED`; mật khẩu mới 15–128 code point.
- Tái xác thực: token 43 ký tự, lưu digest, hạn 5 phút; sai mật khẩu 422.

## Trạng thái hiện tại
PR 1 xong trên `feature/TB-9-password-recovery`:
- `6db65df` tái cấu trúc phần dùng chung với TB-8: `LinkIssuer` (tạo mã một lần và sự kiện outbox), `LinkCodes` (`LINK_INVALID`, `LINK_EXPIRED`, `LINK_USED`), `LinkMailSender` (lớp nền cho email có liên kết), `PasswordHasher.acceptableLength`. Hành vi đăng ký và xác minh không đổi (test TB-8 vẫn đạt).
- `128c5d8` `PasswordRecovery`, `RecoveryController`, `PasswordResetMailSender`; giới hạn `recovery-per-account` 3/giờ, `recovery-per-source` 10/giờ trong `application.yaml`; `AccountRepository.replacePassword` (hash, `auth_epoch + 1`, thu hồi mọi phiên) và `consumeOpenChallenges`.

PR 2 xong trên `feature/TB-9-change-password`:
- `5e9c67c` `LoginThrottle`: bộ đếm "5 lần sai trong 15 phút" tách khỏi `AccountSessions` để dùng chung; đăng nhập không đổi.
- `fcfd339` `PasswordChange` (`changePassword`, `reauthenticate`), `PasswordController` (`/api/v1/me/password`, `/api/v1/auth/reauth`, cần phiên), `SessionRepository.findOwn`. Sai mật khẩu hiện tại: 422 `CURRENT_PASSWORD_INVALID` trên `currentPassword` (ở tái xác thực là `password`), ghi một lần sai theo email và nguồn. Đổi thành công: hash mới, `auth_epoch + 1`, thu hồi mọi phiên kể cả phiên hiện tại, xóa lần sai theo email. Tái xác thực: token 256 bit, lưu digest ở `auth_challenges` purpose `REAUTH`, hạn 5 phút.

## Lệnh kiểm tra
```bash
cd backend && ./gradlew test   # Máy Windows của Huy thêm -Djavax.net.ssl.trustStoreType=Windows-ROOT
```

## Kết quả chạy gần nhất
11/11/2026, PR 2, máy Windows của Huy, Docker 29.6.2:
- `./gradlew test`: 188/188 đạt (thêm 10 `PasswordChangeHttpTest`).
- Thử làm hỏng: sai mật khẩu hiện tại trả 401 thì 3 ca đỏ; không ghi lần sai thì 2 ca đỏ; đổi mật khẩu mà giữ phiên thì 1 ca đỏ; khôi phục thì đạt.
- Chạy thật (`bootRun` profile local từ nhánh này, Mailpit; script Node gọi bằng `openapi-fetch` đúng như `mobile/src/features/account/api.ts`): đăng ký 202, xác minh 204, đăng nhập 200; AUTH04 khôi phục 202, email có liên kết `tutorbridge://reset-password?token=…`; AUTH05 đặt lại 204, lần hai 409 `LINK_USED`; phiên cũ 401; mật khẩu cũ 401, mới 200; đổi mật khẩu với mật khẩu hiện tại sai 422 `CURRENT_PASSWORD_INVALID` (`currentPassword`), phiên vẫn 200; đổi đúng 204, phiên hiện tại sau đó 401, đăng nhập bằng mật khẩu mới 200. Log không có mật khẩu, email.
- Trước lần chạy thật cuối, xóa `auth_rate_counters` và `auth_login_failures` trong DB local: các lần thử trước từ `127.0.0.1` đã chạm giới hạn đăng ký và đăng nhập (đúng thiết kế, nhưng chặn script).

11/11/2026, PR 1, máy Windows của Huy, Docker 29.6.2:
- `./gradlew test`: 178/178 đạt (thêm 13 `PasswordRecoveryHttpTest`, 1 ca email đặt lại qua Mailpit trong `VerificationMailTest`).
- Thử làm hỏng: đặt lại mà không tăng epoch và không thu hồi phiên thì 2 ca đỏ; giữ liên kết đặt lại cũ còn dùng được thì 1 ca đỏ; gửi liên kết cho tài khoản `LOCKED` thì 1 ca đỏ; khôi phục thì đạt. (Ca log AC-UC01-04 đỏ theo khi ca khác đỏ, như ở TB-8.)
- `skills-sync`, `immutable-history`, `client-sync`: ok.

## Quyết định đã chọn
Huy, 11/11/2026, trả lời bốn câu của start-task, đều theo đề xuất của agent:
- Q1: làm `reauthenticate` bản tối thiểu: kiểm mật khẩu (sai thì 422 `CURRENT_PASSWORD_INVALID` như `changePassword`), cấp token lưu trong `auth_challenges` purpose `REAUTH`, dùng một lần, hạn 5 phút, gắn với tài khoản. `ReauthInput.purpose` chỉ được kiểm theo enum, không lưu (lưu cần migration). Phần tiêu thụ token làm cùng F06 (`createPrivacyRequest`).
- Q2: khôi phục gửi email cho tài khoản `ACTIVE` và `UNVERIFIED`; đặt lại không đổi trạng thái (vẫn phải xác minh email riêng). `LOCKED`, `DELETED` không nhận gì. Mọi email nhận cùng phản hồi 202.
- Q3: sai mật khẩu hiện tại ở `changePassword` và `reauthenticate` tính chung bộ đếm "5 lần sai trong 15 phút" của đăng nhập (`auth_login_failures`, theo email và nguồn); khi đang trì hoãn trả 429 `LOGIN_DELAYED` kèm `Retry-After`.
- Q4: đặt lại hoặc đổi mật khẩu thành công thì xóa các lần sai theo email của tài khoản; lần sai theo nguồn giữ nguyên.

- Agent chọn trong PR 1, cần người rà xem:
  - Đặt lại thành công thì tiêu thụ luôn các liên kết đặt lại khác còn hạn của tài khoản, để liên kết cũ không đặt lại được lần nữa.
  - Tài khoản thành `LOCKED`/`DELETED` sau khi nhận email: liên kết trả `LINK_INVALID`, mã không bị tiêu thụ, mật khẩu không đổi.
  - Câu báo khôi phục: "Nếu email có tài khoản, chúng tôi đã gửi liên kết đặt lại mật khẩu. Vui lòng kiểm tra hộp thư."; email "Đặt lại mật khẩu TutorBridge", nói rõ đặt lại sẽ đăng xuất mọi thiết bị.

- Agent chọn trong PR 2, cần người rà xem:
  - Đổi mật khẩu khi tài khoản vừa bị khóa hoặc `auth_epoch` đã đổi (đặt lại hay đổi khác thắng trước): 401 `AUTHENTICATION_REQUIRED`, không đổi gì.
  - Đổi mật khẩu không tiêu thụ các liên kết đặt lại đang còn hạn (chưa có quyết định; đặt lại vẫn thu hồi mọi phiên).
  - Câu báo: 422 "Mật khẩu hiện tại không đúng.".

## Câu hỏi còn mở
Không có.

## Bước kế tiếp
1. Huy viết phần tác giả cho #110 (PR 1) và Draft PR của PR 2.
2. Merge PR 2 vào nhánh PR 1, rồi PR 1 vào `dev` sau khi được duyệt; xóa file handoff này trước khi PR 1 sang Ready.
3. Sau khi TB-9 merge: cập nhật handoff TB-10 (AUTH04, AUTH05, đổi mật khẩu đã kiểm được với API thật) và chạy chuỗi TB-10 trên điện thoại.
