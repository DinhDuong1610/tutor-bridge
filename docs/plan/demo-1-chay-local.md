# Chạy Demo 1 trên điện thoại thật với backend local

Issue #128. Dùng cho kịch bản Demo 1a và Demo 1 trong [lat-1-demo-1.md](lat-1-demo-1.md). Máy tính chạy backend, cơ sở dữ liệu và Mailpit; iPhone mở app bằng Expo Go SDK 58. Chỉ dùng dữ liệu giả.

Đính chốt ngày 12/11/2026 (câu 3 của #112): liên kết trong email mở Expo Go bằng cách đổi `link-base`, không dùng dev build.

## Cần có
- Máy Windows có Docker Desktop đang chạy, Node 26, đã chạy `npm ci` ở thư mục gốc.
- JDK 25 đã cài, `JAVA_HOME` trỏ tới nó; kiểm bằng `java -version`. Gradle cần một bản Java có sẵn để tự chạy (thiếu thì báo `JAVA_HOME is not set and no 'java' command could be found`). Gradle chỉ tự tải JDK 25 để biên dịch code, không thay được bản Java này.
- iPhone có Expo Go cho SDK 58, cùng mạng Wi-Fi với máy tính. Mạng khách hoặc mạng công ty có thể chặn hai máy thấy nhau; khi đó dùng điểm phát Wi-Fi từ điện thoại.
- Tài khoản thử chỉ dùng đuôi `@example.test`. Mật khẩu trao đổi riêng, không ghi vào repo, Issue hay Notion.

## 1. Chọn IP và cổng
- IP LAN của máy: `ipconfig`, lấy `IPv4 Address` của card Wi-Fi. Dưới đây gọi là `<IP>`. Máy nhận IP mới khi đổi mạng hoặc khởi động lại, nên kiểm lại trước mỗi buổi demo và sửa ở cả mục 4 và `mobile/.env.local` (mục 5).
- Cổng backend: 8080, hoặc 18080 nếu 8080 đã bận (máy Đính dùng 18080).
- Cổng Metro: 8081, hoặc 8082 nếu 8081 đã bận.

Kiểm tra cổng nào đang bận: `netstat -ano | findstr :8080`.

Cho phép điện thoại gọi vào hai cổng này. Chạy một lần trong PowerShell quyền quản trị, đổi số cổng nếu cần:

```powershell
New-NetFirewallRule -DisplayName "TutorBridge dev" -Direction Inbound -Protocol TCP -LocalPort 18080,8082 -Profile Private -Action Allow
```

Mạng Wi-Fi phải đặt là Private trong Windows, nếu không quy tắc trên không áp dụng.

## 2. File `.env` ở thư mục gốc
Sao chép `.env.example` thành `.env`. Điền ba khóa của TB-8 và khóa MFA quản trị của TB-13, mỗi khóa một giá trị khác nhau:

```bash
openssl rand -base64 32   # chạy bốn lần, cho TB_EMAIL_DIGEST_KEY, TB_EMAIL_ENCRYPTION_KEY, TB_LINK_TOKEN_KEY, TB_ADMIN_MFA_KEY
```

Đổi các mật khẩu `change-me-local-only` nếu muốn. Đổi khóa hoặc mật khẩu sau khi đã có dữ liệu thì tài khoản cũ không dùng được nữa: xóa dữ liệu theo mục 7.

## 3. Cơ sở dữ liệu và Mailpit
```bash
docker compose --env-file .env -f infra/compose.yaml up -d --wait
```

Mailpit mở ở http://localhost:8025, chỉ trên máy tính.

## 4. Backend
Từ thư mục `backend`, trong Git Bash:

```bash
set -a; . ../.env; set +a
export SERVER_PORT=18080
export TUTORBRIDGE_IDENTITY_MAIL_LINK_BASE="exp://<IP>:8082/--/"
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

`TUTORBRIDGE_IDENTITY_MAIL_LINK_BASE` thay giá trị `tutorbridge://` của profile local, để liên kết trong email có dạng `exp://<IP>:8082/--/verify-email?token=...`, mở thẳng vào Expo Go. Cổng trong liên kết phải trùng cổng Metro ở mục 5. IP đổi (đổi mạng, máy nhận IP mới) thì khởi động lại backend với giá trị mới; email đã gửi trước đó vẫn mang IP cũ.

Kiểm tra từ máy tính, rồi từ trình duyệt trên iPhone:

```text
http://<IP>:18080/actuator/health        {"status":"UP"}
```

iPhone không mở được địa chỉ này thì xem lại mục 1 (tường lửa, cùng mạng).

## 5. App trên iPhone
Tạo `mobile/.env.local`:

```text
EXPO_PUBLIC_API_URL=http://<IP>:18080/api/v1
EXPO_PUBLIC_TERMS_VERSION=ban-thu-chua-hieu-luc
```

`ban-thu-chua-hieu-luc` là phiên bản điều khoản thử mà profile local chấp nhận, chưa có hiệu lực pháp lý (TB-8 Q8). Thiếu biến này thì app không gửi form đăng ký.

Từ thư mục gốc. Máy Windows thường có nhiều card mạng (Docker, WSL, VirtualBox), nên phải chỉ rõ IP để mã QR mang đúng `<IP>` của card Wi-Fi:

```bash
# Git Bash
REACT_NATIVE_PACKAGER_HOSTNAME=<IP> npm run start --workspace @tutorbridge/mobile -- --lan --port 8082
```

```powershell
# PowerShell
$env:REACT_NATIVE_PACKAGER_HOSTNAME = "<IP>"
npm run start --workspace @tutorbridge/mobile -- --lan --port 8082
```

Quét mã QR bằng camera của iPhone, mở bằng Expo Go. Địa chỉ trong mã QR phải là `exp://<IP>:8082`. Đổi `.env.local` thì dừng Metro và chạy lại với `--clear`.

## 6. Tài khoản thử và liên kết trong email
1. Trên app, đăng ký phụ huynh (AUTH01) với email dạng `phuhuynh@example.test`.
2. Trên máy tính, mở Mailpit, mở email "xác minh". Liên kết bắt đầu bằng `exp://`.
3. Mailpit chỉ mở được trên máy tính, nên phải chuyển liên kết sang iPhone: chép liên kết, gửi cho chính mình qua ứng dụng nhắn tin hoặc Ghi chú đồng bộ, rồi chạm vào liên kết trên iPhone. Liên kết dùng một lần, hết hạn sau 30 phút, và chỉ là của tài khoản giả.
4. Làm tương tự với gia sư (`giasu@example.test`) và với email đặt lại mật khẩu (AUTH04, AUTH05).

`TBD` (Đính): nếu chép liên kết qua tay làm chậm buổi demo, có thể cho Mailpit nghe trên IP LAN để mở thẳng trên iPhone. Việc này cần sửa `infra/compose.yaml` và để hộp thư lộ ra mạng nội bộ, nên chưa làm.

## 7. Dừng và xóa dữ liệu
```bash
docker compose --env-file .env -f infra/compose.yaml down       # dừng, giữ dữ liệu
docker compose --env-file .env -f infra/compose.yaml down -v    # xóa sạch, tài khoản thử mất hết
```

## Lỗi hay gặp
| Hiện tượng | Kiểm tra |
|---|---|
| Backend dừng ngay khi khởi động, báo thiếu khóa | Ba khóa TB-8 trong `.env` còn trống hoặc chưa `set -a; . ../.env; set +a` |
| App báo không kết nối được | `EXPO_PUBLIC_API_URL` sai IP hoặc cổng, tường lửa, khác mạng; đã chạy lại Metro với `--clear` chưa |
| Đăng ký báo lỗi điều khoản | `EXPO_PUBLIC_TERMS_VERSION` khác `ban-thu-chua-hieu-luc`, hoặc `.env` đặt `TB_TERMS_VERSIONS` khác |
| Chạm liên kết không mở app | Liên kết còn `tutorbridge://`: backend chưa nhận `TUTORBRIDGE_IDENTITY_MAIL_LINK_BASE`. Cổng trong liên kết khác cổng Metro |
| Test Testcontainers lỗi `DockerClientProviderStrategy` | Docker Desktop chưa chạy |
| `./gradlew` báo `JAVA_HOME is not set and no 'java' command could be found` | Chưa cài JDK hoặc chưa đặt `JAVA_HOME` (mục "Cần có") |
| Mã QR mang IP khác `<IP>` (ví dụ của Docker hoặc WSL), iPhone không mở được app | Chưa đặt `REACT_NATIVE_PACKAGER_HOSTNAME=<IP>` khi chạy Metro (mục 5) |
| Bundle báo `Unable to resolve module @tutorbridge/shared`, hoặc Jest báo `Cannot read properties of null (reading 'use')` | Liên kết workspace lệch chữ hoa thường của ổ đĩa: `npm ci` chạy từ `d:\tutor-bridge` còn Expo dùng `D:\tutor-bridge`. Mở terminal tại `D:\tutor-bridge` (chữ D hoa), chạy lại `npm ci`, và chạy mọi lệnh từ đó (gặp trên máy Huy ngày 12/11/2026) |

## Đã kiểm
Mục 3, 4 và liên kết `exp://` trong email đã chạy thử ngày 12/11/2026, xem "Đã kiểm thế nào" của PR đóng #128. Phần iPhone (mục 5, 6) chưa chạy thật: kiểm ở buổi diễn tập Demo 1a.
