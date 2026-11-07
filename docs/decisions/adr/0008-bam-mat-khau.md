# ADR-0008 Băm mật khẩu bằng Argon2id

**Trạng thái:** Đã chọn thuật toán (Huy, 04/11/2026) và chính sách tham số khởi đầu (Huy, 07/11/2026). Pepper và chuẩn hóa Unicode chờ Đính cùng chốt. **Chủ trì:** Huy.

## Vấn đề
UC01 cho mật khẩu 15–128 ký tự và yêu cầu không ghi mật khẩu vào log. `account_credentials` (V5) lưu `password_hash` và `hash_policy`, nhưng chưa có quyết định về thuật toán và tham số. Issue #8 yêu cầu một ADR cho việc này.

## Quyết định
- Băm bằng Argon2id qua `Argon2PasswordEncoder` của Spring Security. Thư viện này cần BouncyCastle (`bcprov`).
- Mỗi mật khẩu có salt ngẫu nhiên riêng. Chuỗi băm theo định dạng PHC (`$argon2id$v=19$m=...,t=...,p=...$salt$hash`), nên tham số nằm ngay trong `password_hash`.
- `hash_policy` ghi tên chính sách, ví dụ `argon2id-v1`. Khi đổi tham số thì tạo chính sách mới. Đăng nhập thành công bằng chính sách cũ thì băm lại theo chính sách hiện hành, trong cùng giao dịch đăng nhập.
- Băm và so khớp chạy ngoài giao dịch DB khi được, vì đây là việc tốn CPU và bộ nhớ, không giữ kết nối lâu.
- Tài khoản không tồn tại vẫn chạy một lần so khớp giả với cùng tham số, để thời gian phản hồi không lộ email có tồn tại hay không.

## Tham số
Huy chọn ngày 07/11/2026:
- Chính sách `argon2id-v1`: `m=19456` KiB (19 MiB), `t=2`, `p=1`, salt 16 byte, độ dài băm 32 byte. Đây là cấu hình tối thiểu của OWASP Password Storage Cheat Sheet, dùng làm sàn, không phải số đo năng lực.
- Trước thí điểm, đo lại trên máy sẽ chạy (môi trường thử chung, Đính chọn trước M6). Máy chịu được thì nâng `m` hoặc `t` thành chính sách mới; tài khoản được băm lại khi đăng nhập, không cần migration.
- Số đo tham khảo duy nhất hiện có là trên máy cá nhân của Huy (khoảng 26 ms trung vị cho `argon2id-v1`, ghi trong `docs/handoff/TB-8.md`). Không dùng số này để nói về tải (INV-73).

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
- Đo lại tham số trên máy sẽ chạy. Người làm: Huy, khi có môi trường thử chung.
- Có dùng pepper (khóa bí mật cộng thêm, giữ ngoài DB) không, và lưu ở đâu. Người chốt: Đính, Huy. Ý kiến của Huy (07/11/2026): không dùng ở Lát 1 vì chưa có nơi giữ bí mật và mất pepper là mất toàn bộ mật khẩu; xem lại khi có môi trường thật.
- Có chuẩn hóa Unicode mật khẩu trước khi băm không. Gõ tiếng Việt trên các bàn phím khác nhau có thể tạo chuỗi byte khác nhau cho cùng một chữ. Người chốt: Đính, Huy. Ý kiến của Huy (07/11/2026): chuẩn hóa NFKC trước khi băm, theo NIST SP 800-63B.
