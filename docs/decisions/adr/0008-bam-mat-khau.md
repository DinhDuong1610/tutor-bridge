# ADR-0008 Băm mật khẩu bằng Argon2id

**Trạng thái:** Đã chọn thuật toán (Huy, 04/11/2026). Tham số chờ đo, chưa chấp nhận toàn bộ. **Chủ trì:** Huy.

## Vấn đề
UC01 cho mật khẩu 15–128 ký tự và yêu cầu không ghi mật khẩu vào log. `account_credentials` (V5) lưu `password_hash` và `hash_policy`, nhưng chưa có quyết định về thuật toán và tham số. Issue #8 yêu cầu một ADR cho việc này.

## Quyết định
- Băm bằng Argon2id qua `Argon2PasswordEncoder` của Spring Security. Thư viện này cần BouncyCastle (`bcprov`).
- Mỗi mật khẩu có salt ngẫu nhiên riêng. Chuỗi băm theo định dạng PHC (`$argon2id$v=19$m=...,t=...,p=...$salt$hash`), nên tham số nằm ngay trong `password_hash`.
- `hash_policy` ghi tên chính sách, ví dụ `argon2id-v1`. Khi đổi tham số thì tạo chính sách mới. Đăng nhập thành công bằng chính sách cũ thì băm lại theo chính sách hiện hành, trong cùng giao dịch đăng nhập.
- Băm và so khớp chạy ngoài giao dịch DB khi được, vì đây là việc tốn CPU và bộ nhớ, không giữ kết nối lâu.
- Tài khoản không tồn tại vẫn chạy một lần so khớp giả với cùng tham số, để thời gian phản hồi không lộ email có tồn tại hay không.

## Tham số
**TBD.** Tham số (`m` bộ nhớ, `t` số vòng, `p` song song) phải đo trên máy sẽ chạy rồi mới ghi vào đây. Môi trường thử chung chưa được chọn (kế hoạch Lát 1 để Đính quyết trước M6). Không ghi số chưa đo (INV-73).

Mốc tham khảo, chưa phải quyết định: OWASP Password Storage Cheat Sheet đặt cấu hình tối thiểu cho Argon2id là `m=19 MiB, t=2, p=1`.

## Vì sao không chọn phương án khác
| Phương án | Lý do bỏ |
|---|---|
| bcrypt | Chỉ dùng 72 byte đầu của mật khẩu, trong khi UC01 cho tới 128 ký tự. Không tốn bộ nhớ nên yếu hơn trước tấn công bằng GPU |
| PBKDF2 | Không tốn bộ nhớ. Muốn đủ mạnh thì cần số vòng rất lớn |
| scrypt | Dùng được, nhưng Argon2id là lựa chọn đầu tiên trong khuyến nghị của OWASP |

## Hệ quả
- Thêm phụ thuộc BouncyCastle vào backend.
- Mỗi lần đăng nhập tốn bộ nhớ bằng `m`. Nhiều đăng nhập đồng thời nhân lượng bộ nhớ này, nên giới hạn đăng nhập (5 lần/15 phút theo UC01) còn có tác dụng bảo vệ tài nguyên. Ảnh hưởng tải chưa đo.
- Đổi tham số không cần migration, vì tham số nằm trong chuỗi băm và tài khoản được băm lại khi đăng nhập.

## Câu hỏi còn mở
- Tham số cụ thể, đo trên máy chạy. Người chốt: Huy.
- Có dùng pepper (khóa bí mật cộng thêm, giữ ngoài DB) không, và lưu ở đâu. Người chốt: Đính, Huy.
- Có chuẩn hóa Unicode mật khẩu (ví dụ NFC) trước khi băm không. Gõ tiếng Việt trên các bàn phím khác nhau có thể tạo chuỗi byte khác nhau cho cùng một chữ. Người chốt: Đính, Huy.
