# Quy ước code

Những mục ghi **TBD** sẽ chốt khi dựng repo.

## Chung
- Code, tên biến, commit tiếng Anh. Giao diện, thông báo lỗi người dùng, tài liệu tiếng Việt.
- Ghi mã liên quan ở test và PR, không rải trong code. Comment chỉ giải thích lý do không hiển nhiên.
- Làm đúng phạm vi task. Không refactor ngoài lề.
- Phiên bản khóa trong build và CI. Java 25 LTS, Spring Boot 4.1, Node.js 26 LTS, Python 3.14, Expo SDK 57, Next.js 16.4, React 19.2 dùng chung cho mobile và web. Kiểm tra lại bản vá mới nhất khi dựng từng phần.

## API
- Prefix `/api/v1`. `contracts/openapi.yaml` là nguồn chuẩn; code khớp hợp đồng, không ngược lại.
- Lỗi `application/problem+json` với `code` ổn định và `traceId`. Thông báo tiếng Việt, không có stack trace.
- Mã lỗi: 401 chưa có phiên; 403 biết đối tượng nhưng không được phép; 404 cần che sự tồn tại; 409 xung đột hoặc idempotency; 412 phiên bản cũ; 422 dữ liệu sai; 428 thiếu điều kiện; 429 giới hạn.
- Lệnh ghi có `Idempotency-Key` (16–128 ký tự). Sửa theo phiên bản có `If-Match`; tạo lần đầu dùng `If-None-Match: *`.
- Danh sách dùng `limit` và `cursor`. Không dùng offset.
- Nội dung riêng trả `Cache-Control: private, no-store`.
- DTO khai báo tường minh, không trả entity. Cấm thuộc tính ngoài danh sách.
- API nội bộ cho worker dùng `/internal/v1`, tách khỏi OpenAPI công khai.

## Dữ liệu
- Thời điểm là `timestamptz`, hiển thị Asia/Ho_Chi_Minh, 24 giờ. Tiền là số nguyên VND.
- ID là UUID do máy chủ sinh. Đề xuất UUIDv7 để chỉ mục ghi tốt hơn: **TBD** khi dựng backend.
- Migration Flyway chỉ thêm, không sửa file đã merge. Theo thứ tự expand → backfill → validate → enforce.
- Số migration được cấp trong Issue để tránh trùng.
- Migration chạy bằng `tutorbridge_migrator`, ứng dụng chạy bằng `tutorbridge_app`, chỉ có quyền đọc ghi dữ liệu. Không dùng tài khoản sở hữu schema khi chạy.

## Backend
- Package gốc `com.tutorbridge`. Cấu trúc mỗi mô-đun: `api` (công khai), `internal` (service, repository), `web` (controller). Ranh giới kiểm bằng `ArchitectureTest`.
- Truy cập dữ liệu bằng Spring JDBC `JdbcClient`, không dùng JPA.
- Giao dịch đặt ở service. Kiểm quyền trong service, không chỉ ở controller.
- Lỗi nghiệp vụ dùng kiểu riêng, ánh xạ về Problem tại một chỗ.
- Không gọi dịch vụ ngoài trong giao dịch (INV-18).
- Nền chung theo [ADR-0010](../decisions/adr/0010-nen-backend.md), đặt trong `platform`: `Actor` và `SessionResolver` cho xác thực, đọc `Idempotency-Key` và `If-Match`, `Cache-Control`, ánh xạ lỗi validate. Controller dùng các phần này, không tự đọc header.
- Controller khai báo tham số `IdempotencyKey` cho lệnh ghi, `WritePrecondition.Expected` khi cần `If-Match`, `WritePrecondition` khi cho phép tạo lần đầu bằng `If-None-Match: *`. Phản hồi có phiên bản đặt `ETag` bằng `Etag.of(version)`. Mọi phản hồi đã có sẵn `Cache-Control: private, no-store`.
- Service nhận `Actor` làm tham số, không đọc request hay context tĩnh.

### Thêm một chức năng backend
```text
backend/src/main/java/com/tutorbridge/<mô-đun>/
  api/        record và interface mô-đun khác được gọi
  internal/   <Tên>Service (giao dịch, kiểm quyền), <Tên>Repository (JdbcClient)
  web/        <Tên>Controller, DTO request và response
backend/src/test/java/com/tutorbridge/<mô-đun>/
  <Tên>Test, <Tên>HttpTest: tên test chứa mã AC
```
1. Hợp đồng đã có trong `contracts/openapi.yaml`; thiếu thì làm `contract-change` trước.
2. Bảng mới hoặc cột mới đi bằng migration số Đính cấp trong Issue.
3. Viết service và repository, rồi controller dùng nền chung của `platform/web`.
4. Test service trên PostgreSQL thật, test HTTP kiểm cả hợp đồng, ca âm quyền.

