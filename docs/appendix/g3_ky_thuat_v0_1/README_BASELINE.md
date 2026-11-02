# TutorBridge G3 hợp nhất kỹ thuật

TB G3 07 v0.1, ngày 29/10/2026. **Ứng viên trình rà soát, chưa nghiệm thu G3.**

## Nội dung

- `api/openapi.json` và `.yaml`: cùng một OpenAPI 3.1.1, 123 thao tác trên 108 đường dẫn, 138 schema. Máy chủ `.invalid` là placeholder.
- `sql/001..005`: chuỗi ứng viên đầy đủ; 001–004 giữ nguyên byte từ đường cơ sở, 005 mở rộng F01/F06.
- `data/physical_dictionary.json`: cột, ràng buộc và FK lấy từ schema thực thi nhúng; 77 bảng.
- `data/all_tables.mmd`: quan hệ FK vật lý; không biểu diễn đầy đủ chính sách quyền.
- `review`: truy vết FR, quyết định tích hợp, phiếu nhóm và phiếu thử người dùng.
- `evidence`: kết quả thực thi và giới hạn; không đánh đồng nhúng với PostgreSQL máy chủ.
- `native`: bộ chạy máy chủ trên DB tạm loopback, hiện **chưa thực thi**.

## Bằng chứng đã chạy

5 migration biên dịch thành công và 64/64 ca SQL qua PostgreSQL 17.5 nhúng PGlite 0.3.14. Gồm 28 ca hồi quy F02/F04 và 36 ca DB F01/F06. Đây không phải 64 ca HTTP hay bao phủ đầy đủ F03/F05. OpenAPI qua validator 3.1.1, tham chiếu nội bộ, tên operation duy nhất và 8 ca biên schema. Không có backend triển khai, kết quả tải, restore hoặc thử người dùng thật trong gói.

## Chạy lại kiểm tra nhúng

Trong VS Code, mở thư mục gói, chọn Terminal loại **Command Prompt** trên Windows. Cần Node đã cài.

```bat
npm ci
npm run test:embedded
```

Cài dependencies cần Internet một lần. Lệnh dùng DB nhúng trong bộ nhớ, không kết nối dữ liệu thật. Tệp kết quả `evidence/embedded_results.json` được ghi lại theo lần chạy. Không xóa bằng chứng trước khi lưu bản đối chiếu.

## Kiểm tra OpenAPI và cú pháp SQL

```bat
py -3 -m pip install -r tests/requirements-validation.txt
py -3 tests/validate_contract.py
```

## PostgreSQL 17 máy chủ trên máy nhóm

Cần Docker Desktop đang chạy và Python. Chỉ dùng thư mục gói mới giải nén và DB tạm của gói này; không thay DSN sang DB thật. Mật khẩu ngẫu nhiên chỉ tạo tại máy người chạy, không có sẵn trong gói và không được in ra.

```bat
py -3 native/prepare.py
docker compose up -d --wait
py -3 -m pip install -r native/requirements.txt
set TB_REVIEW_PORT=55433
py -3 native/run.py
docker compose down
```

`compose.yaml` dùng tmpfs: dữ liệu thử mất khi container dừng. Runner từ chối DB đã có schema tutorbridge, yêu cầu PostgreSQL major 17 và chỉ kết nối 127.0.0.1. Có 64 ca SQL và 5 kịch bản bổ sung: tranh chấp lịch gia sư/học sinh, ranh giới liền kề, thu hồi nguồn với công bố và tiêu thụ token một lần. Bốn ca có yêu cầu quan sát chờ khóa; ca ranh giới chỉ dùng hai phiên và commit tuần tự. Bộ native được kiểm cú pháp Python nhưng chưa chạy trên máy chủ tại nơi soạn hồ sơ.

## Giới hạn bắt buộc trước lập trình thật

Không cấp toàn bộ quyền bảng và hàm cho vai trò ứng dụng. Bootstrap quản trị/MFA, hash mật khẩu, email, CSRF, ACL đối tượng, revision, receipt/outbox và storage được triển khai ở service theo hợp đồng. Hàm DB kiểm session/grant là lớp hỗ trợ, không phải hệ thống xác thực hoàn chỉnh. Tác vụ xóa cần thực hiện kho ngoài DB và xuất ledger bền vững thật; có hàng `CONFIRMED` không tự chứng minh kho ngoài đã nhận.

005 thêm cột theo hướng tương thích, không backfill tài khoản cũ hoặc tạo credential. Trước dùng dữ liệu đang tồn tại, đối soát trạng thái ACTIVE với email đã xác minh, các bản xác minh cũ, credential và phiên cũ; không tự coi dữ liệu cũ đã thỏa chính sách mới. Cần diễn tập upgrade trên snapshot có dữ liệu và thiết kế retention/anonymization cho hàng còn FK, thay vì xóa cascade.

Các quyết định D35, D16 và D07 chưa được ghi thay xác nhận Đức Huy. Phần chuẩn hóa API của v0.1 này là ứng viên thay tên/đường dẫn giữa các gói; chưa dùng làm hợp đồng đã phát hành cho client thật.
