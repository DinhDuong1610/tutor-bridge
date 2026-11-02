# ADR-0001 Nguyên khối chia mô-đun

**Trạng thái:** Đã chấp nhận (G3-D16-01). **Chủ trì:** Đính.

## Vấn đề
Hai người, 20 giờ/tuần mỗi người, phải làm nghiệp vụ nhiều giao dịch nguyên tử (chốt lớp, đổi lịch, thu hồi nguồn) và vẫn giữ được đường mở rộng.

## Phương án
| Phương án | Ưu | Nhược |
|---|---|---|
| Nguyên khối chia mô-đun | Giao dịch ACID trong một DB; vận hành một artifact | Phải chủ động giữ ranh giới |
| Microservice | Mở rộng và triển khai độc lập | Giao dịch phân tán, vận hành quá sức hai người |
| Nguyên khối không chia | Nhanh lúc đầu | Khó tách khi lớn |

## Quyết định
Spring Boot nguyên khối chia theo Identity & Profiles, Matching & Classes, Materials & Planning, Learning, Support & Privacy, Platform. Cùng artifact chạy được hai vai trò: `api` và `worker`. Mô-đun chỉ gọi nhau qua API công khai của mô-đun, không đọc bảng của nhau. Ranh giới được kiểm bằng test kiến trúc trong CI.

## Hệ quả
- Chịu tải bằng nhân bản API, không cần tách dịch vụ.
- Tách một mô-đun thành dịch vụ chỉ khi có số đo cho thấy cần, và đi qua ADR mới.
- Việc giữ ranh giới phụ thuộc CI, không phụ thuộc kỷ luật từng người hay từng agent.
