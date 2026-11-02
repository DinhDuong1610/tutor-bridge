# Playbook: review-pr

Dùng khi rà một PR, kể cả tự rà trước khi gửi và khi agent này rà code của agent kia. Agent rà **không thay** người rà.

## Thứ tự rà
1. **Đúng việc:** PR khớp Issue, không có phần ngoài phạm vi.
2. **Bất biến:** đối chiếu [invariants](../context/invariants.md) với phần đổi, ưu tiên quyền, lịch, nguồn, đáp án.
3. **Nghiệm thu:** mỗi AC có test, test có khẳng định thật, có ca âm.
4. **Thất bại:** mất mạng, gửi lại, đồng thời, bản cũ, hết hạn.
5. **Rò dữ liệu:** DTO, lỗi, log, cache.
6. **Mở rộng:** truy vấn có giới hạn, có chỉ mục, không N+1, không trạng thái trong RAM.
7. **Ranh giới:** không đọc bảng mô-đun khác, không gọi ngược chiều.

## Mức rà theo rủi ro
| Vùng | Cách rà |
|---|---|
| Quyền, lịch, giao dịch, nguồn, đáp án, migration | Đọc từng dòng, chạy thử nếu nghi ngờ |
| Nghiệp vụ thường | Đọc logic và test |
| CRUD, giao diện đơn giản | Đọc nhanh, xem trạng thái lỗi |

## Dấu hiệu test giả
- Test đạt cả khi xóa code cần kiểm
- Ca đồng thời chạy lần lượt
- Mock đúng phần cần kiểm
- Bảng rỗng nên ca đạt mà không chứng minh gì

Khi nghi ngờ, tạm làm hỏng code để xem test có đỏ không. Đợt kiểm chứng V6 từng có ca đạt trên bảng rỗng, và phải thêm ca đối chứng trước/sau mới có giá trị.

## Cách báo kết quả
Mỗi phát hiện ghi: mức (chặn, nên sửa, góp ý), file và dòng, tình huống cụ thể gây sai, đề xuất. Chỉ báo lỗi có tình huống thật. Không có phát hiện thì nói rõ đã kiểm những gì.

## Người rà phải tự trả lời
- Tôi có giải thích được đoạn code này làm gì và tại sao không?
- Tình huống xấu nhất là gì, và có test không?

Chưa trả lời được thì chưa duyệt. Không hiểu thì không merge.