## Mobile và web
- npm workspaces: `packages/*`, `mobile`, `web`. Cài phụ thuộc mobile bằng `npx expo install` để đúng bản SDK.
- Dùng `@tutorbridge/api-client`, sinh từ OpenAPI bằng `openapi-typescript` và gọi qua `openapi-fetch`. Không sửa `schema.d.ts` bằng tay; đổi hợp đồng thì chạy `npm run generate:api`.
- Web gọi API ở phía máy chủ (`server-only`). Mobile chỉ dùng biến `EXPO_PUBLIC_`, không chứa bí mật.
- Thư viện theo [ADR-0009](../decisions/adr/0009-nen-giao-dien.md): Expo Router, TanStack Query, `StyleSheet` với token cho mobile, Tailwind và shadcn/ui cho web. Thêm thư viện ngoài danh sách này phải qua Đính.
- Code dùng chung không có React (kiểu Problem, `Idempotency-Key`, định dạng ngày giờ và tiền) đặt ở `@tutorbridge/shared`.
- Mỗi màn hình có đủ: tải, rỗng, lỗi trường, lỗi hệ thống, hết phiên, mất quyền.
- Lệnh ghi có: đang gửi, đã xác nhận, chưa rõ kết quả, thử lại an toàn.
- Giữ dữ liệu đã nhập khi lỗi. Không chỉ dùng màu để phân biệt trạng thái.
- Mã màn hình (`PH06`) gắn vào comment đầu file màn hình để truy vết.

### Cấu trúc mobile
```text
mobile/src/
  app/                 chỉ có route của Expo Router, mỗi file gọi một màn hình trong features
    _layout.tsx        provider: TanStack Query, phiên, theme
    (auth)/            AUTH01–AUTH06, khi chưa đăng nhập
    (parent)/          PH, chặn khi không phải phụ huynh
    (tutor)/           GS, chặn khi không phải gia sư
  features/<tính năng>/
    screens/           màn hình, comment đầu file ghi mã màn hình
    components/        component chỉ tính năng này dùng
    api.ts             query và mutation TanStack Query của tính năng
    schemas.ts         schema zod cho form
  shared/
    api/               client có gắn phiên, đọc Problem, lệnh ghi, tra trạng thái lệnh
    session/           lưu token bằng expo-secure-store, chặn route, hết phiên
    ui/                theme, component nền, khung màn hình đủ trạng thái
    test/              tiện ích test, giả lập API
```
Tên thư mục tính năng theo luồng: `account`, `students`, `tutor-profile`, `calendar`, `needs`, `matching`, `offers`, `classes`, `notifications`.

### Cấu trúc web
```text
web/src/
  app/
    (public)/          WEB01, WEB02
    admin/             AD, chặn khi chưa có phiên quản trị và MFA
  proxy.ts             chặn route quản trị khi chưa có cookie phiên
  features/<tính năng>/   giống mobile: screens, components, actions.ts (server action), schemas.ts
  shared/
    api/               client gọi API bằng cookie phiên `tb_session` (ADR-0010 mục 3)
    session/           cookie phiên, token CSRF, chặn route khi hết phiên
    ui/                component shadcn/ui và khung trang đủ trạng thái
```

### Thêm một màn hình
1. Issue backend của chức năng đã merge, API client đã sinh lại.
2. Tạo màn hình trong `features/<tính năng>/screens/` dùng khung màn hình của `shared/ui`; khung này đã có đủ trạng thái bắt buộc.
3. Lệnh ghi đi qua mutation của `shared/api`, không tự sinh `Idempotency-Key` hay tự thử lại.
4. Thêm route mỏng trong `app/` trỏ tới màn hình.
5. Test từng trạng thái bằng API giả lập của `shared/test`.

## Python worker
- Không trạng thái, không kết nối DB. Mọi dữ liệu đi qua `/internal/v1`.
- Prompt, model, cấu hình có phiên bản. Đầu ra kiểm theo schema.
- Có deadline, số lần thử có giới hạn, ghi chi phí kể cả khi lỗi.

## Kiểm thử
| Mức | Dùng khi | Công cụ |
|---|---|---|
| Unit | Logic thuần: tính điểm, giá, lịch | JUnit; Jest với `jest-expo` cho mobile; Vitest cho `packages/` |
| Tích hợp | Service, giao dịch, ràng buộc DB | PostgreSQL thật qua Testcontainers, image `pgvector/pgvector:pg17` |
| Hợp đồng | Code khớp OpenAPI; đổi hợp đồng không phá vỡ | Validator kiểm request và response thật trong test tích hợp (đề xuất `swagger-request-validator`, dựng ở TB-08); `oasdiff` trên CI (Huy, 04/11/2026) |
| Đồng thời | TX05, đổi lịch, thu hồi | Nhiều kết nối, hai bản API |
| Quyền | Mọi API | Ca âm: khác gia đình, đổi ID, sai vai trò |
| Tải | Theo [mo-rong](../design/mo-rong.md) | k6 |

- Không mock database cho logic giao dịch hay quyền.
- Tên test chứa mã: `AC_UC06_02_concurrent_accept_creates_one_class`.
- Mỗi AC thuộc phạm vi có ít nhất một test. Mỗi INV liên quan có ca âm.
- Không có dữ liệu thật trong fixture.
