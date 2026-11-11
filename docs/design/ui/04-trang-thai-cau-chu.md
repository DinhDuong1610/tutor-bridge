# Trạng thái và câu chữ

Mọi màn hình vẽ đủ các trạng thái dưới đây khi áp dụng (`03-man-hinh.md`, "Trạng thái chung bắt buộc"). Câu chữ giống nhau trên mobile và web. Câu có trong code ghi **(code)**: Figma dùng nguyên văn, không viết lại. Câu **(đề xuất)** thì Đính chốt khi duyệt, rồi đưa vào code.

## Trạng thái đọc dữ liệu
| Trạng thái | Khi nào | Hiển thị | Câu chữ |
|---|---|---|---|
| Đang tải | Lần đầu mở | Chỉ báo xoay ở giữa vùng nội dung, giữ tiêu đề màn hình | Không chữ, nhãn trợ năng "Đang tải" |
| Rỗng | Danh sách không có mục | `Notice` info và nút hành động | Câu riêng từng màn hình, nói đây là gì và làm gì tiếp: "Chưa có hồ sơ con nào." + nút "Thêm hồ sơ con" |
| Lỗi tải | Mất mạng, lỗi 5xx | `Notice` danger, nút "Thử lại" | "Không tải được dữ liệu. Kiểm tra kết nối rồi thử lại." (code; web: "...rồi tải lại trang.") |
| Dữ liệu cũ | Tải lại lỗi khi đã có dữ liệu | Giữ dữ liệu, `Notice` warning ở trên | "Không cập nhật được. Đang hiển thị dữ liệu đã tải trước đó." (code) |
| Mất quyền | 403 | `Notice` danger, không hiện dữ liệu | "Bạn không còn quyền xem nội dung này." (code) |
| Không tìm thấy | 404 | `Notice` danger | "Không tìm thấy nội dung này. Có thể nó đã bị xóa hoặc bạn không có quyền xem." (code) |
| Thao tác quá nhanh | 429 | `Notice` warning, nút "Thử lại" | "Bạn thao tác quá nhanh. Vui lòng đợi một lúc rồi thử lại." (code) |
| Hết phiên | 401 | Nền tự đưa về đăng nhập. Màn hình đăng nhập hiện `Notice` info | "Phiên đăng nhập đã hết. Vui lòng đăng nhập lại. Bản nháp đã lưu vẫn còn." (đề xuất) |

Mất quyền và không tìm thấy không nói gì về đối tượng (tên, chủ sở hữu), để không lộ dữ liệu ngoài quyền.

## Trạng thái lệnh ghi
`CommandStatus` đặt ngay trên nút gửi. Nút gửi hiện chỉ báo xoay khi đang gửi.

| Trạng thái | Hiển thị | Câu chữ |
|---|---|---|
| Đang gửi | `Notice` info, nút `busy` | "Đang gửi…" (code) |
| Đã xác nhận | `Notice` success, rồi chuyển màn hình nếu luồng yêu cầu | Câu riêng nói điều máy chủ đã ghi: "Đã gửi đề nghị cho Nguyễn Thị Lan." Không dùng "Thành công!" |
| Lỗi trường | Lỗi dưới từng ô, `Notice` danger chung | "Vui lòng sửa các ô được đánh dấu rồi gửi lại." (code) |
| Lỗi khác | `Notice` danger | Tiêu đề lỗi từ máy chủ, hoặc "Không thực hiện được. Vui lòng thử lại." (code) |
| Chưa rõ kết quả | `Notice` warning, nút "Thử lại" | "Chưa rõ kết quả. Yêu cầu có thể đã được ghi nhận. Thử lại sẽ không tạo bản trùng." (code) |

Dữ liệu đã nhập giữ nguyên ở mọi trạng thái lỗi. Không khóa nút làm cách duy nhất chống gửi lặp; nền đã gửi `Idempotency-Key`.

## Trạng thái nghiệp vụ hay gặp ở Lát 1 (đề xuất)
| Trạng thái | Màn hình | Câu chữ |
|---|---|---|
| Bản cũ (`problemKind` là `stale`) | PH05, PH08, GS02, GS04 | "Dữ liệu đã thay đổi từ lúc bạn mở. Tải lại để xem bản mới; phần bạn đã nhập hợp lệ được giữ lại." |
| Giá hoặc lịch gia sư đổi | PH08 | "Giá hoặc lịch của gia sư đã thay đổi. Hãy xem lại trước khi gửi." |
| Không giữ chỗ | PH06, PH08, PH09 | "Gợi ý và đề nghị đang chờ chưa giữ chỗ. Lớp chỉ được chốt khi gia sư chấp nhận." |
| Xung đột lịch khi chấp nhận | GS05 | "Chưa nhận được lớp: có buổi trùng lịch. Không buổi nào được đặt. Phụ huynh có thể gửi đề nghị mới." |
| Đề nghị hết hạn | PH09, GS05 | "Đề nghị đã hết hạn lúc 18:00 20/11/2026. Không thể chấp nhận nữa." |
| Nháp chưa đủ điều kiện | PH04 | "Đã lưu nháp. Cần thêm: ngân sách, khung giờ rảnh. Chưa thể tìm gia sư từ bản nháp." |
| Không có gia sư phù hợp | PH06 | "Chưa có gia sư đáp ứng đủ điều kiện." và danh sách điều kiện đã kiểm, không nói về lịch hay hồ sơ của người khác |
| Kết quả gợi ý hết hạn | PH06 | "Kết quả này đã cũ. Tìm lại để có danh sách theo giá và lịch hiện tại." |
| Lỗi chạy gợi ý | PH06 | "Không tìm được lúc này. Nhu cầu của bạn vẫn được giữ." và nút "Thử lại". Khác hẳn trạng thái không có gia sư |

## Quy tắc viết
- Xưng "bạn". Gọi gia sư theo tên hiển thị máy chủ trả, không tự thêm "thầy", "cô".
- Câu chủ động, nói việc đã xảy ra và việc làm tiếp. Không xin lỗi chung chung, không "Oops", không dấu chấm than, không emoji.
- Nút là động từ nói đúng kết quả: "Lưu nháp", "Xác nhận nhu cầu", "Gửi đề nghị", "Chấp nhận đề nghị", "Từ chối". Không dùng "OK", "Gửi" chung chung khi có từ cụ thể hơn.
- Lỗi nói cái gì sai và sửa thế nào: "Mật khẩu cần từ 15 đến 128 ký tự.", không "Mật khẩu không hợp lệ."
- Giới hạn nói trước, không đợi lỗi: gợi ý dưới ô ghi "20–2.000 ký tự", bộ đếm ký tự hiện khi gõ.
- Không dùng từ hứa hẹn kết quả: "đảm bảo", "phù hợp nhất", "giúp con tiến bộ" (PRD 1.2, WEB01).
- Số theo kiểu Việt: `2.000`, `250.000 ₫`. Ngày `20/11/2026`, giờ `18:00`.
- Dữ liệu mẫu trong Figma là tên giả rõ ràng (Minh An, Nguyễn Thị Lan), email `@example.com`. Không dùng dữ liệu trẻ thật.
