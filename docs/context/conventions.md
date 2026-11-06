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

## Mobile và web
- npm workspaces: `packages/*`, `mobile`, `web`. Cài phụ thuộc mobile bằng `npx expo install` để đúng bản SDK.
- Dùng `@tutorbridge/api-client`, sinh từ OpenAPI bằng `openapi-typescript` và gọi qua `openapi-fetch`. Không sửa `schema.d.ts` bằng tay; đổi hợp đồng thì chạy `npm run generate:api`.
- Web gọi API ở phía máy chủ (`server-only`). Mobile chỉ dùng biến `EXPO_PUBLIC_`, không chứa bí mật.
- Điều hướng mobile: **TBD** ở Issue `NEN-MB`, Đính chốt (CR-012). Hướng dẫn của Expo đề xuất Expo Router. Cấu trúc thư mục, lưu phiên, lấy dữ liệu của mobile và web cũng chốt ở `NEN-MB`, `NEN-WEB`.
- Mỗi màn hình có đủ: tải, rỗng, lỗi trường, lỗi hệ thống, hết phiên, mất quyền.
- Lệnh ghi có: đang gửi, đã xác nhận, chưa rõ kết quả, thử lại an toàn.
- Giữ dữ liệu đã nhập khi lỗi. Không chỉ dùng màu để phân biệt trạng thái.
- Mã màn hình (`PH06`) gắn vào tên file hoặc comment đầu file để truy vết.

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
