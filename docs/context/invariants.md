# Bất biến

Những điều luôn phải đúng, ở mọi module, mọi lần sửa. Vi phạm là lỗi chặn merge. Mỗi dòng có nguồn; khi cần chi tiết, đọc nguồn.

## Quyền
| Mã | Bất biến | Nguồn |
|---|---|---|
| INV-01 | Mặc định từ chối. Mọi đường đọc, ghi, tải, truy xuất, job kiểm tại backend đủ: tài khoản, vai trò, quan hệ, trạng thái đối tượng | NFR01, ma trận quyền |
| INV-02 | Biết ID hoặc URL không cấp quyền. Ẩn nút trên giao diện không phải kiểm soát quyền | NFR01, AC-UC02-02 |
| INV-03 | `owner`, `role`, `actor` lấy từ phiên, không lấy từ payload. Đăng ký chỉ nhận PARENT hoặc TUTOR | BR, AC-UC01-01, G3-D07-07 |
| INV-04 | Quản trị không mặc định đọc học liệu. Hỗ trợ chỉ có VIEW_ORIGINAL do chủ sở hữu nguồn cấp, tối đa 24 giờ, đúng vụ việc, không tải, không AI, không đáp án | G3-D07-03 |
| INV-05 | Người xác minh không tự duyệt hồ sơ của mình | UC03 |
| INV-06 | Payload, cache, lỗi không chứa dữ liệu ngoài quyền. Không ẩn bằng giao diện. Lỗi không tiết lộ đối tượng ngoài quyền | NFR01, TB G2 02 mục 5.4 |

## Lịch và giao dịch
| Mã | Bất biến | Nguồn |
|---|---|---|
| INV-10 | Chốt lớp là nguyên tử: một lớp, mục tiêu đầu, toàn bộ buổi và hai booking mỗi buổi cùng một commit, hoặc không có gì | BR05, TX05 |
| INV-11 | Một tài nguyên (gia sư, học sinh) không có hai booking hiệu lực trùng giờ. Khoảng là `[bắt đầu, kết thúc)`, hai buổi sát biên hợp lệ | BR04, ADR-0002 |
| INV-12 | Chốt trùng được chặn bằng ràng buộc DB, không bằng khóa RAM hay khóa nút | BR06, ADR-0002 |
| INV-13 | Gợi ý và đề nghị đang chờ không giữ chỗ. Chốt phải kiểm lại toàn bộ tại commit | BR04, BR05 |
| INV-14 | Một nhu cầu tạo tối đa một lớp | BR05 |
| INV-15 | Đổi lịch giữ buổi cũ đến khi lịch mới áp dụng thành công. Lịch đã qua không viết lại | BR05 |
| INV-16 | Thứ tự khóa chung: lớp → thực thể nghiệp vụ → tài nguyên theo khóa ổn định → dữ liệu phụ | F03 |
| INV-17 | Giá trong đề nghị và lớp là ảnh chụp, không đổi theo hồ sơ sau đó. Giá buổi `ceil(rate × D / 60)`, tiền là số nguyên VND | BR02 |
| INV-18 | Không có I/O ngoài (AI, email, kho tệp) bên trong giao dịch DB | Giao dịch service |

## Gửi lại và phiên bản
| Mã | Bất biến | Nguồn |
|---|---|---|
| INV-20 | Mọi lệnh ghi có `Idempotency-Key`. Cùng khóa cùng nội dung trả kết quả cũ; cùng khóa khác nội dung trả 409 | BR06 |
| INV-21 | Receipt, nghiệp vụ, outbox cùng một commit. Chỉ trả thành công sau commit | G3-DP01 |
| INV-22 | Replay kiểm quyền hiện hành trước khi trả, không lộ nội dung đã thu hồi | F03 |
| INV-23 | Sửa theo phiên bản dùng `If-Match`. Bản cũ nhận 412, không tự gộp | BR06, G3-D07-06 |
| INV-24 | Mất phản hồi thì tra trạng thái hoặc gửi lại cùng khóa. Client không đoán thành công | BR06 |
| INV-25 | Ràng buộc nghiệp vụ độc lập với receipt. Hết hạn receipt không làm phát sinh trùng | BR06, ADR-0006 |
| INV-26 | Receipt và outbox bị dọn định kỳ. `command_id`, `event_id` có thể không còn bản ghi; code không giả định luôn tìm thấy | ADR-0006 |

## Phân khúc R0
| Mã | Bất biến | Nguồn |
|---|---|---|
| INV-30 | Dịch vụ phải kiểm phân khúc R0: Toán 8 đại số, trực tuyến, tiếng Việt. Từ V6, DB không còn chặn khối lớp và môn | ADR-0006, G2-D01 |
| INV-31 | Hạn mức AI đọc từ cấu hình, không viết cứng. Mặc định R0 theo NFR13, NFR14 | ADR-0006 |

