# TB-9 Khôi phục, đổi mật khẩu, tái xác thực

- **Chủ task:** Huy
- **Nhánh:** PR 1 `feature/TB-9-password-recovery` (khôi phục, đặt lại); PR 2 đổi mật khẩu, tái xác thực, tách sau khi PR 1 merge
- **Agent đang làm:** Claude (Claude Code)
- **Cập nhật lần cuối:** 11/11/2026, start-task
- **Mã liên quan:** UC01, FR-39; AC-UC01-02, AC-UC01-03, AC-UC01-04; INV-01, INV-06, INV-18, INV-20, INV-21, INV-63, INV-80; ADR-0004, ADR-0008; CR-014

## Mục tiêu
Bốn thao tác `requestRecovery`, `resetPassword`, `changePassword`, `reauthenticate` trên nền TB-8 (#92, #39, #109). Màn hình AUTH04, AUTH05 và đổi mật khẩu đã có trong TB-10 (#88, #90) và đang chờ các API này.

## Kết quả start-task
- Issue #9 đủ chủ task (Huy), mã UC01 và FR-39, bốn API, tiêu chí xong. Phụ thuộc #8 đã đóng.
- Phạm vi: cả bốn thao tác nằm trong `r0a-scope.md` (nhóm Xác thực, 11 thao tác).
- Hợp đồng đủ cho ba thao tác: `requestRecovery` (202 `GenericAcknowledgment`, 429 `RATE_LIMITED`), `resetPassword` (15 phút, dùng một lần, đổi hash, tăng epoch, thu hồi mọi phiên; `LINK_INVALID`, `LINK_EXPIRED`, `LINK_USED`), `changePassword` (sai mật khẩu hiện tại thì 422 `CURRENT_PASSWORD_INVALID` kèm `fields[].path` `currentPassword`). Cả ba không dùng `Idempotency-Key` (CR-014). Không đổi hợp đồng, không cần migration.
- `reauthenticate` chỉ ghi "See TB G3 07 reconciliation decisions". Thao tác duy nhất nhận `reauthToken` là `createPrivacyRequest`, nằm ngoài Lát 1. Đã hỏi Huy (Q1).

## Kế hoạch
- [ ] **PR 1, `implement-slice`:** `requestRecovery`, `resetPassword`, email đặt lại mật khẩu.
  - Khôi phục: đếm `RECOVERY` trên `auth_rate_counters` (3/giờ theo email, 10/giờ theo nguồn, Q6 của TB-8); tài khoản `ACTIVE` hoặc `UNVERIFIED` nhận mã `RESET_PASSWORD` 15 phút qua outbox (mã hóa như TB-8); phản hồi giống nhau cho mọi email.
  - Đặt lại: tiêu thụ mã nguyên tử; đổi hash `argon2id-v1`; tăng `auth_epoch`; thu hồi mọi phiên; xóa lần sai theo email; cùng một commit. Không đổi trạng thái tài khoản.
  - Worker: handler gửi email đặt lại, liên kết `tutorbridge://reset-password?token=…` (Q7 của TB-10).
- [ ] **PR 2, `implement-slice`:** `changePassword`, `reauthenticate`.
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
start-task xong, chưa có code.

## Lệnh kiểm tra
```bash
cd backend && ./gradlew test   # Máy Windows của Huy thêm -Djavax.net.ssl.trustStoreType=Windows-ROOT
```

## Kết quả chạy gần nhất
Chưa chạy.

## Quyết định đã chọn
Huy, 11/11/2026, trả lời bốn câu của start-task, đều theo đề xuất của agent:
- Q1: làm `reauthenticate` bản tối thiểu: kiểm mật khẩu (sai thì 422 `CURRENT_PASSWORD_INVALID` như `changePassword`), cấp token lưu trong `auth_challenges` purpose `REAUTH`, dùng một lần, hạn 5 phút, gắn với tài khoản. `ReauthInput.purpose` chỉ được kiểm theo enum, không lưu (lưu cần migration). Phần tiêu thụ token làm cùng F06 (`createPrivacyRequest`).
- Q2: khôi phục gửi email cho tài khoản `ACTIVE` và `UNVERIFIED`; đặt lại không đổi trạng thái (vẫn phải xác minh email riêng). `LOCKED`, `DELETED` không nhận gì. Mọi email nhận cùng phản hồi 202.
- Q3: sai mật khẩu hiện tại ở `changePassword` và `reauthenticate` tính chung bộ đếm "5 lần sai trong 15 phút" của đăng nhập (`auth_login_failures`, theo email và nguồn); khi đang trì hoãn trả 429 `LOGIN_DELAYED` kèm `Retry-After`.
- Q4: đặt lại hoặc đổi mật khẩu thành công thì xóa các lần sai theo email của tài khoản; lần sai theo nguồn giữ nguyên.

## Câu hỏi còn mở
Không có.

## Bước kế tiếp
PR 1: viết test cho `requestRecovery`, `resetPassword` rồi làm service, controller, handler email.
