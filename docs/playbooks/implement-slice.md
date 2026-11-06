# Playbook: implement-slice

Dùng để làm một chức năng: API, service, dữ liệu, màn hình và test. Giao dịch phức tạp dùng thêm `transaction-change`.

Theo CR-012, chức năng có cả API và màn hình được tách thành Issue backend và Issue giao diện. Issue backend làm bước 1–3 và 5–7. Issue giao diện làm bước 1, 4–7, và chỉ bắt đầu khi Issue backend đã merge. Nếu API chạy khác hợp đồng thì dừng và báo người làm backend, không sửa backend trong Issue giao diện.

## Các bước
1. **Xác nhận hợp đồng.** API đã có trong `contracts/openapi.yaml`. Cần đổi thì dừng và làm `contract-change` trước.
2. **Viết test trước cho AC và ca âm.** Tên test chứa mã AC. Quyền luôn có ca âm: khác gia đình, đổi ID, sai vai trò.
3. **Làm backend theo module-map.** Kiểm quyền trong service. Không đọc bảng của mô-đun khác.
4. **Làm giao diện** bằng API client sinh ra và thành phần dùng chung của nền mobile hoặc web. Đủ trạng thái: tải, rỗng, lỗi, hết phiên, mất quyền, chưa rõ kết quả. Test giao diện cho từng trạng thái.
5. **Chạy test thật** sau mỗi bước đạt, rồi commit nhỏ.
6. **Cập nhật handoff** sau mỗi commit: đã xong gì, kế tiếp là gì.
7. **Tự rà** theo `review-pr` trước khi yêu cầu người review.

## Kiểm trước khi báo xong
- [ ] Mọi AC của task có test và đạt
- [ ] Ca âm quyền có và đạt
- [ ] INV liên quan đã đối chiếu từng dòng
- [ ] Lệnh ghi có `Idempotency-Key`, sửa có `If-Match`
- [ ] Danh sách có `limit` và `cursor`
- [ ] Không có dữ liệu ngoài quyền trong DTO, lỗi, log
- [ ] Lỗi người dùng tiếng Việt, có `code` và `traceId`
- [ ] Ghi rõ lệnh đã chạy và kết quả thật

## Không được
- Thêm tính năng ngoài Issue, kể cả khi thấy hữu ích.
- Mock database cho logic quyền hoặc giao dịch.
- Sửa test cho khớp code sai.