## Matching
| Mã | Bất biến | Nguồn |
|---|---|---|
| INV-35 | Lọc cứng trước, xếp hạng sau. Thiếu dữ liệu để xác nhận điều kiện cứng là không đạt. Không nới điều kiện, không thêm hồ sơ mẫu | BR01, UC05 |
| INV-36 | Trả `min(5, N)`, không trùng. Cùng dữ liệu và phiên bản cho cùng thứ tự. Lưu đầu vào, điểm thành phần, phiên bản | BR03 |
| INV-37 | Điểm không phải xác suất phù hợp hay tiến bộ. Lý do lấy từ dữ liệu, ghi đúng tự khai hay đã xác minh | BR03, UC05 |
| INV-38 | Chỉ dùng phương án B khi phụ huynh chủ động chọn, gắn nhãn không dùng AI, ghi sự kiện riêng. Rỗng khác thiếu dữ liệu khác lỗi | BR03, FR-14 |

## Nguồn, AI và nội dung
| Mã | Bất biến | Nguồn |
|---|---|---|
| INV-40 | Chỉ Spring ghi DB và kiểm quyền. Python worker không có thông tin đăng nhập DB | ADR-0005 |
| INV-41 | Nội dung học liệu là dữ liệu, không phải lệnh. Không đổi quyền, không gọi công cụ | NFR06 |
| INV-42 | Kiểm quyền nguồn lúc đọc, trước gọi nhà cung cấp, trước lưu/trả, trước duyệt và công bố | NFR03, G3-DP02 |
| INV-43 | Thu hồi chặn ngay tại commit, kể cả nội dung dẫn xuất với người mất quyền. Dọn cache, chỉ mục chạy sau | BR08, TX09 |
| INV-44 | AI chỉ tạo nháp. Không tự duyệt, không tự công bố. Kết quả muộn không đè bản đã sửa (`fencing_token`) | UC11, G3-DP03 |
| INV-45 | Xem gốc, tải, dùng AI, đọc dẫn xuất là bốn quyền riêng | G3-DP02 |
| INV-46 | Đáp án tách bảng, tách DTO, tách endpoint. Không nằm trong payload đề bài. Đến hạn không tự mở | UC12, G3-DP04 |
| INV-47 | Upload, trích xuất, duyệt nguồn, duyệt bài, công bố là các trạng thái khác nhau | TB G2 02 mục 5.2 |
| INV-48 | Gửi nhà cung cấp dùng mã giả, không có tên, liên hệ, địa chỉ. AI mặc định tắt khi chưa có trần tiền | NFR07, NFR14 |
| INV-49 | AI lỗi không ảnh hưởng lớp, lịch, nháp. Luôn có đường soạn thủ công. Không tự thử lại vô hạn | NFR05, NFR14 |

## Xác nhận và học tập
| Mã | Bất biến | Nguồn |
|---|---|---|
| INV-50 | Chuyên môn do gia sư xác nhận, tham gia do phụ huynh xác nhận. Không xác nhận thay | BR07, UC09 |
| INV-51 | Im lặng không phải đồng ý. Quá 72 giờ vẫn chờ, chỉ nhắc | UC09 |
| INV-52 | Bản công bố mới cần xác nhận lại, không kế thừa đồng ý cũ | G3-D35-03 |
| INV-53 | COMPLETED cần đủ xác nhận trên cùng phiên bản. Hủy, vắng, tranh chấp không tính hoàn thành | UC09 |
| INV-54 | Bản đã duyệt, đã công bố, đã nộp là bất biến. Sửa tạo phiên bản mới | G3-DP04, UC12 |

## Vòng đời và dữ liệu cá nhân
| Mã | Bất biến | Nguồn |
|---|---|---|
| INV-60 | Trước chốt không lộ email, liên hệ, địa chỉ trẻ | BR07 |
| INV-61 | Kết thúc lớp khác thu hồi nguồn: chặn AI và tạo mới, nội dung công bố còn đọc được tối đa 90 ngày | BR08, G3-D35-02 |
| INV-62 | Hoàn tất xóa cần đủ các kho. Ẩn giao diện không phải đã xóa. Restore phải áp lại ledger trước khi mở dịch vụ | UC16, G3-D16-04 |
| INV-63 | Log, audit, sự kiện đánh giá không chứa mật khẩu, token, học liệu nguyên văn, tên trẻ | NFR09, UC13 |
| INV-64 | Thu thập tối thiểu. Không bắt buộc ngày sinh, trường, ảnh, địa chỉ của trẻ | UC02 |

## Đo lường và trung thực
| Mã | Bất biến | Nguồn |
|---|---|---|
| INV-70 | Tỷ lệ có mẫu số, kỳ, `asOf`. N=0 là không áp dụng, không phải 0% | UC13 |
| INV-71 | Không suy học lực hay tiến bộ từ điểm danh, nộp bài | UC13 |
| INV-72 | Chỉ hiện thành công sau khi máy chủ xác nhận. Không có phần trăm giả | Mục 9 PRD/SRS |
| INV-73 | Không công bố năng lực tải, SLA, hiệu quả khi chưa đo | XC09 |

## Mở rộng
| Mã | Bất biến | Nguồn |
|---|---|---|
| INV-80 | API không trạng thái: không giữ phiên, khóa, tệp, tác vụ trong RAM một tiến trình | NFR15 |
| INV-81 | Danh sách có giới hạn và phân trang theo con trỏ. Không truy vấn không chặn trên | CR-008 |
| INV-82 | Mô-đun chỉ gọi API công khai của mô-đun khác, không đọc bảng của nhau | ADR-0001 |
| INV-83 | Tương thích connection pooler: không dựa trạng thái phiên DB qua nhiều giao dịch | ADR-0002 |
