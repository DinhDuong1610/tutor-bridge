# Đường mở rộng

Theo CR-008: thiết kế không được chặn đường lên hàng trăm nghìn người dùng. **Đây là định hướng, không phải năng lực đã đo.** Không công bố con số nào cho đến khi có bài thử tải (XC09).

## Số tài khoản không phải là tải
Lấy cách quy đổi ở Kế hoạch mục 7.2: mỗi người đồng thời thao tác sau 10 giây, mỗi thao tác 2 request, tức 0,2 RPS/người. Tỷ lệ đồng thời dưới đây chỉ là **giả định minh họa**, phải thay bằng số đo ở thí điểm.

| Tài khoản | Giả định đồng thời lúc cao điểm | Người đồng thời | Xấp xỉ RPS |
|---|---|---|---|
| 1.000 | 10% | 100 | 20 |
| 10.000 | 5% | 500 | 100 |
| 100.000 | 3% | 3.000 | 600 |
| 500.000 | 2% | 10.000 | 2.000 |

## Nguyên tắc: làm sẵn cái khó đổi, để sau cái dễ thêm
| Làm ngay vì sau này sửa rất tốn | Để sau vì thêm được khi cần |
|---|---|
| API không trạng thái | Nhiều replica, autoscaling |
| Ranh giới mô-đun | Tách dịch vụ |
| Gỡ giới hạn cứng trong schema (ADR-0006) | Partition |
| Hợp đồng worker không phụ thuộc DB | Broker |
| Phân trang theo con trỏ, không dùng offset | Read replica |
| Truy vấn theo chỉ mục, có giới hạn kết quả | Cache |
| Tương thích connection pooler | Cụm PostgreSQL lớn hơn |
| API có phiên bản, app cũ còn chạy được | CDN cho nội dung công khai |

Dựng sẵn Kubernetes, Kafka hay microservice lúc này chỉ tốn công vận hành mà không thêm năng lực cho hai người.

## Các nấc
Chuyển nấc khi **số đo** chạm ngưỡng, không theo số tài khoản. Mỗi nấc phải qua bài thử tải ghi rõ cấu hình.

| Nấc | Tải mục tiêu | Thay đổi | Dấu hiệu cần lên nấc |
|---|---|---|---|
| 0. Thí điểm | 20 RPS (NFR10) | Một máy; 2 bản API để kiểm tính đúng; DB được quản lý | p95 vượt mục tiêu ở tải thật |
| 1. Vài nghìn | 100–200 RPS (L2 trong kế hoạch) | Nhiều bản API sau cân bằng tải; connection pooler; worker riêng | CPU DB cao kéo dài, pool cạn |
| 2. Hàng chục nghìn | Vài trăm RPS | Read replica cho đọc không cần mới nhất; cache hồ sơ công khai; partition bảng theo thời gian; dọn dữ liệu tự động | Hàng đợi tồn đọng, bảng log lớn |
| 3. Hàng trăm nghìn | Trên 1.000 RPS | Broker cho sự kiện; công cụ tìm kiếm riêng nếu matching chậm; tách mô-đun tải cao đầu tiên qua ADR | Ghi vào một DB chạm giới hạn |

## Điểm dự kiến nghẽn đầu tiên
1. **Lọc matching:** tính lịch chung trên nhiều gia sư. Phải thu hẹp bằng điều kiện rẻ trước, rồi mới xét lịch và vector.
2. **Bảng tăng nhanh:** `audit_events`, `outbox_events`, `notifications`, `command_receipts`. Xử lý ở ADR-0006.
3. **Đọc phiên mỗi yêu cầu:** ADR-0004.
4. **Chi phí AI:** có thể chạm trước cả giới hạn kỹ thuật. Hạn mức và trần tiền luôn được giữ.

## Bằng chứng cần có
Bài thử tải dùng k6 theo Kế hoạch mục 7.2, chạy ở mỗi nấc trước khi nhận thêm người dùng. Lịch không trùng là điều kiện cứng, không đánh đổi lấy tốc độ.
