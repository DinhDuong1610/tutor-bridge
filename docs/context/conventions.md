# Quy ước code

Mục ghi **TBD** là quyết định còn mở, chưa được tự chọn.

## Chung
- Code, tên biến, commit tiếng Anh. Giao diện, thông báo lỗi người dùng, tài liệu tiếng Việt.
- Ghi mã liên quan ở test và PR, không rải trong code. Comment chỉ giải thích lý do không hiển nhiên.
- Làm đúng phạm vi task. Không refactor ngoài lề.
- Phiên bản khóa trong build và CI. Java 25 LTS, Spring Boot 4.1, Node.js 26 LTS, Python 3.14, Expo SDK 58 (React Native 0.88), Next.js 16.4, React 19.3 dùng chung cho mobile và web. Kiểm tra lại bản vá mới nhất khi dựng từng phần.

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
- ID là UUID do máy chủ sinh, lấy từ bean `IdGenerator` (hiện UUIDv4). Đổi sang UUIDv7 để chỉ mục ghi tốt hơn: **TBD**, Đính chốt (bàn giao M0); khi chốt chỉ đổi bean trong `RuntimeServices`.
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
- Code mới lấy thời gian từ bean `Clock` (UTC) và ID từ bean `IdGenerator`, không gọi `Instant.now()` hay `UUID.randomUUID()` trực tiếp, để test cố định được.
- Không ghi bí mật hay dữ liệu cá nhân vào log. `LogRedaction` che bearer, cookie phiên, email, cặp `password=`… trong log JSON và trong log text của profile `local` (`RedactedTextLogFormatter`, cả stack trace; Đính chốt 13/11/2026, #85), nhưng chỉ là lớp chặn cuối.
- Dữ liệu giả cho máy local: mỗi mô-đun khai báo một bean `LocalSeed` trong gói `internal`, chỉ ghi bảng của mình, chạy lại được nhiều lần, dữ liệu rõ ràng là giả.
- Service nhận `Actor` làm tham số, không đọc request hay context tĩnh. Controller lấy nó bằng `@AuthenticationPrincipal Actor actor`.
- Mọi đường dẫn cần phiên, trừ thao tác mô-đun khai báo công khai bằng một bean `PublicEndpoints` (ví dụ đăng ký, đăng nhập). Không mở đường dẫn bằng cách sửa `SecurityConfig`.
- Test HTTP của chức năng: kế thừa `HttpTest` (toàn ứng dụng, PostgreSQL thật), tạo người gọi bằng `newActor(role)`, gửi bằng `.with(sessions().as(actor))`, và mọi phản hồi qua `.andExpect(Contract.matches())`. Xem `HttpTestExampleTest`.
- Test riêng tầng web không cần DB: `@WebMvcTest` kèm `@Import(WebSecurityTestConfig.class)` và `TestSessions`.
- Ca âm quyền luôn có: không phiên, sai vai trò, đối tượng của người khác.

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

## Mobile và web (chung)
- npm workspaces: `packages/*`, `mobile`, `web`. Cài từ thư mục gốc bằng `npm ci` với Node 26.
- Dùng `@tutorbridge/api-client`, sinh từ OpenAPI bằng `openapi-typescript` và gọi qua `openapi-fetch`. Không sửa `schema.d.ts` bằng tay; đổi hợp đồng thì chạy `npm run generate:api`.
- Code dùng chung không có React (kiểu Problem, `Idempotency-Key`, định dạng ngày giờ Asia/Ho_Chi_Minh 24 giờ và tiền VND) đặt ở `@tutorbridge/shared`.
- Thư viện theo [ADR-0009](../decisions/adr/0009-nen-giao-dien.md). Thêm thư viện ngoài danh sách phải qua Đính. Thư viện mới phát hành ít nhất hai tuần, khóa đúng phiên bản.
- Cả workspace dùng một bản `react`, `react-dom`, `@types/react`, `@types/react-dom` (`overrides` ở `package.json` gốc), đúng bản Expo SDK yêu cầu. Sau khi thêm thư viện, `npm ls react @types/react` chỉ được có một phiên bản.
- Mỗi màn hình có đủ: tải, rỗng, lỗi trường, lỗi hệ thống, hết phiên, mất quyền. Lệnh ghi có: đang gửi, đã xác nhận, chưa rõ kết quả, thử lại an toàn. Không hiện thành công trước khi máy chủ xác nhận.
- Mỗi thao tác ghi của người dùng có một `Idempotency-Key`: thử lại sau khi chưa rõ kết quả thì giữ khóa; sau kết quả rõ (thành công hay lỗi) thì lần sau dùng khóa mới. Nền đã làm việc này, màn hình không tự sinh khóa.
- Lệnh ghi nhận lỗi 5xx, có hay không có thân Problem, là **chưa rõ kết quả**, vì lỗi có thể xảy ra sau khi giao dịch đã commit (Đính chốt trên #83, 12/11/2026). Chỉ lỗi 4xx là thất bại rõ. Đọc dữ liệu không theo quy tắc này. Web đã làm trong `runCommand`; mobile (`call`, `useCommand`) làm ở PR sau.
- Giữ dữ liệu đã nhập khi lỗi. Không chỉ dùng màu để phân biệt trạng thái. Câu chữ trạng thái giống nhau trên mobile và web.
- Mã màn hình (`PH06`, `AD03`) ghi trong comment đầu file màn hình để truy vết.
- Màu lấy từ token (mobile `theme.ts`, web biến CSS trong `globals.css`), cùng một bảng màu. Đổi màu thì đo lại độ tương phản theo WCAG 2.1 và ghi số đo.

## Mobile
### Cấu trúc
```text
mobile/src/
  app/                 chỉ có route của Expo Router, mỗi file gọi một màn hình trong features
    _layout.tsx        provider: phiên, TanStack Query (theme là token, không cần provider)
    index.tsx          điều hướng về trang của vai trò, hoặc đăng nhập
    +not-found.tsx     liên kết tới màn hình không có
    (auth)/            AUTH01–AUTH05, khi chưa đăng nhập; nhóm không tạo đoạn đường dẫn: /sign-in
    parent/            PH, đường dẫn /parent/..., chỉ PARENT
    tutor/             GS, đường dẫn /tutor/..., chỉ TUTOR
  features/<tính năng>/
    screens/           màn hình, comment đầu file ghi mã màn hình
    components/        component chỉ tính năng này dùng
    api.ts             query và lệnh ghi của tính năng
    schemas.ts         schema zod cho form
  shared/
    api/               client gắn phiên, call, useCommand, QueryProvider
    session/           SessionProvider, RequireRole, lưu phiên bằng expo-secure-store
    form/              FormTextField, useServerErrors
    ui/                theme.ts, Button, TextField, Notice, QueryScreen, CommandStatus
    test/              mockApi, renderScreen, phiên giả, kiểu matcher của expo-router, test mẫu
mobile/__mocks__/      bản giả expo-secure-store, expo-crypto cho Jest
```
- Vai trò dùng thư mục thường `parent/`, `tutor/`, không dùng nhóm `(parent)`: nhóm không tạo đoạn đường dẫn, nên trang chủ hai vai trò sẽ cùng là `/` và đụng nhau.
- Import trong `src` dùng alias `@/`. Cài phụ thuộc bằng `npx expo install` để đúng bản SDK.
- Chỉ dùng biến `EXPO_PUBLIC_` (xem `mobile/.env.example`), không chứa bí mật.
- Tên thư mục tính năng theo luồng: `account`, `students`, `tutor-profile`, `calendar`, `needs`, `matching`, `offers`, `classes`, `notifications`.

### Gọi API và phiên
- Đọc: `useQuery` với `queryFn: () => call(api.GET(...))`. `call` trả dữ liệu hoặc ném `ApiError` (có `kind`, `code`, `fields`) hay `UnknownOutcomeError`. Đọc tự thử lại tối đa hai lần khi mất mạng hoặc lỗi 5xx, không thử lại lỗi 4xx.
- Ghi: `useCommand((input, key) => call(api.POST(..., { params: { header: { "Idempotency-Key": key } } })))`. Hiển thị theo `state`: `sending`, `confirmed`, `failed` (kèm `error.fields`), `unknown` (nút thử lại gọi `retry`).
- Phiên: `useSession()` cho `status` (`loading`, `signedIn`, `signedOut` kèm `endedBy`), `signIn(session)` sau đăng nhập, `signOut()` sau khi gọi API đăng xuất. Token chỉ nằm trong `expo-secure-store`. Mọi 401 tự kết thúc phiên, xóa token và dữ liệu đã tải, đưa về `/sign-in`; màn hình không tự xử lý 401.
- Khu vực theo vai trò bọc bằng `RequireRole` trong `_layout.tsx`; màn hình đăng nhập bọc bằng `RequireSignedOut`. Máy chủ vẫn kiểm quyền mọi yêu cầu.

### Giao diện và form
- Màu, cỡ chữ, khoảng cách lấy từ `@/shared/ui/theme`, không viết số hay mã màu trong màn hình. Không đặt chiều cao cố định quanh chữ để chữ phóng to 200% vẫn đọc được.
- Màn hình đọc dữ liệu dùng `QueryScreen`: đã có tải, rỗng, lỗi hệ thống kèm thử lại, mất quyền, không tìm thấy, dữ liệu cũ khi tải lại lỗi. `empty` thay cả nội dung; màn hình cần giữ form khi danh sách rỗng thì tự hiện thông báo rỗng.
- Lệnh ghi hiển thị bằng `CommandStatus` cạnh nút gửi; nút gửi đặt `busy` khi `state === "sending"`.
- Form: `useForm` với `zodResolver(schema)`; giới hạn lấy từ schema của hợp đồng, máy chủ vẫn kiểm lại. Ô nhập dùng `FormTextField`; các thuộc tính khác của `TextInput` (`secureTextEntry`, `autoComplete`, `keyboardType`, `autoCapitalize`, `multiline`) truyền thẳng vào nó, không tự viết ô nhập riêng. Lỗi trường của máy chủ đặt vào ô bằng `useServerErrors(command.error, form.setError, [tên ô])`.

### Test
- Màn hình: `mockApi({ "GET /students": () => json(200, ...) })` thay `fetch` theo đường dẫn hợp đồng, yêu cầu ngoài danh sách làm test hỏng; `renderScreen(<Screen />, { session: fakeSession("PARENT") })` dựng màn hình với đủ provider; `restore()` sau mỗi test.
- Điều hướng: `renderRouter` và `toHavePathname` của `expo-router/testing-library`; phiên bằng `storeSession(fakeSession(...))`.
- Mẫu đầy đủ: `mobile/src/shared/test/example.test.tsx`.

### Thêm một màn hình
1. Issue backend của chức năng đã merge, API client đã sinh lại.
2. Tạo màn hình trong `features/<tính năng>/screens/` với `QueryScreen`, form và `useCommand` của nền.
3. Thêm route mỏng trong `app/` trỏ tới màn hình; khu vực theo vai trò đã được chặn ở `_layout.tsx`.
4. Test từng trạng thái bằng `mockApi` và `renderScreen` như test mẫu.

## Web
### Cấu trúc
```text
web/src/
  app/
    (public)/          WEB01 (/), WEB02 (/huong-dan); không có học liệu riêng
    admin/(auth)/      AD01 /admin/sign-in
    admin/(console)/   AD03 /admin/verifications, AD04...; layout kiểm phiên quản trị
    admin/session-expired/route.ts   xóa cookie khi API trả 401
    not-found.tsx      404 tiếng Việt
  proxy.ts             chặn /admin khi chưa có cookie phiên
  features/<tính năng>/   components, actions.ts (Server Action), schemas.ts
  shared/
    api/               server.ts (adminApi), result.ts (load, runCommand)
    session/           cookie phiên, verifyAdminSession
    form/              useServerCommand, useServerFieldErrors
    lib/utils.ts       cn() cho class Tailwind
    ui/                shadcn/ui (button, input), FormField, Notice, AdminPage, StateMessage, CommandStatus
    test/              test mẫu, stub server-only
```
- Thêm component shadcn/ui theo `web/AGENTS.md` (sửa import `cn`, không cài gói `cn`).
- Biến môi trường ở `web/.env.example`. API chỉ được gọi phía máy chủ (`server-only`).

### Phiên quản trị
- Cookie `tb_session` (HttpOnly, Secure, SameSite=Strict) đặt bằng `setSessionCookie` trong Server Action đăng nhập; xóa bằng `clearSessionCookie`. Trình duyệt không đọc được token.
- Gọi API bằng `await adminApi()`: tự gửi cookie phiên; lệnh ghi gửi thêm cặp CSRF và `Origin` (`TB_WEB_ORIGIN`, phải nằm trong `tutorbridge.security.admin-origins` của API).
- `proxy.ts` chỉ kiểm có cookie. Kiểm thật là `verifyAdminSession()`: layout quản trị gọi, và **mỗi trang quản trị cũng gọi** trước khi lấy dữ liệu, vì trang được prerender một phần nên phần tĩnh của trang con vẫn gửi đi dù layout chuyển hướng. Không đặt nội dung nhạy cảm trong phần tĩnh.

### Trang và form
- Đọc trong Server Component: `const result = await load((await adminApi()).GET(...))`; `result.status !== "ok"` thì trả `<StateMessage result={result} />`. 401 tự về hết phiên. Danh sách rỗng dùng `EmptyState`. Khung trang `AdminPage`.
- Ghi: Server Action `"use server"` nhận `(input, idempotencyKey)` và trả `runCommand((await adminApi()).POST(..., { params: { header: { "Idempotency-Key": idempotencyKey } } }))`. Form phía trình duyệt dùng `useServerCommand(action)`, `useServerFieldErrors`, `FormField`, `CommandStatus`.

### Test
- Hàm phía máy chủ chạy Vitest trong Node; component thêm dòng `// @vitest-environment jsdom` và dùng Testing Library.
- Mẫu đầy đủ: `web/src/shared/test/example.test.tsx`.

### Thêm một trang quản trị
1. Issue backend đã merge, API client đã sinh lại.
2. Tạo trang trong `app/admin/(console)/...`, gọi `verifyAdminSession()` rồi `load(...)`; form là client component trong `features/<tính năng>/` với Server Action ở `actions.ts`.
3. Test Server Action và form như test mẫu.

## Python worker
- Không trạng thái, không kết nối DB. Mọi dữ liệu đi qua `/internal/v1`.
- Prompt, model, cấu hình có phiên bản. Đầu ra kiểm theo schema.
- Có deadline, số lần thử có giới hạn, ghi chi phí kể cả khi lỗi.

## Kiểm thử
| Mức | Dùng khi | Công cụ |
|---|---|---|
| Unit | Logic thuần: tính điểm, giá, lịch | JUnit; Jest với `jest-expo` cho mobile; Vitest cho `packages/` và web |
| Tích hợp | Service, giao dịch, ràng buộc DB | PostgreSQL thật qua Testcontainers, image `pgvector/pgvector:pg17` |
| Hợp đồng | Phản hồi khớp OpenAPI (thao tác, mã trạng thái, kiểu nội dung, schema); đổi hợp đồng không phá vỡ | `Contract.matches()` trên mọi test HTTP, validator JSON Schema 2020-12 `com.networknt:json-schema-validator`; `oasdiff` trên CI (Huy, 04/11/2026) |
| Đồng thời | TX05, đổi lịch, thu hồi | Nhiều kết nối, hai bản API; dùng `Concurrently`, không dùng common pool |
| Quyền | Mọi API | Ca âm: khác gia đình, đổi ID, sai vai trò |
| Tải | Theo [mo-rong](../design/mo-rong.md) | k6 |

- Không mock database cho logic giao dịch hay quyền.
- Tên test chứa mã: `AC_UC06_02_concurrent_accept_creates_one_class`.
- Mỗi AC thuộc phạm vi có ít nhất một test. Mỗi INV liên quan có ca âm.
- Không có dữ liệu thật trong fixture.
